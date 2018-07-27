package moe.feng.nevo.decorators.enscreenshot.utils;

import android.os.LocaleList;
import android.util.Pair;
import androidx.annotation.NonNull;

import java.util.*;

public final class FormatUtils {

    private static final Map<Locale, List<String>> sEditActionTextFormats = new HashMap<>();

    static {
        sEditActionTextFormats.put(
                Locale.ENGLISH,
                Arrays.asList("Edit", "Edit in %s", "%s")
        );
        sEditActionTextFormats.put(
                Locale.SIMPLIFIED_CHINESE,
                Arrays.asList("编辑", "在%s编辑", "在 %s 编辑", "%s")
        );
        sEditActionTextFormats.put(
                Locale.TRADITIONAL_CHINESE,
                Arrays.asList("編輯", "在%s編輯", "在 %s 編輯", "%s")
        );
    }

    @NonNull
    private static Locale chooseAvailableLocale(@NonNull Locale locale) {
        if (sEditActionTextFormats.containsKey(locale)) {
            return locale;
        }
        for (Locale loc : sEditActionTextFormats.keySet()) {
            if (loc.getLanguage().equals(locale.getLanguage())) {
                return loc;
            }
        }
        return Locale.ENGLISH;
    }

    @NonNull
    private static Locale chooseAvailableLocale(@NonNull LocaleList localeList) {
        for (int i = 0; i < localeList.size(); i++) {
            final Locale locale = localeList.get(i);
            if (sEditActionTextFormats.containsKey(locale)) {
                return locale;
            }
            for (Locale loc : sEditActionTextFormats.keySet()) {
                if (loc.getLanguage().equals(locale.getLanguage())) {
                    return loc;
                }
            }
        }
        return Locale.ENGLISH;
    }

    @NonNull
    public static List<String> getEditActionTextFormats(@NonNull Locale locale) {
        return Collections.unmodifiableList(sEditActionTextFormats.get(chooseAvailableLocale(locale)));
    }

    @NonNull
    public static Pair<Locale, List<String>> getEditActionTextFormats(@NonNull LocaleList localeList) {
        final Locale actualLocale = chooseAvailableLocale(localeList);
        final List<String> formats = Collections.unmodifiableList(sEditActionTextFormats.get(actualLocale));
        return Pair.create(actualLocale, formats);
    }

    @NonNull
    public static String safeGetEditActionTextFormat(@NonNull Locale locale, int index) {
        final List<String> formats = getEditActionTextFormats(locale);
        if (index >= formats.size()) {
            return formats.get(1);
        } else {
            return formats.get(index);
        }
    }

}
