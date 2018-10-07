package moe.feng.nevo.decorators.enscreenshot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import moe.feng.nevo.decorators.enscreenshot.utils.IntentUtils;

public final class SupportUsDialog {

    private static final String ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone";

    private static final String TAG = "SupportUsDialog";

    public static boolean hasInstalledAlipayClient(@NonNull Context context) {
        final PackageManager pm = context.getPackageManager();
        try {
            final PackageInfo info = pm.getPackageInfo(ALIPAY_PACKAGE_NAME, PackageManager.MATCH_DISABLED_COMPONENTS);
            return info != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void start(@NonNull Context context) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "BuildConfig.SHOW_ALIPAY_ALWAYS: " + BuildConfig.SHOW_ALIPAY_ALWAYS);
            Log.i(TAG, "hasInstalledAlipayClient: " + hasInstalledAlipayClient(context));
        }
        final boolean shouldShowAlipay = BuildConfig.SHOW_ALIPAY_ALWAYS || hasInstalledAlipayClient(context);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.support_us_dialog_title);
        builder.setMessage(HtmlCompat.fromHtml(context.getString(shouldShowAlipay ?
                R.string.support_us_dialog_message : R.string.support_us_dialog_message_play_store), 0));
        builder.setPositiveButton(R.string.rate_us, (dialog, which) -> {
            context.startActivity(IntentUtils.createViewIntent(
                    Uri.parse("https://play.google.com/store/apps/details?id=moe.feng.nevo.decorators.enscreenshot")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        if (shouldShowAlipay) {
            builder.setNeutralButton(R.string.donate, (dialog, which) -> startAlipayDonateDialog(context));
        }
        builder.show();
    }

    public static void startAlipayDonateDialog(@NonNull Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.alipay_dialog_title)
                .setMessage(R.string.alipay_dialog_message)
                .setPositiveButton(R.string.alipay_installed_button, (dialog, which) -> {
                    context.startActivity(IntentUtils.createViewIntent(
                            Uri.parse(BuildConfig.ALIPAY_SUPPORT_URL)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

}
