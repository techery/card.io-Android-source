package io.card.development;

/* SampleActivity.java
 * See the file "LICENSE.md" for the full license governing this code.
 */

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.card.payment.CardIOActivity;
import io.card.payment.CardType;
import io.card.payment.CreditCard;
import io.card.payment.i18n.StringKey;
import io.card.payment.i18n.SupportedLocale;
import io.card.payment.i18n.locales.LocalizedStringsList;

public class SampleActivity extends Activity {

    protected static final String TAG = SampleActivity.class.getSimpleName();

    private static final int REQUEST_SCAN = 100;
    private static final int REQUEST_AUTOTEST = 200;

    private CheckBox mEnableExpiryToggle;
    private CheckBox mScanExpiryToggle;
    private CheckBox mSuppressScanToggle;

    private TextView mResultLabel;
    private ImageView mResultImage;

    private boolean autotestMode;
    private int numAutotestsPassed;
    private Spinner mLanguageSpinner;
    private EditText mUnblurEdit;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_activity);

        mEnableExpiryToggle = (CheckBox) findViewById(R.id.gather_expiry);
        mScanExpiryToggle = (CheckBox) findViewById(R.id.scan_expiry);
        mSuppressScanToggle = (CheckBox) findViewById(R.id.detect_only);

        mLanguageSpinner = (Spinner) findViewById(R.id.language);
        mUnblurEdit = (EditText) findViewById(R.id.unblur);

        mResultLabel = (TextView) findViewById(R.id.result);
        mResultImage = (ImageView) findViewById(R.id.result_image);

        TextView version = (TextView) findViewById(R.id.version);
        version.setText("card.io library: " + CardIOActivity.sdkVersion() + "\n" +
                "Build date: " + CardIOActivity.sdkBuildDate());

        setScanExpiryEnabled();
        setupLanguageList();
    }

    private void setScanExpiryEnabled() {
        mScanExpiryToggle.setEnabled(mEnableExpiryToggle.isChecked());
    }

    public void onExpiryToggle(View v) {
        setScanExpiryEnabled();
    }

    public void onScan(View pressed) {
        Intent intent = new Intent(this, CardIOActivity.class)
                .putExtra(CardIOActivity.EXTRA_REQUIRE_EXPIRY, mEnableExpiryToggle.isChecked())
                .putExtra(CardIOActivity.EXTRA_SCAN_EXPIRY, mScanExpiryToggle.isChecked())
                .putExtra(CardIOActivity.EXTRA_LANGUAGE_OR_LOCALE, (String) mLanguageSpinner.getSelectedItem())
                .putExtra(CardIOActivity.EXTRA_SUPPRESS_SCAN, mSuppressScanToggle.isChecked())
                .putExtra(CardIOActivity.EXTRA_RETURN_CARD_IMAGE, true)
                .putExtra(CardIOActivity.EXTRA_DISABLE_ORIENTATION_CHANGE, true)
                .putExtra(CardIOActivity.EXTRA_UI_CONFIG, UiConfigImpl.class);

        try {
            int unblurDigits = Integer.parseInt(mUnblurEdit.getText().toString());
            intent.putExtra(CardIOActivity.EXTRA_UNBLUR_DIGITS, unblurDigits);
        } catch(NumberFormatException ignored) {}

        startActivityForResult(intent, REQUEST_SCAN);
    }

    public void onAutotest(View v) {
        Log.i(TAG, "\n\n\n ============================== \n" + "successfully completed "
                + numAutotestsPassed + " tests\n" + "beginning new test run\n");

        Intent intent = new Intent(this, CardIOActivity.class)
                .putExtra(CardIOActivity.EXTRA_REQUIRE_EXPIRY, false)
                .putExtra("debug_autoAcceptResult", true);

        startActivityForResult(intent, REQUEST_AUTOTEST);

        autotestMode = true;
    }

    @Override
    public void onStop() {
        super.onStop();

        mResultLabel.setText("");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.v(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ", " + data + ")");

        String outStr = new String();

        if ((requestCode == REQUEST_SCAN || requestCode == REQUEST_AUTOTEST) && data != null
                && data.hasExtra(CardIOActivity.EXTRA_SCAN_RESULT)) {
            CreditCard result = data.getParcelableExtra(CardIOActivity.EXTRA_SCAN_RESULT);
            if (result != null) {
                outStr += "Card number: " + result.getRedactedCardNumber() + "\n";

                CardType cardType = result.getCardType();
                outStr += "Card type: " + cardType.name() + " cardType.getDisplayName(null)="
                        + cardType.getDisplayName(null) + "\n";

                if (mEnableExpiryToggle.isChecked()) {
                    outStr += "Expiry: " + result.expiryMonth + "/" + result.expiryYear + "\n";
                }
            }

            if (autotestMode) {
                numAutotestsPassed++;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onAutotest(null);
                    }
                }, 500);
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            autotestMode = false;
        }

        Bitmap card = CardIOActivity.getCapturedCardImage(data);
        mResultImage.setImageBitmap(card);

        Log.i(TAG, "Set result: " + outStr);

        mResultLabel.setText(outStr);
    }

    private void setupLanguageList() {
        List<String> languages = new ArrayList<>();
        for (SupportedLocale<StringKey> locale : LocalizedStringsList.ALL_LOCALES) {
            languages.add(locale.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, languages);
        mLanguageSpinner.setAdapter(adapter);
        mLanguageSpinner.setSelection(adapter.getPosition("en"));
    }
}
