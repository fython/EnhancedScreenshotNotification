package moe.feng.nevo.decorators.enscreenshot;

import android.app.Activity;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.utils.Executors;

public class PreferencesActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new PreferencesFragment())
                    .commit();
        }
    }

    public static class PreferencesFragment extends PreferenceFragment {

        private Preference mScreenshotPath;
        private Preference mPreferredEditor;
        private CheckBoxPreference mHideLauncherIcon;

        private ScreenshotPreferences mPreferences;

        private final Set<CompletableFuture<?>> mFutures = new LinkedHashSet<>();

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            mPreferences = new ScreenshotPreferences(getContext());

            mScreenshotPath = findPreference("screenshot_path");
            mPreferredEditor = findPreference("preferred_editor");
            mHideLauncherIcon = (CheckBoxPreference) findPreference("hide_launcher_icon");

            updateUiScreenshotPath();
            updateUiPreferredEditor();
            updateUiHideLauncherIcon();

            mScreenshotPath.setOnPreferenceClickListener(this::setupScreenshotPath);
            mPreferredEditor.setOnPreferenceClickListener(this::setupPreferredEditor);
            mHideLauncherIcon.setOnPreferenceChangeListener(this::changeHideLauncherIcon);
        }

        private boolean setupScreenshotPath(Preference p) {
            // TODO
            return true;
        }

        private boolean setupPreferredEditor(Preference p) {
            // TODO
            return true;
        }

        private boolean changeHideLauncherIcon(Preference p, Object o) {
            final boolean b = (Boolean) o;
            // TODO
            return true;
        }

        @MainThread
        private void updateUiScreenshotPath() {
            mFutures.add(CompletableFuture
                    .supplyAsync(mPreferences::getScreenshotPath)
                    .thenApply(path ->
                            getString(R.string.pref_screenshots_store_path_summary_format, path))
                    .whenCompleteAsync((summary, thr) -> mScreenshotPath.setSummary(summary),
                            Executors.getMainThreadExecutor()));
        }

        @MainThread
        private void updateUiPreferredEditor() {
            mFutures.add(CompletableFuture
                    .supplyAsync(() -> mPreferences.getPreferredEditorTitle(getContext()))
                    .thenApply(optional ->
                            optional.orElseGet(() -> getString(R.string.ask_every_time)))
                    .thenApply(title ->
                            getString(R.string.pref_preferred_editor_summary, title))
                    .whenCompleteAsync((summary, thr) -> mPreferredEditor.setSummary(summary),
                            Executors.getMainThreadExecutor()));
        }

        @MainThread
        private void updateUiHideLauncherIcon() {
            // TODO
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            for (Future future : mFutures) {
                if (!future.isCancelled() && !future.isDone()) {
                    future.cancel(true);
                }
            }
            mFutures.clear();
        }
    }
}
