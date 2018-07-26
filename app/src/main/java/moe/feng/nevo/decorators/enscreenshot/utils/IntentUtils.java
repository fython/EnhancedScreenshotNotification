package moe.feng.nevo.decorators.enscreenshot.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import android.widget.Toast;
import androidx.annotation.NonNull;
import moe.feng.nevo.decorators.enscreenshot.R;

public final class IntentUtils {

    private IntentUtils() {
        throw new InstantiationError();
    }

    public static void viewAppInMarket(@NonNull Context context, @NonNull String packageName) {
        try {
            context.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "market://details?id=" + packageName)));
        } catch (android.content.ActivityNotFoundException ignored) {
            try {
                context.startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://play.google.com/store/apps/details?id=" + packageName)));
            } catch (ActivityNotFoundException e) {
                try {
                    Toast.makeText(context, R.string.toast_activity_not_found, Toast.LENGTH_LONG).show();
                } catch (Exception ignore) {

                }
            }
        }
    }

    public static void closeSystemDialogs(@NonNull Context context) {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(intent);
    }

}
