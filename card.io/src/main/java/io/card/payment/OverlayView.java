package io.card.payment;

/* OverlayView.java
 * See the file "LICENSE.md" for the full license governing this code.
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;

import io.card.payment.i18n.LocalizedStrings;
import io.card.payment.i18n.StringKey;

/**
 * This class implements a transparent overlay that is drawn over the raw camera capture frames.
 * <p/>
 * OverlayView draws the guide frame which indicates to the user where to hold the card. For debug
 * builds, it also displays the frame rate and the focus score. Once a card is detected, the class
 * displays a still image of the card.
 * <p/>
 * There are two stages of mark-up that are applied to the card image. When the image is first
 * passed into this class, a negative rounded rectangle mask is used to block out the image
 * background behind the rounded corners of the card. Then, a light gray rounded rectangle outline
 * is drawn along the card edges.
 * <p/>
 * Once the digits are detected for the credit card number, those digits are drawn above the
 * respective digits of the card.
 * <p/>
 * An instance of this class is created when the owning CardIOActivity is created. Its lifecycle is
 * the same as that of the owning activity.
 * <p/>
 * <p/>
 * A couple of technical notes:
 * <p/>
 * the drawing code is not optimized for performance. It re-computes values for each frame that
 * could be cached instead (such as guide tick locations). However, I have measured performance for
 * the app, including drawing performance, and drawing is not at all a bottleneck. So, fixing this
 * seems like a low priority item.
 * <p/>
 * It is not clear whether the rotation animation currently implemented is really needed. We should
 * figure out a strategy for this, and then change the code accordingly.
 * <p/>
 * To prevent race conditions & memory leaks, setting new card images happens in the context of the
 * UI thread.
 * <p/>
 * The class has an instance float field displayScale that holds the value screen dimensions are
 * scaled by. This field is used for instance to achieve consistent guide frame thickness
 * independent of screen scale.
 * <p/>
 */
public class OverlayView extends View {
    private static final String TAG = OverlayView.class.getSimpleName();

    private static final float GUIDE_FONT_SIZE = 26.0f;
    private static final float GUIDE_LINE_PADDING = 8.0f;
    private static final float GUIDE_LINE_HEIGHT = GUIDE_FONT_SIZE + GUIDE_LINE_PADDING;
    private static final float CARD_NUMBER_MARKUP_FONT_SIZE = GUIDE_FONT_SIZE + 2;

    private static final Orientation[] GRADIENT_ORIENTATIONS = { Orientation.TOP_BOTTOM,
            Orientation.LEFT_RIGHT, Orientation.BOTTOM_TOP, Orientation.RIGHT_LEFT };

    private static final int GUIDE_STROKE_WIDTH = 10;

    private static final float CORNER_RADIUS_SIZE = 1 / 15.0f;

    private static final int TORCH_WIDTH = 70;
    private static final int TORCH_HEIGHT = 50;

    private static final int BUTTON_TOUCH_TOLERANCE = 20;

    private static final int GUIDE_CORNER_RADIUS = 20;

    private final WeakReference<CardIOActivity> mScanActivityRef;
    private DetectionInfo mDInfo;
    private Bitmap mBitmap;
    private Rect mGuide;
    private CreditCard mDetectedCard;
    private int mRotation;
    private int mState;
    private int guideColor;
    private float guideCornerRadius;

    private String scanInstructions;

    // Keep paint objects around for high frequency methods to avoid re-allocating them.
    private GradientDrawable mGradientDrawable;
    private final Paint mGuidePaint;
    private final Paint mLockedBackgroundPaint;
    private Path mLockedBackgroundPath;
    private Rect mCameraPreviewRect;
    private final Torch mTorch;
    private Rect mTorchRect;
    private boolean mShowTorch;
    private int mRotationFlip;
    private float mScale = 1;
    private int instructionsMarginBottom;

    public OverlayView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        TypedArray attrs = context.obtainStyledAttributes(attributeSet, R.styleable.CioOverlayView);
        int guideColor = attrs.getColor(R.styleable.CioOverlayView_cio_guide_color, Color.GREEN);
        setGuideColor(guideColor);
        String scanInstructions = attrs.getString(R.styleable.CioOverlayView_cio_scan_instructions);
        if (!TextUtils.isEmpty(scanInstructions)) {
            setScanInstructions(scanInstructions);
        }
        instructionsMarginBottom = attrs.getDimensionPixelSize(R.styleable.CioOverlayView_cio_scan_instructions_margin_bottom, 0);
        attrs.recycle();

        mScanActivityRef = new WeakReference<>((CardIOActivity)context);

        mRotationFlip = 1;

        // card.io is designed for an hdpi screen (density = 1.5);
        mScale = getResources().getDisplayMetrics().density / 1.5f;

        mTorch = new Torch(TORCH_WIDTH * mScale, TORCH_HEIGHT * mScale);

        mGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mLockedBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLockedBackgroundPaint.clearShadowLayer();
        mLockedBackgroundPaint.setStyle(Paint.Style.FILL);
        mLockedBackgroundPaint.setColor(0xbb000000); // 75% black

        scanInstructions = LocalizedStrings.getString(StringKey.SCAN_GUIDE);

        guideCornerRadius = mScale * GUIDE_CORNER_RADIUS;
    }

    public int getGuideColor() {
        return guideColor;
    }

    public void setGuideColor(int color) {
        guideColor = color;
    }

    public String getScanInstructions() {
        return scanInstructions;
    }

    public void setScanInstructions(String scanInstructions) {
        this.scanInstructions = scanInstructions;
    }

    // Public methods used by CardIOActivity
    public void setGuideAndRotation(Rect rect, int rotation) {
        mRotation = rotation;
        mGuide = rect;
        invalidate();

        Point topEdgeUIOffset;
        if (mRotation % 180 != 0) {
            topEdgeUIOffset = new Point((int) (40 * mScale), (int) (60 * mScale));
            mRotationFlip = -1;
        } else {
            topEdgeUIOffset = new Point((int) (60 * mScale), (int) (40 * mScale));
            mRotationFlip = 1;
        }
        if (mCameraPreviewRect != null) {
            Point torchPoint = new Point(mCameraPreviewRect.left + topEdgeUIOffset.x,
                    mCameraPreviewRect.top + topEdgeUIOffset.y);

            // mTorchRect used only for touch lookup, not layout
            mTorchRect = Util.rectGivenCenter(torchPoint, (int) (TORCH_WIDTH * mScale),
                    (int) (TORCH_HEIGHT * mScale));

            int[] gradientColors = { Color.WHITE, Color.BLACK };
            Orientation gradientOrientation = GRADIENT_ORIENTATIONS[(mRotation / 90) % 4];
            mGradientDrawable = new GradientDrawable(gradientOrientation, gradientColors);
            mGradientDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            mGradientDrawable.setBounds(mGuide);
            mGradientDrawable.setAlpha(50);

            mLockedBackgroundPath = new Path();
            mLockedBackgroundPath.addRect(new RectF(mCameraPreviewRect), Path.Direction.CW);
            mLockedBackgroundPath.addRect(new RectF(mGuide), Path.Direction.CCW);
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (mBitmap != null) {
            mBitmap.recycle();
        }
        mBitmap = bitmap;
        if (mBitmap != null) {
            decorateBitmap();
        }
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setDetectionInfo(DetectionInfo dinfo) {
        if (mDInfo != null && !mDInfo.sameEdgesAs(dinfo)) {
            invalidate();
        }
        this.mDInfo = dinfo;
    }

    public int getCardX() {
        return mGuide.centerX() - mBitmap.getWidth() / 2;
    }

    public int getCardY() {
        return mGuide.centerY() - mBitmap.getHeight() / 2;
    }

    public Bitmap getCardImage() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            return Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        } else {
            return null;
        }
    }

    // Drawing methods
    private RectF guideStrokeRectF(int x1, int y1, int x2, int y2) {
        RectF r;
        int t2 = (int) (GUIDE_STROKE_WIDTH / 2 * mScale);
        r = new RectF();

        r.left = Math.min(x1, x2) - t2;
        r.right = Math.max(x1, x2) + t2;

        r.top = Math.min(y1, y2) - t2;
        r.bottom = Math.max(y1, y2) + t2;

        return r;
    }

    @Override
    public void onDraw(Canvas canvas) {

        if (mGuide == null || mCameraPreviewRect == null) {
            return;
        }
        canvas.save();
        int tickLength;

        // Draw background rect

        mGradientDrawable.draw(canvas);

        if ((mRotation == 0) || (mRotation == 180)) {
            tickLength = (mGuide.bottom - mGuide.top) / 4;
        } else {
            tickLength = (mGuide.right - mGuide.left) / 4;
        }

        if (mDInfo != null && mDInfo.numVisibleEdges() == 4) {
            // draw lock shadow.
            canvas.drawPath(mLockedBackgroundPath, mLockedBackgroundPaint);
        }

        // Draw guide lines
        mGuidePaint.clearShadowLayer();
        mGuidePaint.setStyle(Paint.Style.FILL);
        mGuidePaint.setColor(guideColor);

        float radius = guideCornerRadius;
        // top left
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.left, mGuide.top, mGuide.left + tickLength, mGuide.top),
                radius, radius, mGuidePaint);
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.left, mGuide.top, mGuide.left, mGuide.top + tickLength),
                radius, radius, mGuidePaint);

        // top right
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.right, mGuide.top, mGuide.right - tickLength, mGuide.top),
                radius, radius, mGuidePaint);
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.right, mGuide.top, mGuide.right, mGuide.top + tickLength),
                radius, radius, mGuidePaint);

        // bottom left
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.left, mGuide.bottom, mGuide.left + tickLength, mGuide.bottom),
                radius, radius, mGuidePaint);
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.left, mGuide.bottom, mGuide.left, mGuide.bottom - tickLength),
                radius, radius, mGuidePaint);

        // bottom right
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.right, mGuide.bottom, mGuide.right - tickLength,
                        mGuide.bottom), radius, radius, mGuidePaint);
        canvas.drawRoundRect(
                guideStrokeRectF(mGuide.right, mGuide.bottom, mGuide.right, mGuide.bottom
                        - tickLength), radius, radius, mGuidePaint);

        if (mDInfo != null) {
            if (mDInfo.topEdge) {
                canvas.drawRoundRect(guideStrokeRectF(mGuide.left, mGuide.top, mGuide.right, mGuide.top),
                        radius, radius, mGuidePaint);
            }
            if (mDInfo.bottomEdge) {
                canvas.drawRoundRect(
                        guideStrokeRectF(mGuide.left, mGuide.bottom, mGuide.right, mGuide.bottom),
                        radius, radius, mGuidePaint);
            }
            if (mDInfo.leftEdge) {
                canvas.drawRoundRect(
                        guideStrokeRectF(mGuide.left, mGuide.top, mGuide.left, mGuide.bottom),
                        radius, radius, mGuidePaint);
            }
            if (mDInfo.rightEdge) {
                canvas.drawRoundRect(
                        guideStrokeRectF(mGuide.right, mGuide.top, mGuide.right, mGuide.bottom),
                        radius, radius, mGuidePaint);
            }

            if (mDInfo.numVisibleEdges() < 3) {
                // Draw guide text
                // Set up paint attributes
                float guideHeight = GUIDE_LINE_HEIGHT * mScale;
                float guideFontSize = GUIDE_FONT_SIZE * mScale;

                Util.setupTextPaintStyle(mGuidePaint);
                mGuidePaint.setTextAlign(Align.CENTER);
                mGuidePaint.setTextSize(guideFontSize);

                // Translate and rotate text
                int dx = mGuide.left + mGuide.width() / 2;
                int dy = mGuide.top + mGuide.height() / 2;

                if ((mRotation == 0) || (mRotation == 180)) {
                    dy -= instructionsMarginBottom;
                } else {
                    dx += instructionsMarginBottom;
                }

                canvas.translate(dx, dy);
                canvas.rotate(mRotationFlip * mRotation);

                if (scanInstructions != null && scanInstructions != "") {
                    String[] lines = scanInstructions.split("\n");
                    float y = -(((guideHeight * (lines.length - 1)) - guideFontSize) / 2) - 3;

                    for (int i = 0; i < lines.length; i++) {
                        canvas.drawText(lines[i], 0, y, mGuidePaint);
                        y += guideHeight;
                    }
                }
            }
        }
        canvas.restore();

        if (mShowTorch) {
            // draw torch
            canvas.save();
            canvas.translate(mTorchRect.exactCenterX(), mTorchRect.exactCenterY());
            canvas.rotate(mRotationFlip * mRotation);
            mTorch.draw(canvas);
            canvas.restore();
        }
    }

    public void setDetectedCard(CreditCard creditCard) {
        mDetectedCard = creditCard;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            int action;
            action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {

                Point p = new Point((int) event.getX(), (int) event.getY());
                Rect r = Util.rectGivenCenter(p, BUTTON_TOUCH_TOLERANCE, BUTTON_TOUCH_TOLERANCE);
                if (mShowTorch && mTorchRect != null && Rect.intersects(mTorchRect, r)) {
                    mScanActivityRef.get().toggleFlash();
                } else {
                    mScanActivityRef.get().triggerAutoFocus();
                }
            }
        } catch (NullPointerException e) {
            // Un-reproducible NPE reported on device without flash where flash detected and flash
            // button pressed (see https://github.com/paypal/PayPal-Android-SDK/issues/27)
        }

        return false;
    }

    /* create the card image with inside a rounded rect */
    private void decorateBitmap() {

        RectF roundedRect = new RectF(2, 2, mBitmap.getWidth() - 2, mBitmap.getHeight() - 2);
        float cornerRadius = mBitmap.getHeight() * CORNER_RADIUS_SIZE;

        // Alpha canvas with white rounded rect
        Bitmap maskBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(maskBitmap);
        maskCanvas.drawColor(Color.TRANSPARENT);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.BLACK);
        maskPaint.setStyle(Paint.Style.FILL);
        maskCanvas.drawRoundRect(roundedRect, cornerRadius, cornerRadius, maskPaint);

        Paint paint = new Paint();
        paint.setFilterBitmap(false);

        // Draw mask onto mBitmap
        Canvas canvas = new Canvas(mBitmap);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(maskBitmap, 0, 0, paint);

        // Now re-use the above bitmap to do a shadow.
        paint.setXfermode(null);

        maskBitmap.recycle();
    }

    // TODO - move this into RequestTask, so we just get back a card image ready to go
    public void markupCard() {

        if (mBitmap == null) {
            return;
        }

        if (mDetectedCard.flipped) {
            Matrix m = new Matrix();
            m.setRotate(180, mBitmap.getWidth() / 2, mBitmap.getHeight() / 2);

            mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(),
                    m, false);
        }

        Canvas bc = new Canvas(mBitmap);
        Paint paint = new Paint();
        Util.setupTextPaintStyle(paint);
        paint.setTextSize(CARD_NUMBER_MARKUP_FONT_SIZE * mScale);

        int len = mDetectedCard.cardNumber.length();
        float sf = mBitmap.getWidth() / (float)CardScanner.CREDIT_CARD_TARGET_WIDTH;
        int yOffset = (int) ((mDetectedCard.yoff * sf - 6));
        for (int i = 0; i < len; i++) {
            int xOffset = (int) (mDetectedCard.xoff[i] * sf);
            bc.drawText("" + mDetectedCard.cardNumber.charAt(i), xOffset, yOffset, paint);
        }
    }

    public boolean isAnimating() {
        return (mState != 0);
    }

    public void setCameraPreviewRect(Rect rect) {
        mCameraPreviewRect = rect;
    }

    public void setShowTorch(boolean showTorch) {
        mShowTorch = showTorch;
    }

    public void setTorchOn(boolean b) {
        mTorch.setOn(b);
        invalidate();
    }

    // for test
    public Rect getTorchRect() {
        return mTorchRect;
    }
}
