package moe.feng.nevo.decorators.enscreenshot.utils;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;

public final class PermissionUtils {

    private PermissionUtils() {}

    /**
     * Check if application can draw over other apps
     * @param context Context
     * @return Boolean
     */
    public static boolean canDrawOverlays(@NonNull Context context) {
        final int sdkInt = Build.VERSION.SDK_INT;
        if (sdkInt >= Build.VERSION_CODES.M) {
            if (sdkInt == Build.VERSION_CODES.O) {
                // Sometimes Settings.canDrawOverlays returns false after allowing permission.
                // Google Issue Tracker: https://issuetracker.google.com/issues/66072795
                AppOpsManager appOpsMgr = context.getSystemService(AppOpsManager.class);
                if (appOpsMgr != null) {
                    int mode = appOpsMgr.checkOpNoThrow(
                            "android:system_alert_window",
                            android.os.Process.myUid(),
                            context.getPackageName()
                    );
                    return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_IGNORED;
                } else {
                    return false;
                }
            }
            // Default
            return android.provider.Settings.canDrawOverlays(context);
        }
        return true; // This fallback may returns a incorrect result.
    }

    /**
     * Request overlay permission to draw over other apps
     * @param activity Current activity
     * @param requestCode Request code
     */
    public static void requestOverlayPermission(@NonNull Activity activity, int requestCode) {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
        activity.startActivityForResult(intent, requestCode);
        // TODO Support third-party customize ROM?
    }

}
