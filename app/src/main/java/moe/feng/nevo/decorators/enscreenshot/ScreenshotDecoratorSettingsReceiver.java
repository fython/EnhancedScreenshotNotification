package moe.feng.nevo.decorators.enscreenshot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.utils.IntentUtils;

public class ScreenshotDecoratorSettingsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        context.startActivity(new Intent(context, PreferencesActivity.class)
                .setAction(Intent.ACTION_APPLICATION_PREFERENCES));
        IntentUtils.closeSystemDialogs(context);
    }
}
