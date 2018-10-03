package moe.feng.nevo.decorators.enscreenshot;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.*;
import android.preference.*;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.utils.*;

public class PreferencesActivity extends Activity {

    private static final String ACTION_UPDATE_SETTINGS =
            BuildConfig.APPLICATION_ID + ".action.UPDATE_SETTINGS";

    private static final String EXTRA_UPDATE_TYPE =
            BuildConfig.APPLICATION_ID + ".extra.UPDATE_TYPE";

    private static final String NEVOLUTION_PACKAGE = "com.oasisfeng.nevo";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Intent.ACTION_APPLICATION_PREFERENCES.equals(
                Optional.ofNullable(getIntent()).map(Intent::getAction).orElse(""))) {
            Objects.requireNonNull(getActionBar()).setDisplayHomeAsUpEnabled(true);
        }

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
        }, Executors.mainThread());

        if (!PermissionUtils.canDrawOverlays(this)) {
            PermissionUtils.requestOverlayPermission(this, 0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_preferences, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_feedback) {
            final String subject = "Enhanced Screenshot Notification - Feedback";
            final String text = "Describe your suggestions here...\n\n"
                    + DeviceInfoPrinter.DIVIDER + DeviceInfoPrinter.print(this);
            final Intent sendToIntent = IntentUtils.createMailSendToIntent("fythonx@gmail.com", subject, text);
            if (sendToIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(Intent.createChooser(sendToIntent, getString(R.string.action_feedback)));
            } else {
                final Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/fython/EnhancedScreenshotNotification/issues"));
                startActivity(intent);
            }
            return true;
        } else if (item.getItemId() == R.id.action_help) {
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://gwo.app/help/esn"));
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_privacy_policy) {
            startActivity(IntentUtils.createViewIntent(Uri.parse(getString(R.string.action_privacy_policy_url))));
        }
        return super.onOptionsItemSelected(item);
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
        private static final String KEY_SHOW_SCREENSHOT_DETAILS = "show_screenshot_details";
        private static final String KEY_PREVIEW_IN_FLOATING_WINDOW = "preview_in_floating_window";
        private static final String KEY_REPLACE_NOTIFICATION_WITH_PREVIEW = "replace_notification_with_preview";
        private static final String KEY_DETECT_BARCODE = "detect_barcode";
        private static final String KEY_TELEGRAM = "telegram";

        private static final int REQUEST_PERMISSION = 10;

        private ListPreference mActionAfterSharing;
        private SwitchPreference mStoragePermission;
        private Preference mScreenshotPath;
        private Preference mPreferredEditor;
        private CheckBoxPreference mHideLauncherIcon;
        private ListPreference mEditActionTextFormat;
        private CheckBoxPreference mShowScreenshotsCount;
        private CheckBoxPreference mShowScreenshotDetails;
        private SwitchPreference mPreviewInFloatingWindow;
        private SwitchPreference mReplaceNotificationWithPreview;
        private SwitchPreference mDetectBarcode;

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
            mShowScreenshotDetails = (CheckBoxPreference) findPreference(KEY_SHOW_SCREENSHOT_DETAILS);
            mPreviewInFloatingWindow = (SwitchPreference) findPreference(KEY_PREVIEW_IN_FLOATING_WINDOW);
            mReplaceNotificationWithPreview = (SwitchPreference) findPreference(KEY_REPLACE_NOTIFICATION_WITH_PREVIEW);
            final Preference githubPref = findPreference(KEY_GITHUB_REPO);
            mDetectBarcode = (SwitchPreference) findPreference(KEY_DETECT_BARCODE);
            final Preference telegramPref = findPreference(KEY_TELEGRAM);

            updateUiActionAfterSharing();
            updateUiStoragePermission();
            updateUiScreenshotPath();
            updateUiPreferredEditor();
            updateUiHideLauncherIcon();
            updateUiShowScreenshotsCount();
            updateUiShowScreenshotDetails();
            updatePreviewInFloatingWindow();
            updateReplaceNotificationWithPreview();
            updateDetectBarcode();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                getPreferenceScreen().removePreference(findPreference("notification_settings"));
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                mPreviewInFloatingWindow.setEnabled(false);
                mPreviewInFloatingWindow.setSummary(R.string.pref_preview_in_floating_window_summary_unsupported);
                getPreferenceScreen().removePreference(mReplaceNotificationWithPreview);
            }

            mPreviewInFloatingWindow.setOnPreferenceChangeListener((p, o) -> {
                mPreferences.setPreviewInFloatingWindow((boolean) o);
                mReplaceNotificationWithPreview.setEnabled((boolean) o);
                return true;
            });
            mReplaceNotificationWithPreview.setOnPreferenceChangeListener((p, o) -> {
                mPreferences.setReplaceNotificationWithPreview((boolean) o);
                return true;
            });
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
            mShowScreenshotDetails.setOnPreferenceChangeListener((p, o) -> {
                mPreferences.setShowScreenshotDetails((boolean) o);
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
            mDetectBarcode.setOnPreferenceChangeListener((p, o) -> {
                mPreferences.setDetectBarcode((boolean) o);
                return true;
            });
            telegramPref.setOnPreferenceClickListener(p -> {
                try {
                    startActivity(IntentUtils.createViewIntent(Uri.parse(getString(R.string.telegram_discussion_url))));
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
            mFutures.add(new PreferredEditorChooserDialog().postShow(getContext(), getChildFragmentManager()));
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
                            Executors.mainThread()));
        }

        private void updateUiStoragePermission() {
            mFutures.add(CompletableFuture
                    .supplyAsync(() -> PermissionRequestActivity
                            .checkIfPermissionTypeGranted(getContext(), PermissionRequestActivity.TYPE_STORAGE))
                    .whenCompleteAsync((bool, thr) -> {
                                mStoragePermission.setChecked(bool);
                                mStoragePermission.setEnabled(!bool);
                            },
                            Executors.mainThread()));
        }

        private void updateUiScreenshotPath() {
            mFutures.add(CompletableFuture
                    .supplyAsync(mPreferences::getScreenshotPath)
                    .thenApply(path ->
                            getString(R.string.pref_screenshots_store_path_summary_format, path))
                    .whenCompleteAsync((summary, thr) -> mScreenshotPath.setSummary(summary),
                            Executors.mainThread()));
        }

        private void updateUiPreferredEditor() {
            mFutures.add(CompletableFuture
                    .supplyAsync(() ->
                            Pair.create(mPreferences.getPreferredEditorTitle(), mPreferences.getPreferredEditorIcon()))
                    .whenCompleteAsync((pair, thr) -> {
                        if (pair.second.isPresent()) {
                            mPreferredEditor.setIcon(pair.second.get());
                        } else {
                            mPreferredEditor.setIcon(null);
                        }
                        mEditActionTextFormat.setEnabled(pair.first.isPresent());
                        updateEditActionTextFormat();
                        mPreferredEditor.setSummary(
                                getString(R.string.pref_preferred_editor_summary,
                                        pair.first.orElseGet(() -> getString(R.string.ask_every_time))
                                )
                        );
                    }, Executors.mainThread()));
        }

        private void updateUiHideLauncherIcon() {
            mFutures.add(CompletableFuture
                    .supplyAsync(mPreferences::isHideLauncherIcon)
                    .whenCompleteAsync((bool, thr) -> mHideLauncherIcon.setChecked(bool),
                            Executors.mainThread()));
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
                    }, Executors.mainThread()));
        }

        private void updateUiShowScreenshotsCount() {
            mShowScreenshotsCount.setChecked(mPreferences.isShowScreenshotsCount());
        }

        private void updateUiShowScreenshotDetails() {
            mShowScreenshotDetails.setChecked(mPreferences.isShowScreenshotDetails());
        }

        private void updatePreviewInFloatingWindow() {
            mPreviewInFloatingWindow.setChecked(mPreferences.canPreviewInFloatingWindow());
            mReplaceNotificationWithPreview.setEnabled(mPreviewInFloatingWindow.isChecked());
        }

        private void updateReplaceNotificationWithPreview() {
            mReplaceNotificationWithPreview.setChecked(mPreferences.isReplaceNotificationWithPreview());
        }

        private void updateDetectBarcode() {
            mDetectBarcode.setChecked(mPreferences.shouldDetectBarcode());
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

            static final String STATE_CHOICES = "choices";

            private ScreenshotPreferences mPreferences;

            private List<Pair<ComponentName, String>> mChoices;

            @Override
            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                mPreferences = new ScreenshotPreferences(getContext());
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.pref_preferred_editor);
                final Bundle bundle;
                if (savedInstanceState == null) {
                    bundle = Objects.requireNonNull(getArguments().getBundle(STATE_CHOICES));
                } else {
                    bundle = Objects.requireNonNull(savedInstanceState.getBundle(STATE_CHOICES));
                }
                mChoices = unparcelChoicesList(bundle);
                final Optional<ComponentName> current =
                        mPreferences.getPreferredEditorComponentName();
                int selected = 0;
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
                        (dialog, which) -> {
                            mPreferences.setPreferredEditorComponentName(mChoices.get(which).first);
                            getContext().sendBroadcast(new Intent(ACTION_UPDATE_SETTINGS)
                                    .putExtra(EXTRA_UPDATE_TYPE, KEY_PREFERRED_EDITOR));
                            dismiss();
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                return builder.create();
            }

            @Override
            public void onSaveInstanceState(Bundle outState) {
                super.onSaveInstanceState(outState);
                outState.putBundle(STATE_CHOICES, parcelChoicesList(mChoices));
            }

            public CompletableFuture postShow(@NonNull Context context,@NonNull FragmentManager fm) {
                return CompletableFuture.supplyAsync(() -> parcelChoicesList(buildChoicesList(context)))
                        .whenCompleteAsync((choices, err) -> {
                            final Bundle bundle = new Bundle();
                            bundle.putBundle(STATE_CHOICES, choices);
                            setArguments(bundle);
                            show(fm, KEY_PREFERRED_EDITOR);
                        }, Executors.mainThread());
            }

            static Bundle parcelChoicesList(@NonNull List<Pair<ComponentName, String>> list) {
                final Bundle bundle = new Bundle();
                bundle.putInt("0", list.size());
                final ArrayList<ComponentName> cns = new ArrayList<>();
                final ArrayList<String> strs = new ArrayList<>();
                for (Pair<ComponentName, String> pair : list) {
                    cns.add(pair.first);
                    strs.add(pair.second);
                }
                bundle.putParcelableArrayList("1", cns);
                bundle.putStringArrayList("2", strs);
                return bundle;
            }

            @NonNull
            static List<Pair<ComponentName, String>> unparcelChoicesList(@NonNull Bundle bundle) {
                final List<Pair<ComponentName, String>> list = new ArrayList<>();
                final int size = bundle.getInt("0");
                final List<ComponentName> cn = Objects.requireNonNull(bundle.getParcelableArrayList("1"));
                final List<String> ns = Objects.requireNonNull(bundle.getStringArrayList("2"));
                for (int i = 0; i < size; i++) {
                    list.add(Pair.create(cn.get(i), ns.get(i)));
                }
                return list;
            }

            @NonNull
            static List<Pair<ComponentName, String>> buildChoicesList(
                    @NonNull Context context) {
                final List<Pair<ComponentName, String>> result = new ArrayList<>();

                result.add(Pair.create(null, context.getString(R.string.ask_every_time)));

                final Intent intent = new Intent(Intent.ACTION_EDIT);
                intent.setDataAndType(Uri.parse("content://"), "image/*");
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
