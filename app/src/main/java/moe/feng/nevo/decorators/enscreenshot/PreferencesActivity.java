package moe.feng.nevo.decorators.enscreenshot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.preference.*;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.utils.FormatUtils;
import moe.feng.nevo.decorators.enscreenshot.utils.IntentUtils;
import moe.feng.nevo.decorators.enscreenshot.utils.Executors;

public class PreferencesActivity extends Activity {

    private static final String ACTION_UPDATE_SETTINGS =
            BuildConfig.APPLICATION_ID + ".action.UPDATE_SETTINGS";

    private static final String EXTRA_UPDATE_TYPE =
            BuildConfig.APPLICATION_ID + ".extra.UPDATE_TYPE";

    private static final String NEVOLUTION_PACKAGE = "com.oasisfeng.nevo";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new PreferencesFragment())
                    .commit();
        }

        CompletableFuture.supplyAsync(() -> {
            PackageInfo packageInfo = null;
            try {
                packageInfo = getPackageManager().getPackageInfo(NEVOLUTION_PACKAGE, 0);
            } catch (PackageManager.NameNotFoundException ignored) {

            }
            return packageInfo != null && NEVOLUTION_PACKAGE.equals(packageInfo.packageName);
        }).whenCompleteAsync((isNevoInstalled, thr) -> {
            if (!isNevoInstalled || thr != null) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.nevolution_missing_title)
                        .setMessage(R.string.nevolution_missing_content)
                        .setCancelable(false)
                        .setPositiveButton(R.string.go_to_google_play, (dialog, which) ->
                                IntentUtils.viewAppInMarket(this, NEVOLUTION_PACKAGE))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setOnDismissListener(dialog -> {
                            if (!isFinishing()) {
                                finish();
                            }
                        })
                        .show();
            }
        }, Executors.getMainThreadExecutor());
    }

    public static class PreferencesFragment extends PreferenceFragment {

        private static final String KEY_ACTION_AFTER_SHARING = "action_after_sharing";
        private static final String KEY_STORAGE_PERMISSION = "storage_permission";
        private static final String KEY_SCREENSHOT_PATH = "screenshot_path";
        private static final String KEY_PREFERRED_EDITOR = "preferred_editor";
        private static final String KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon";
        private static final String KEY_EDIT_ACTION_TEXT_FORMAT = "edit_action_text_format";
        private static final String KEY_GITHUB_REPO = "github_repo";
        private static final String KEY_SHOW_SCREENSHOTS_COUNT = "show_screenshots_count";

        private static final int REQUEST_PERMISSION = 10;

        private ListPreference mActionAfterSharing;
        private SwitchPreference mStoragePermission;
        private Preference mScreenshotPath;
        private Preference mPreferredEditor;
        private CheckBoxPreference mHideLauncherIcon;
        private ListPreference mEditActionTextFormat;
        private CheckBoxPreference mShowScreenshotsCount;

        private ScreenshotPreferences mPreferences;

        private final Set<CompletableFuture<?>> mFutures = new LinkedHashSet<>();

        private final BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @Nullable Intent intent) {
                if (intent == null || intent.getAction() == null) {
                    return;
                }

                if (ACTION_UPDATE_SETTINGS.equals(intent.getAction())) {
                    switch (intent.getStringExtra(EXTRA_UPDATE_TYPE)) {
                        case KEY_SCREENSHOT_PATH:
                            updateUiScreenshotPath();
                            break;
                        case KEY_PREFERRED_EDITOR:
                            updateUiPreferredEditor();
                            break;
                        default:
                            Log.w("Preferences", "Unsupported update type.");
                    }
                }
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            mPreferences = new ScreenshotPreferences(getContext());

            mActionAfterSharing = (ListPreference) findPreference(KEY_ACTION_AFTER_SHARING);
            mStoragePermission = (SwitchPreference) findPreference(KEY_STORAGE_PERMISSION);
            mScreenshotPath = findPreference(KEY_SCREENSHOT_PATH);
            mPreferredEditor = findPreference(KEY_PREFERRED_EDITOR);
            mHideLauncherIcon = (CheckBoxPreference) findPreference(KEY_HIDE_LAUNCHER_ICON);
            mEditActionTextFormat = (ListPreference) findPreference(KEY_EDIT_ACTION_TEXT_FORMAT);
            mShowScreenshotsCount = (CheckBoxPreference) findPreference(KEY_SHOW_SCREENSHOTS_COUNT);
            final Preference githubPref = findPreference(KEY_GITHUB_REPO);

            updateUiActionAfterSharing();
            updateUiStoragePermission();
            updateUiScreenshotPath();
            updateUiPreferredEditor();
            updateUiHideLauncherIcon();
            updateUiShowScreenshotsCount();

            mActionAfterSharing.setOnPreferenceChangeListener((p, o) -> {
                mPreferences.setShareEvolveType(Integer.valueOf((String) o));
                return true;
            });
            mStoragePermission.setOnPreferenceChangeListener((p, o) -> {
                final Intent intent = new Intent(getContext(), PermissionRequestActivity.class);
                intent.putExtra(PermissionRequestActivity.EXTRA_PERMISSION_TYPE,
                        PermissionRequestActivity.TYPE_STORAGE);
                startActivityForResult(intent, REQUEST_PERMISSION);
                return false;
            });
            mEditActionTextFormat.setOnPreferenceChangeListener((p, o) -> {
                mPreferences.setEditActionTextFormat((String) o);
                updateEditActionTextFormat();
                return true;
            });
            mShowScreenshotsCount.setOnPreferenceChangeListener((p, o) -> {
                mPreferences.setShowScreenshotsCount((boolean) o);
                return true;
            });
            mScreenshotPath.setOnPreferenceClickListener(this::setupScreenshotPath);
            mPreferredEditor.setOnPreferenceClickListener(this::setupPreferredEditor);
            mHideLauncherIcon.setOnPreferenceChangeListener(this::changeHideLauncherIcon);
            githubPref.setOnPreferenceClickListener(p -> {
                try {
                    final Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(getString(R.string.pref_github_repo_url)));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getActivity(), R.string.toast_activity_not_found, Toast.LENGTH_LONG).show();
                }
                return true;
            });
        }

        @Override
        public void onResume() {
            getContext().registerReceiver(
                    mUpdateReceiver, new IntentFilter(ACTION_UPDATE_SETTINGS));
            updateUiStoragePermission();
            super.onResume();
        }

        @Override
        public void onPause() {
            getContext().unregisterReceiver(mUpdateReceiver);
            super.onPause();
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            if (REQUEST_PERMISSION == requestCode) {
                if (resultCode == RESULT_OK) {
                    updateUiStoragePermission();
                }
            }
        }

        private boolean setupScreenshotPath(Preference p) {
            new ScreenshotPathEditDialog()
                    .show(getChildFragmentManager(), KEY_SCREENSHOT_PATH);
            return true;
        }

        private boolean setupPreferredEditor(Preference p) {
            new PreferredEditorChooserDialog()
                    .show(getChildFragmentManager(), KEY_PREFERRED_EDITOR);
            return true;
        }

        private boolean changeHideLauncherIcon(Preference p, Object o) {
            final boolean b = (Boolean) o;
            mPreferences.setHideLauncherIcon(b);
            return true;
        }

        private void updateUiActionAfterSharing() {
            mFutures.add(CompletableFuture
                    .supplyAsync(mPreferences::getShareEvolveType)
                    .whenCompleteAsync((type, thr) ->
                                    mActionAfterSharing.setValue(String.valueOf(mPreferences.getShareEvolveType())),
                            Executors.getMainThreadExecutor()));
        }

        private void updateUiStoragePermission() {
            mFutures.add(CompletableFuture
                    .supplyAsync(() -> getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED)
                    .whenCompleteAsync((bool, thr) -> {
                                mStoragePermission.setChecked(bool);
                                mStoragePermission.setEnabled(!bool);
                            },
                            Executors.getMainThreadExecutor()));
        }

        private void updateUiScreenshotPath() {
            mFutures.add(CompletableFuture
                    .supplyAsync(mPreferences::getScreenshotPath)
                    .thenApply(path ->
                            getString(R.string.pref_screenshots_store_path_summary_format, path))
                    .whenCompleteAsync((summary, thr) -> mScreenshotPath.setSummary(summary),
                            Executors.getMainThreadExecutor()));
        }

        private void updateUiPreferredEditor() {
            mFutures.add(CompletableFuture
                    .supplyAsync(mPreferences::getPreferredEditorTitle)
                    .whenCompleteAsync((optional, thr) -> {
                        mEditActionTextFormat.setEnabled(optional.isPresent());
                        updateEditActionTextFormat();
                        mPreferredEditor.setSummary(
                                getString(R.string.pref_preferred_editor_summary,
                                        optional.orElseGet(() -> getString(R.string.ask_every_time))
                                )
                        );
                    }, Executors.getMainThreadExecutor()));
        }

        private void updateUiHideLauncherIcon() {
            mFutures.add(CompletableFuture
                    .supplyAsync(mPreferences::isHideLauncherIcon)
                    .whenCompleteAsync((bool, thr) -> mHideLauncherIcon.setChecked(bool),
                            Executors.getMainThreadExecutor()));
        }

        private void updateEditActionTextFormat() {
            mFutures.add(CompletableFuture
                    .supplyAsync(() -> FormatUtils.getEditActionTextFormats(LocaleList.getDefault()).second)
                    .whenCompleteAsync((list, thr) -> {
                        final Optional<CharSequence> editorTitle = mPreferences.getPreferredEditorTitle();
                        final String currentFormat = mPreferences.getEditActionTextFormat();
                        final String summary;
                        if (editorTitle.isPresent()) {
                            summary = String.format(currentFormat, editorTitle.get());
                        } else {
                            summary = currentFormat.replace("%", "%%");
                        }
                        mEditActionTextFormat.setSummary(summary);
                        mEditActionTextFormat.setEntries(list.stream()
                                .map(format -> {
                                    if (format.contains("%s")) {
                                        return String.format(format, editorTitle.orElse("%s"));
                                    } else {
                                        return format;
                                    }
                                })
                                .toArray(String[]::new));
                        mEditActionTextFormat.setEntryValues(list.toArray(new String[list.size()]));
                        if (list.contains(currentFormat)) {
                            mEditActionTextFormat.setValueIndex(list.indexOf(currentFormat));
                        } else {
                            mEditActionTextFormat.setValueIndex(1);
                        }
                    }, Executors.getMainThreadExecutor()));
        }

        private void updateUiShowScreenshotsCount() {
            mShowScreenshotsCount.setChecked(mPreferences.isShowScreenshotsCount());
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

        public static class ScreenshotPathEditDialog extends DialogFragment {

            private static final String STATE_EDIT_TEXT = "edit_text";

            private ScreenshotPreferences mPreferences;

            private EditText mEditText;

            @Override
            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                mPreferences = new ScreenshotPreferences(getContext());
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.pref_screenshots_store_path);
                @SuppressLint("InflateParams")
                final View view = LayoutInflater.from(builder.getContext())
                        .inflate(R.layout.dialog_layout_edit_text, null);
                mEditText = view.findViewById(android.R.id.edit);
                builder.setView(view);
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (TextUtils.isEmpty(mEditText.getText())) {
                        mPreferences.setScreenshotPath(null);
                    } else {
                        mPreferences.setScreenshotPath(mEditText.getText().toString());
                    }
                    getContext().sendBroadcast(new Intent(ACTION_UPDATE_SETTINGS)
                            .putExtra(EXTRA_UPDATE_TYPE, KEY_SCREENSHOT_PATH));
                });
                builder.setNegativeButton(android.R.string.cancel, null);

                if (savedInstanceState == null) {
                    mEditText.setText(mPreferences.getScreenshotPath());
                } else {
                    mEditText.onRestoreInstanceState(
                            savedInstanceState.getParcelable(STATE_EDIT_TEXT));
                }

                return builder.create();
            }

            @Override
            public void onSaveInstanceState(Bundle outState) {
                super.onSaveInstanceState(outState);
                if (mEditText != null) {
                    outState.putParcelable(STATE_EDIT_TEXT, mEditText.onSaveInstanceState());
                }
            }
        }

        public static class PreferredEditorChooserDialog extends DialogFragment {

            private ScreenshotPreferences mPreferences;

            private List<Pair<ComponentName, String>> mChoices;

            private int selected;

            @Override
            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                mPreferences = new ScreenshotPreferences(getContext());
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.pref_preferred_editor);
                // TODO: build list async
                mChoices = buildChoicesList(getContext());
                final Optional<ComponentName> current =
                        mPreferences.getPreferredEditorComponentName();
                if (current.isPresent() && mPreferences.isPreferredEditorAvailable()) {
                    for (int i = 1; i < mChoices.size(); i++) {
                        if (current.get().equals(mChoices.get(i).first)) {
                            selected = i;
                            break;
                        }
                    }
                }
                builder.setSingleChoiceItems(
                        mChoices.stream().map(p -> p.second).toArray(CharSequence[]::new),
                        selected,
                        (dialog, which) -> selected = which);
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mPreferences.setPreferredEditorComponentName(mChoices.get(selected).first);
                    getContext().sendBroadcast(new Intent(ACTION_UPDATE_SETTINGS)
                            .putExtra(EXTRA_UPDATE_TYPE, KEY_PREFERRED_EDITOR));
                });
                builder.setNegativeButton(android.R.string.cancel, null);
                return builder.create();
            }

            @NonNull
            private static List<Pair<ComponentName, String>> buildChoicesList(
                    @NonNull Context context) {
                final List<Pair<ComponentName, String>> result = new ArrayList<>();

                result.add(Pair.create(null, context.getString(R.string.ask_every_time)));

                final Intent intent = new Intent(Intent.ACTION_EDIT);
                intent.setType("image/*");
                final PackageManager pm = context.getPackageManager();
                final List<ResolveInfo> resolve = context.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.GET_META_DATA);
                resolve.stream()
                        .map(item -> Pair.create(
                                ComponentName.createRelative(
                                        item.activityInfo.packageName, item.activityInfo.name),
                                item.loadLabel(pm).toString()
                        ))
                        .forEach(result::add);

                return result;
            }
        }
    }
}
