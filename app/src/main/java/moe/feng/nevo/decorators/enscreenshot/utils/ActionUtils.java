package moe.feng.nevo.decorators.enscreenshot.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

public final class ActionUtils {

    private ActionUtils() {
        throw new InstantiationError();
    }

    public static void viewAppInMarket(@NonNull Context context, @NonNull String packageName) {
        try {
            context.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "market://details?id=" + packageName)));
        } catch (android.content.ActivityNotFoundException ignored) {
            context.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://play.google.com/store/apps/details?id=" + packageName)));
        }
    }

}
