package moe.feng.nevo.decorators.enscreenshot.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import android.provider.ContactsContract;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.gms.vision.barcode.Barcode;
import moe.feng.nevo.decorators.enscreenshot.R;
import moe.feng.nevo.decorators.enscreenshot.ScreenshotDecorator;

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

    public static Intent createViewIntent(@NonNull Uri uri) {
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    public static Intent createDialIntent(@NonNull Uri uri) {
        return new Intent(Intent.ACTION_DIAL, uri);
    }

    public static Intent createMailSendToIntent(@NonNull String address, String subject, String text) {
        final Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + address));
        intent.putExtra(Intent.EXTRA_EMAIL, address);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        return intent;
    }

    public static void closeSystemDialogs(@NonNull Context context) {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(intent);
    }

    public static Intent createCopyIntent(@NonNull String text) {
        final Intent copyIntent = new Intent();
        copyIntent.setComponent(ScreenshotDecorator.CopyUrlReceiver.COMPONENT_NAME);
        copyIntent.putExtra("data", text);
        return copyIntent;
    }

    public static Intent createAddContactFromBarcode(@NonNull Barcode barcode) {
        if (Barcode.CONTACT_INFO != barcode.valueFormat || barcode.contactInfo == null) {
            throw new IllegalArgumentException("The expected value format of Barcode is Barcode.CONTACT_INFO.");
        }
        final Barcode.ContactInfo src = barcode.contactInfo;
        final Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
        intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);

        if (src.name != null) {
            final Barcode.PersonName name = src.name;
            if (name.formattedName != null) {
                intent.putExtra(ContactsContract.Intents.Insert.NAME, name.formattedName);
            }
        }

        if (src.emails != null && src.emails.length > 0) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, src.emails[0].address);
            int firstType = convertEmailTypeBarcodeToContract(src.emails[0].type);
            if (firstType != -1) {
                intent.putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, firstType);
            }
            if (src.emails.length > 1) {
                intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, src.emails[1].address);
                int secondType = convertEmailTypeBarcodeToContract(src.emails[1].type);
                if (secondType != -1) {
                    intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL_TYPE, secondType);
                }
                if (src.emails.length > 2) {
                    intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_EMAIL, src.emails[2].address);
                    int thirdType = convertEmailTypeBarcodeToContract(src.emails[2].type);
                    if (thirdType != -1) {
                        intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_EMAIL_TYPE, thirdType);
                    }
                }
            }
        }

        if (src.phones != null && src.phones.length > 0) {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, src.phones[0].number);
            int firstType = convertPhoneTypeBarcodeToContract(src.phones[0].type);
            if (firstType != -1) {
                intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, firstType);
            }
            if (src.phones.length > 1) {
                intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, src.phones[1].number);
                int secondType = convertPhoneTypeBarcodeToContract(src.phones[1].type);
                if (secondType != -1) {
                    intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE, secondType);
                }
                if (src.phones.length > 2) {
                    intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, src.phones[2].number);
                    int thirdType = convertPhoneTypeBarcodeToContract(src.phones[2].type);
                    if (thirdType != -1) {
                        intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE, thirdType);
                    }
                }
            }
        }

        return intent;
    }

    private static int convertEmailTypeBarcodeToContract(int barcodeEmailType) {
        switch (barcodeEmailType) {
            case Barcode.Email.WORK: {
                return ContactsContract.CommonDataKinds.Email.TYPE_WORK;
            }
            case Barcode.Email.HOME: {
                return ContactsContract.CommonDataKinds.Email.TYPE_HOME;
            }
        }
        return -1;
    }

    private static int convertPhoneTypeBarcodeToContract(int barcodePhoneType) {
        switch (barcodePhoneType) {
            case Barcode.Phone.WORK: {
                return ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
            }
            case Barcode.Phone.HOME: {
                return ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
            }
            case Barcode.Phone.MOBILE: {
                return ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
            }
            case Barcode.Phone.FAX: {
                return ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK;
            }
        }
        return -1;
    }

}
