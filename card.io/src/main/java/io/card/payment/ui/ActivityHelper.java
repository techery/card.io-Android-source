package io.card.payment.ui;

/* ActivityHelper.java
 * See the file "LICENSE.md" for the full license governing this code.
 */

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class ActivityHelper {

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setFlagSecure(Activity activity) {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            // Prevent screenshots; FLAG_SECURE is restricted to HoneyComb and above to prevent issues with some Samsung handsets
            // Please see http://stackoverflow.com/questions/9822076/how-do-i-prevent-android-taking-a-screenshot-when-my-app-goes-to-the-background
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
