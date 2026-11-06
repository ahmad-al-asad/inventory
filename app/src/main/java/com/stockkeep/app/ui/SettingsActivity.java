package com.stockkeep.app.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.stockkeep.app.BuildConfig;
import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.data.BackupManager;
import com.stockkeep.app.data.Repository;

import java.time.LocalDate;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private final ActivityResultLauncher<String> exportLauncher =
                registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                    if (uri != null) runExport(uri);
                });

        private final ActivityResultLauncher<String[]> importLauncher =
                registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) confirmImport(uri);
                });

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            ListPreference theme = findPreference(Prefs.KEY_THEME);
            if (theme != null) {
                theme.setOnPreferenceChangeListener((p, value) -> {
                    Prefs.applyTheme((String) value);
                    return true;
                });
            }
            Preference export = findPreference(Prefs.KEY_EXPORT);
            if (export != null) {
                export.setOnPreferenceClickListener(p -> {
                    exportLauncher.launch("StockKeep-backup-" + LocalDate.now() + ".zip");
                    return true;
                });
            }
            Preference restore = findPreference(Prefs.KEY_IMPORT);
            if (restore != null) {
                restore.setOnPreferenceClickListener(p -> {
                    importLauncher.launch(new String[]{"application/zip", "application/x-zip-compressed",
                            "application/octet-stream"});
                    return true;
                });
            }
            Preference version = findPreference(Prefs.KEY_VERSION);
            if (version != null) {
                version.setSummary(getString(R.string.version_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            }
        }

        private void runExport(Uri uri) {
            Context app = requireContext().getApplicationContext();
            Repository.get(app).io().execute(() -> {
                String message;
                try {
                    int n = BackupManager.export(app, uri);
                    message = app.getResources().getQuantityString(R.plurals.export_done, n, n);
                } catch (Exception e) {
                    message = app.getString(R.string.export_failed, String.valueOf(e.getMessage()));
                }
                toast(app, message);
            });
        }

        private void confirmImport(Uri uri) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.restore_confirm_title)
                    .setMessage(R.string.restore_confirm_text)
                    .setPositiveButton(R.string.restore, (d, w) -> runImport(uri))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }

        private void runImport(Uri uri) {
            Context app = requireContext().getApplicationContext();
            Repository.get(app).io().execute(() -> {
                String message;
                try {
                    int n = BackupManager.restore(app, uri);
                    message = app.getResources().getQuantityString(R.plurals.restore_done, n, n);
                } catch (Exception e) {
                    message = app.getString(R.string.restore_failed, String.valueOf(e.getMessage()));
                }
                toast(app, message);
            });
        }

        private static void toast(Context app, String message) {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
        }
    }
}
