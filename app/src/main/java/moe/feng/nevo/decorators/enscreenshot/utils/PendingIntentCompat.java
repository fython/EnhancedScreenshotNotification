package moe.feng.nevo.decorators.enscreenshot.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;

public final class PendingIntentCompat {

    private PendingIntentCompat() {}

    public static PendingIntent getForegroundService(@NonNull Context context, int requestCode, @NonNull Intent serviceIntent, int flag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, requestCode, serviceIntent, flag);
        } else {
            return PendingIntent.getService(context, requestCode, serviceIntent, flag);
        }
    }

}
