package com.stockkeep.app.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.security.AppLock;

/** Full-screen cover shown on top of the app until the user unlocks with fingerprint/face or the phone's PIN. */
public class LockActivity extends AppCompatActivity {

    private BiometricPrompt prompt;
    private TextView message;
    private boolean prompting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
        message = findViewById(R.id.lock_message);
        findViewById(R.id.btn_unlock).setOnClickListener(v -> unlock());

        // Back leaves the app instead of skipping the lock.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        prompting = false;
                        AppLock.get().markUnlocked();
                        finish();
                        overridePendingTransition(0, android.R.anim.fade_out);
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence err) {
                        prompting = false;
                        boolean cancelled = code == BiometricPrompt.ERROR_USER_CANCELED
                                || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || code == BiometricPrompt.ERROR_CANCELED;
                        message.setText(cancelled ? getString(R.string.lock_text) : err);
                    }
                });

        if (savedInstanceState == null) message.post(this::unlock);
    }

    private void unlock() {
        if (prompting || isFinishing()) return;
        if (!AppLock.isAvailable(this)) {
            // The phone's screen lock was removed, so there is nothing to unlock with. Don't lock the user out.
            Prefs.setAppLock(this, false);
            AppLock.get().markUnlocked();
            Toast.makeText(this, R.string.lock_turned_off_no_screen_lock, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        prompting = true;
        message.setText(R.string.lock_text);
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_prompt_title))
                .setSubtitle(getString(R.string.lock_prompt_subtitle))
                .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build());
    }
}
