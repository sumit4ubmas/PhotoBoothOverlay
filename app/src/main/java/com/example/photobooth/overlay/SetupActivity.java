package com.example.photobooth.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * SetupActivity:
 * - Shown once when the user first installs the overlay.
 * - Guides the user to grant "Draw over other apps" permission.
 * - Once granted, starts the OverlayService and closes itself.
 */
public class SetupActivity extends Activity {

    private static final int REQ_OVERLAY_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        TextView statusText = findViewById(R.id.statusText);
        Button permissionBtn = findViewById(R.id.permissionButton);
        Button startBtn = findViewById(R.id.startButton);

        updateUI(statusText, permissionBtn, startBtn);

        permissionBtn.setOnClickListener(v -> {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
        });

        startBtn.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
                Toast.makeText(this, "Photo Booth overlay started!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Please grant Draw Over Apps permission first.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView statusText = findViewById(R.id.statusText);
        Button permissionBtn = findViewById(R.id.permissionButton);
        Button startBtn = findViewById(R.id.startButton);
        updateUI(statusText, permissionBtn, startBtn);

        // Auto-proceed if permission already granted
        if (Settings.canDrawOverlays(this)) {
            startOverlayService();
            finish();
        }
    }

    private void updateUI(TextView statusText, Button permissionBtn, Button startBtn) {
        if (Settings.canDrawOverlays(this)) {
            statusText.setText("✅ Permission granted! Tap START to activate the overlay.");
            permissionBtn.setEnabled(false);
            startBtn.setEnabled(true);
        } else {
            statusText.setText("⚠️ Please grant \"Draw over other apps\" permission for the Photo Booth overlay button to appear on top of all screens.");
            permissionBtn.setEnabled(true);
            startBtn.setEnabled(false);
        }
    }

    private void startOverlayService() {
        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY_PERMISSION) {
            // onResume will handle the UI update
        }
    }
}
