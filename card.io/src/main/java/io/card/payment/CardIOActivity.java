package io.card.payment;

/* CardIOActivity.java
 * See the file "LICENSE.md" for the full license governing this code.
 */

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.util.Date;

import io.card.payment.i18n.LocalizedStrings;
import io.card.payment.i18n.StringKey;
import io.card.payment.ui.ActivityHelper;
import io.card.payment.ui.config.UIConfig;
import io.card.payment.ui.config.DefaultUiConfig;

/**
 * This is the entry point {@link android.app.Activity} for a card.io client to use <a
 * href="https://card.io">card.io</a>.
 *
 * @version 1.0
 */
public final class CardIOActivity extends Activity {

    /**
     * Boolean extra. Optional. Defaults to <code>false</code>. If
     * set to <code>false</code>, expiry information will not be required.
     */
    public static final String EXTRA_REQUIRE_EXPIRY = "io.card.payment.requireExpiry";

    /**
     * Boolean extra. Optional. Defaults to <code>true</code>. If
     * set to <code>true</code>, and {@link #EXTRA_REQUIRE_EXPIRY} is <code>true</code>,
     * an attempt to extract the expiry from the card image will be made.
     */
    public static final String EXTRA_SCAN_EXPIRY = "io.card.payment.scanExpiry";

    /**
     * Integer extra. Optional. Defaults to <code>-1</code> (no blur). Privacy feature.
     * How many of the Card number digits NOT to blur on the resulting image.
     * Setting it to <code>4</code> will blur all digits except the last four.
     */
    public static final String EXTRA_UNBLUR_DIGITS = "io.card.payment.unblurDigits";

    /**
     * Parcelable extra containing {@link CreditCard}. The data intent returned to your {@link android.app.Activity}'s
     * {@link Activity#onActivityResult(int, int, Intent)} will contain this extra if the resultCode is
     * {@link #RESULT_CARD_INFO}.
     */
    public static final String EXTRA_SCAN_RESULT = "io.card.payment.scanResult";

    /**
     * Boolean extra. Optional. Defaults to <code>false</code>. Removes the keyboard button from the
     * scan screen.
     * <br><br>
     * If scanning is unavailable, the {@link android.app.Activity} result will be {@link #RESULT_SCAN_NOT_AVAILABLE}.
     */
    public static final String EXTRA_SUPPRESS_MANUAL_ENTRY = "io.card.payment.suppressManual";

    /**
     * String extra. Optional. The preferred language for all strings appearing in the user
     * interface. If not set, or if set to null, defaults to the device's current language setting.
     * <br><br>
     * Can be specified as a language code ("en", "fr", "zh-Hans", etc.) or as a locale ("en_AU",
     * "fr_FR", "zh-Hant_TW", etc.).
     * <br><br>
     * If the library does not contain localized strings for a specified locale, then will fall back
     * to the language. E.g., "es_CO" -&gt; "es".
     * <br><br>
     * If the library does not contain localized strings for a specified language, then will fall
     * back to American English.
     * <br><br>
     * If you specify only a language code, and that code matches the device's currently preferred
     * language, then the library will attempt to use the device's current region as well. E.g.,
     * specifying "en" on a device set to "English" and "United Kingdom" will result in "en_GB".
     * <br><br>
     * These localizations are currently included:
     * <br><br>
     * ar, da, de, en, en_AU, en_GB, es, es_MX, fr, he, is, it, ja, ko, ms, nb, nl, pl, pt, pt_BR, ru,
     * sv, th, tr, zh-Hans, zh-Hant, zh-Hant_TW.
     */
    public static final String EXTRA_LANGUAGE_OR_LOCALE = "io.card.payment.languageOrLocale";

    /**
     * Boolean extra. Optional. Once a card image has been captured but before it has been
     * processed, this value will determine whether to continue processing as usual. If the value is
     * <code>true</code> the {@link CardIOActivity} will finish with a {@link #RESULT_SCAN_SUPPRESSED} result code.
     */
    public static final String EXTRA_SUPPRESS_SCAN = "io.card.payment.suppressScan";

    /**
     * String extra. If {@link #EXTRA_RETURN_CARD_IMAGE} is set to <code>true</code>, the data intent passed to your
     * {@link android.app.Activity} will have the card image stored as a JPEG formatted byte array in this extra.
     */
    public static final String EXTRA_CAPTURED_CARD_IMAGE = "io.card.payment.capturedCardImage";

    /**
     * Boolean extra. Optional. If this value is set to <code>true</code> the card image will be passed as an
     * extra in the data intent that is returned to your {@link android.app.Activity} using the
     * {@link #EXTRA_CAPTURED_CARD_IMAGE} key.
     */
    public static final String EXTRA_RETURN_CARD_IMAGE = "io.card.payment.returnCardImage";

    public static final String EXTRA_UI_CONFIG = "io.card.payment.ui_config";

    /**
     * Boolean extra. Used for testing only.
     */
    static final String PRIVATE_EXTRA_CAMERA_BYPASS_TEST_MODE = "io.card.payment.cameraBypassTestMode";

    private static int lastResult = 0xca8d10; // arbitrary. chosen to be well above

    /**
     * result code supplied to {@link Activity#onActivityResult(int, int, Intent)} when a scan request completes.
     */
    public static final int RESULT_CARD_INFO = lastResult++;

    /**
     * result code supplied to {@link Activity#onActivityResult(int, int, Intent)}
     * when the user presses the manual entry button.
     */
    public static final int RESULT_SCAN_CANCELED = lastResult++;

    /**
     * result code indicating that scan is not available.
     * This error can be avoided in normal situations by checking
     * {@link #canReadCardWithCamera()}.
     */
    public static final int RESULT_SCAN_NOT_AVAILABLE = lastResult++;

    /**
     * result code indicating that we only captured the card image.
     */
    public static final int RESULT_SCAN_SUPPRESSED = lastResult++;

    private static final String TAG = CardIOActivity.class.getSimpleName();

    private static final int DEGREE_DELTA = 15;

    private static final int ORIENTATION_PORTRAIT = 1;
    private static final int ORIENTATION_PORTRAIT_UPSIDE_DOWN = 2;
    private static final int ORIENTATION_LANDSCAPE_RIGHT = 3;
    private static final int ORIENTATION_LANDSCAPE_LEFT = 4;

    private static final String BUNDLE_WAITING_FOR_PERMISSION = "io.card.payment.waitingForPermission";

    private static final long[] VIBRATE_PATTERN = { 0, 70, 10, 40 };

    private static final int TOAST_OFFSET_Y = -75;

    private static final int PERMISSION_REQUEST_ID = 11;

    private OverlayView mOverlay;
    private OrientationEventListener orientationListener;
    private UIConfig uiConfig;

    // TODO: the preview is accessed by the scanner. Not the best practice.
    Preview mPreview;

    private CreditCard mDetectedCard;
    private Rect mGuideFrame;
    private int mLastDegrees;
    private int mFrameOrientation;
    private boolean suppressManualEntry;
    private boolean mDetectOnly;
    private boolean waitingForPermission;

    private CardScanner mCardScanner;

    private boolean manualEntryFallbackOrForced = false;

    // ------------------------------------------------------------------------
    // ACTIVITY LIFECYCLE
    // ------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent clientData = this.getIntent();

        LocalizedStrings.setLanguage(clientData);

        // Validate app's manifest is correct.
        mDetectOnly = clientData.getBooleanExtra(EXTRA_SUPPRESS_SCAN, false);

        ResolveInfo resolveInfo;
        String errorMsg;

        // Check for CardIOActivity's orientation config in manifest
        resolveInfo = getPackageManager().resolveActivity(clientData,
                PackageManager.MATCH_DEFAULT_ONLY);
        errorMsg = Util.manifestHasConfigChange(resolveInfo, CardIOActivity.class);
        if (errorMsg != null) {
            throw new RuntimeException(errorMsg); // Throw the actual exception from this class, for
            // clarity.
        }

        suppressManualEntry = clientData.getBooleanExtra(EXTRA_SUPPRESS_MANUAL_ENTRY, false);

        if (savedInstanceState != null) {
            waitingForPermission = savedInstanceState.getBoolean(BUNDLE_WAITING_FOR_PERMISSION);
        }

        if (!CardScanner.processorSupported()){
            manualEntryFallbackOrForced = true;
        } else {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (!waitingForPermission) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_DENIED) {
                            String[] permissions = {Manifest.permission.CAMERA};
                            waitingForPermission = true;
                            requestPermissions(permissions, PERMISSION_REQUEST_ID);
                        } else {
                            checkCamera();
                            android23AndAboveHandleCamera();
                        }
                    }
                } else {
                    checkCamera();
                    android22AndBelowHandleCamera();
                }
            } catch (Exception e) {
                handleGeneralExceptionError(e);
            }
        }

    }

    private void android23AndAboveHandleCamera() {
        if (manualEntryFallbackOrForced) {
            finishIfSuppressManualEntry();
        } else {
            // Guaranteed to be called in API 23+
            showCameraScannerOverlay();
        }
    }


    private void android22AndBelowHandleCamera() {
        if (manualEntryFallbackOrForced) {
            finishIfSuppressManualEntry();
        } else {
            // guaranteed to be called in onCreate on API < 22, so it's ok that we're removing the window feature here
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            showCameraScannerOverlay();
        }
    }

    private void finishIfSuppressManualEntry() {
        setResultAndFinish(RESULT_SCAN_NOT_AVAILABLE, null);
    }

    private void checkCamera() {
        try {
            if (!Util.hardwareSupported()) {
                StringKey errorKey = StringKey.ERROR_NO_DEVICE_SUPPORT;
                String localizedError = LocalizedStrings.getString(errorKey);
                Log.w(Util.PUBLIC_LOG_TAG, errorKey + ": " + localizedError);
                manualEntryFallbackOrForced = true;
            }
        } catch (CameraUnavailableException e) {
            StringKey errorKey = StringKey.ERROR_CAMERA_CONNECT_FAIL;
            String localizedError = LocalizedStrings.getString(errorKey);

            Log.e(Util.PUBLIC_LOG_TAG, errorKey + ": " + localizedError);
            Toast toast = Toast.makeText(this, localizedError, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, TOAST_OFFSET_Y);
            toast.show();
            manualEntryFallbackOrForced = true;
        }
    }

    private void showCameraScannerOverlay() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            View decorView = getWindow().getDecorView();
            // Hide the status bar.
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            // Remember that you should never show the action bar if the
            // status bar is hidden, so hide that too if necessary.
            ActionBar actionBar = getActionBar();
            if (null != actionBar) {
                actionBar.hide();
            }
        }

        try {
            mGuideFrame = new Rect();

            mFrameOrientation = ORIENTATION_PORTRAIT;

            if (getIntent().getBooleanExtra(PRIVATE_EXTRA_CAMERA_BYPASS_TEST_MODE, false)) {
                if (!this.getPackageName().contentEquals("io.card.development")) {
                    throw new IllegalStateException("Illegal access of private extra");
                }
                // use reflection here so that the tester can be safely stripped for release
                // builds.
                Class<?> testScannerClass = Class.forName("io.card.payment.CardScannerTester");
                Constructor<?> cons = testScannerClass.getConstructor(this.getClass(),
                        Integer.TYPE);
                mCardScanner = (CardScanner) cons.newInstance(new Object[] { this,
                        mFrameOrientation });
            } else {
                mCardScanner = new CardScanner(this, mFrameOrientation);
            }
            mCardScanner.prepareScanner();

            initUi();

            orientationListener = new OrientationEventListener(this,
                    SensorManager.SENSOR_DELAY_UI) {
                @Override
                public void onOrientationChanged(int orientation) {
                    doOrientationChange(orientation);
                }
            };

        } catch (Exception e) {
            handleGeneralExceptionError(e);
        }
    }

    private void handleGeneralExceptionError(Exception e) {
        StringKey errorKey = StringKey.ERROR_CAMERA_UNEXPECTED_FAIL;
        String localizedError = LocalizedStrings.getString(errorKey);

        Log.e(Util.PUBLIC_LOG_TAG, "Unknown exception, please post the stack trace as a GitHub issue", e);
        Toast toast = Toast.makeText(this, localizedError, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, TOAST_OFFSET_Y);
        toast.show();
        manualEntryFallbackOrForced = true;
    }

    private void doOrientationChange(int orientation) {
        if (orientation < 0 || mCardScanner == null) {
            return;
        }

        orientation += mCardScanner.getRotationalOffset();

        // Check if we have gone too far forward with
        // rotation adjustment, keep the result between 0-360
        if (orientation > 360) {
            orientation -= 360;
        }
        int degrees;

        degrees = -1;

        if (orientation < DEGREE_DELTA || orientation > 360 - DEGREE_DELTA) {
            degrees = 0;
            mFrameOrientation = ORIENTATION_PORTRAIT;
        } else if (orientation > 90 - DEGREE_DELTA && orientation < 90 + DEGREE_DELTA) {
            degrees = 90;
            mFrameOrientation = ORIENTATION_LANDSCAPE_LEFT;
        } else if (orientation > 180 - DEGREE_DELTA && orientation < 180 + DEGREE_DELTA) {
            degrees = 180;
            mFrameOrientation = ORIENTATION_PORTRAIT_UPSIDE_DOWN;
        } else if (orientation > 270 - DEGREE_DELTA && orientation < 270 + DEGREE_DELTA) {
            degrees = 270;
            mFrameOrientation = ORIENTATION_LANDSCAPE_RIGHT;
        }
        if (degrees >= 0 && degrees != mLastDegrees) {
            mCardScanner.setDeviceOrientation(mFrameOrientation);
            setDeviceDegrees(degrees);
        }
    }

    /**
     * Suspend/resume camera preview as part of the {@link android.app.Activity} life cycle (side note: we reuse the
     * same buffer for preview callbacks to greatly reduce the amount of required GC).
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (!waitingForPermission) {
            if (manualEntryFallbackOrForced) {
                finishIfSuppressManualEntry();
                return;
            }

            Util.logNativeMemoryStats();

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ActivityHelper.setFlagSecure(this);

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            orientationListener.enable();

            if (!restartPreview()) {
                StringKey error = StringKey.ERROR_CAMERA_UNEXPECTED_FAIL;
                showErrorMessage(LocalizedStrings.getString(error));
                setScannedCardToResultAndFinish();
            } else {
                // Turn flash off
                setFlashOn(false);
            }

            doOrientationChange(mLastDegrees);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(BUNDLE_WAITING_FOR_PERMISSION, waitingForPermission);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (orientationListener != null) {
            orientationListener.disable();
        }
        setFlashOn(false);

        if (mCardScanner != null) {
            mCardScanner.pauseScanning();
        }
    }

    @Override
    protected void onDestroy() {
        mOverlay = null;

        if (orientationListener != null) {
            orientationListener.disable();
        }
        setFlashOn(false);

        if (mCardScanner != null) {
            mCardScanner.endScanning();
            mCardScanner = null;
        }

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_ID) {
            waitingForPermission = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCameraScannerOverlay();
            } else {
                // show manual entry - handled in onResume()
                manualEntryFallbackOrForced = true;
            }
        }
    }

    /**
     * This {@link android.app.Activity} overrides back button handling to handle back presses properly given the
     * various states this {@link android.app.Activity} can be in.
     * <br><br>
     * This method is called by Android, never directly by application code.
     */
    @Override
    public void onBackPressed() {
        if (!manualEntryFallbackOrForced && mOverlay.isAnimating()) {
            try {
                restartPreview();
            } catch (RuntimeException re) {
                Log.w(TAG, "*** could not return to preview: " + re);
            }
        } else if (mCardScanner != null) {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------------
    // STATIC METHODS
    // ------------------------------------------------------------------------

    /**
     * Determine if the device supports card scanning.
     * <br><br>
     * An ARM7 processor and Android SDK 8 or later are required. Additional checks for specific
     * misbehaving devices may also be added.
     *
     * @return <code>true</code> if camera is supported. <code>false</code> otherwise.
     */
    public static boolean canReadCardWithCamera() {
        try {
            return Util.hardwareSupported();
        } catch (CameraUnavailableException e) {
            return false;
        } catch (RuntimeException e) {
            Log.w(TAG, "RuntimeException accessing Util.hardwareSupported()");
            return false;
        }
    }

    /**
     * Returns the String version of this SDK.  Please include the return value of this method in any support requests.
     *
     * @return The String version of this SDK
     */
    public static String sdkVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * @deprecated Always returns {@code new Date()}.
     */
    @Deprecated
    public static Date sdkBuildDate() {
        return new Date();
    }

    /**
     * Utility method for decoding card bitmap
     *
     * @param intent - intent received in {@link Activity#onActivityResult(int, int, Intent)}
     * @return decoded bitmap or null
     */
    public static Bitmap getCapturedCardImage(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_CAPTURED_CARD_IMAGE)) {
            return null;
        }

        byte[] imageData = intent.getByteArrayExtra(EXTRA_CAPTURED_CARD_IMAGE);
        ByteArrayInputStream inStream = new ByteArrayInputStream(imageData);
        Bitmap result = BitmapFactory.decodeStream(inStream, null, new BitmapFactory.Options());
        return result;
    }

    // end static

    void onFirstFrame() {
        SurfaceView sv = mPreview.getSurfaceView();
        if (mOverlay != null) {
            mOverlay.setCameraPreviewRect(new Rect(sv.getLeft(), sv.getTop(), sv.getRight(), sv
                    .getBottom()));
        }
        mFrameOrientation = ORIENTATION_PORTRAIT;
        setDeviceDegrees(0);

        onEdgeUpdate(new DetectionInfo());
    }

    void onEdgeUpdate(DetectionInfo dInfo) {
        mOverlay.setDetectionInfo(dInfo);
    }

    void onCardDetected(Bitmap detectedBitmap, DetectionInfo dInfo) {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_PATTERN, -1);
        } catch (SecurityException e) {
            Log.e(Util.PUBLIC_LOG_TAG,
                    "Could not activate vibration feedback. Please add <uses-permission android:name=\"android.permission.VIBRATE\" /> to your application's manifest.");
        } catch (Exception e) {
            Log.w(Util.PUBLIC_LOG_TAG, "Exception while attempting to vibrate: ", e);
        }

        mCardScanner.pauseScanning();

        if (dInfo.predicted()) {
            mDetectedCard = dInfo.creditCard();
            mOverlay.setDetectedCard(mDetectedCard);
        }

        float sf;
        if (mFrameOrientation == ORIENTATION_PORTRAIT
                || mFrameOrientation == ORIENTATION_PORTRAIT_UPSIDE_DOWN) {
            sf = mGuideFrame.right / (float)CardScanner.CREDIT_CARD_TARGET_WIDTH * .95f;
        } else {
            sf = mGuideFrame.right / (float)CardScanner.CREDIT_CARD_TARGET_WIDTH * 1.15f;
        }

        Matrix m = new Matrix();
        m.postScale(sf, sf);

        Bitmap scaledCard = Bitmap.createBitmap(detectedBitmap, 0, 0, detectedBitmap.getWidth(),
                detectedBitmap.getHeight(), m, false);
        mOverlay.setBitmap(scaledCard);

        if (mDetectOnly) {
            Intent dataIntent = new Intent();
            Util.writeCapturedCardImageIfNecessary(getIntent(), dataIntent, mOverlay);

            setResultAndFinish(RESULT_SCAN_SUPPRESSED, dataIntent);
        } else {
            setScannedCardToResultAndFinish();
        }
    }

    private void setScannedCardToResultAndFinish() {
        Intent dataIntent = new Intent();
        if (mDetectedCard != null) {
            dataIntent.putExtra(EXTRA_SCAN_RESULT, mDetectedCard);
            mDetectedCard = null;
        }

        Util.writeCapturedCardImageIfNecessary(getIntent(), dataIntent, mOverlay);

        setResultAndFinish(RESULT_CARD_INFO, dataIntent);
    }

    /**
     * Show an error message using toast.
     */
    private void showErrorMessage(final String msgStr) {
        Log.e(Util.PUBLIC_LOG_TAG, "error display: " + msgStr);
        Toast toast = Toast.makeText(CardIOActivity.this, msgStr, Toast.LENGTH_LONG);
        toast.show();
    }

    private boolean restartPreview() {
        mDetectedCard = null;
        assert mPreview != null;
        return mCardScanner.resumeScanning(mPreview.getSurfaceHolder());
    }

    private void setDeviceDegrees(int degrees) {
        View sv;

        sv = mPreview.getSurfaceView();

        if (sv == null) {
            return;
        }

        mGuideFrame = mCardScanner.getGuideFrame(sv.getWidth(), sv.getHeight());

        // adjust for surface view y offset
        mGuideFrame.top += sv.getTop();
        mGuideFrame.bottom += sv.getTop();
        mOverlay.setGuideAndRotation(mGuideFrame, degrees);
        mLastDegrees = degrees;
    }

    // Called by OverlayView
    void toggleFlash() {
        setFlashOn(!mCardScanner.isFlashOn());
    }

    void setFlashOn(boolean b) {
        boolean success = (mPreview != null && mOverlay != null && mCardScanner.setFlashOn(b));
        if (success) {
            mOverlay.setTorchOn(b);
        }
    }

    void triggerAutoFocus() {
        mCardScanner.triggerAutoFocus(true);
    }

    private void initUi() {
        UIConfig uiConfig = getUiConfig();
        ViewGroup viewGroup = (ViewGroup) LayoutInflater.from(this)
                .inflate(uiConfig.getLayoutId(), null, false);
        mPreview = uiConfig.getPreviewView(viewGroup);
        mPreview.setPreviewSize(mCardScanner.mPreviewWidth, mCardScanner.mPreviewHeight);
        mOverlay = uiConfig.getOverlayView(viewGroup);
        initManualEntryButton(uiConfig.getManualEntryButton(viewGroup));
        setContentView(viewGroup);
    }

    private UIConfig getUiConfig() {
        try {
            Class<UIConfig> uiConfigClazz = (Class<UIConfig>) getIntent().getSerializableExtra(EXTRA_UI_CONFIG);
            if (uiConfigClazz == null) {
                return new DefaultUiConfig();
            }
            return uiConfigClazz.newInstance();
        } catch (Exception ex) {
            ex.printStackTrace();
            return new DefaultUiConfig();
        }
    }

    private void initManualEntryButton(View manualEntryButton) {
        // Show the keyboard button
        if (suppressManualEntry) {
            manualEntryButton.setVisibility(View.GONE);
        } else {
            manualEntryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setResultAndFinish(RESULT_SCAN_CANCELED, new Intent());
                }
            });
        }
    }

    private void setResultAndFinish(final int resultCode, final Intent data) {
        setResult(resultCode, data);
        finish();
    }

    // for torch test
    public Rect getTorchRect() {
        if (mOverlay == null) {
            return null;
        }
        return mOverlay.getTorchRect();
    }

}
