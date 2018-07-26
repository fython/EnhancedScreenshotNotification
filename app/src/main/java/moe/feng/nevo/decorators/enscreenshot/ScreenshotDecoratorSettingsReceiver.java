package moe.feng.nevo.decorators.enscreenshot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ScreenshotDecoratorSettingsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        context.startActivity(new Intent(context, PreferencesActivity.class));
        closeSystemDialogs(context);
    }

    private static void closeSystemDialogs(@NonNull Context context) {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(intent);
    }
}
