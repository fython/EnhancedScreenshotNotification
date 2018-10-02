package moe.feng.nevo.decorators.enscreenshot;

import android.app.Application;
import android.content.res.Configuration;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import moe.feng.nevo.decorators.enscreenshot.utils.FormatUtils;

public final class DecoratorApplication extends Application {

    public static FirebaseApp getFirebaseApp() {
        return FirebaseApp.getInstance(FIREBASE_NAME);
    }

    public static final String FILE_PROVIDER_AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.files";

    public static final String FIREBASE_NAME = "[ESN]";

    @Override
    public void onCreate() {
        super.onCreate();

        final FirebaseOptions firebaseOptions = new FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.APPLICATION_ID)
                .setApiKey(getString(R.string.google_api_key))
                .setProjectId(getString(R.string.project_id))
                .build();
        FirebaseApp.initializeApp(this, firebaseOptions, FIREBASE_NAME);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final ScreenshotPreferences preferences = new ScreenshotPreferences(this);
        preferences.setEditActionTextFormat(FormatUtils.getEditActionTextFormats(newConfig.getLocales()).second.get(1));
    }

}
