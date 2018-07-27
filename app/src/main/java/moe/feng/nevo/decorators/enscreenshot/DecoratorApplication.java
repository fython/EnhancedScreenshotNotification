package moe.feng.nevo.decorators.enscreenshot;

import android.app.Application;
import android.content.res.Configuration;
import moe.feng.nevo.decorators.enscreenshot.utils.FormatUtils;

public final class DecoratorApplication extends Application {

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final ScreenshotPreferences preferences = new ScreenshotPreferences(this);
        preferences.setEditActionTextFormat(FormatUtils.getEditActionTextFormats(newConfig.getLocales()).second.get(1));
    }

}
