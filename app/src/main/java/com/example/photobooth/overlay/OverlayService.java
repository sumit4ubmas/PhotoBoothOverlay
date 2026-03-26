package com.example.photobooth.overlay;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * OverlayService:
 * Runs as a foreground service and draws a persistent floating button
 * (TYPE_APPLICATION_OVERLAY) that always sits on top of every app.
 *
 * Tapping the button:
 *   - If Photo Booth is NOT running → launches it
 *   - If Photo Booth IS running    → brings it to foreground (or closes it
 *     if it's already in foreground — toggle behaviour)
 *
 * The button is draggable so the user can reposition it anywhere on screen.
 */
public class OverlayService extends Service {

    private static final String CHANNEL_ID      = "overlay_channel";
    private static final int    NOTIFICATION_ID  = 42;

    // Package and main activity of the Photo Booth app being controlled
    private static final String PHOTO_BOOTH_PKG      = "com.example.photobooth";
    private static final String PHOTO_BOOTH_ACTIVITY = "com.example.photobooth.MainActivity";

    private WindowManager   windowManager;
    private View            overlayView;
    private WindowManager.LayoutParams layoutParams;

    // For drag tracking
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 10; // px

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupOverlayButton();
    }

    // ── Overlay button setup ────────────────────────────────────────────────

    private void setupOverlayButton() {
        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.overlay_button, null);

        ImageButton fab = overlayView.findViewById(R.id.overlayFab);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                // FLAG_NOT_FOCUSABLE  → overlay never steals focus from other apps
                // FLAG_LAYOUT_IN_SCREEN → stays within screen bounds
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        // Default position: top-right corner
        layoutParams.gravity = Gravity.TOP | Gravity.END;
        layoutParams.x = 20;
        layoutParams.y = 80;

        // Touch: handle drag + tap
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX      = layoutParams.x;
                        initialY      = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging    = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(event.getRawX() - initialTouchX);
                        float dy = Math.abs(event.getRawY() - initialTouchY);
                        if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            // WindowManager gravity is TOP|END → x is from right edge
                            layoutParams.x = initialX - (int)(event.getRawX() - initialTouchX);
                            layoutParams.y = initialY + (int)(event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(overlayView, layoutParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            // It's a tap — toggle Photo Booth
                            togglePhotoBooth();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, layoutParams);
    }

    // ── Photo Booth launch / close logic ───────────────────────────────────

    private void togglePhotoBooth() {
        if (isPhotoBoothInForeground()) {
            // App is visible → close/move to background
            closePhotoBooth();
        } else if (isPhotoBoothRunning()) {
            // App is running but in background → bring to front
            bringPhotoBoothToFront();
        } else {
            // App is not running → launch it
            launchPhotoBooth();
        }
    }

    private void launchPhotoBooth() {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(PHOTO_BOOTH_PKG);
            if (launchIntent != null) {
                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                );
                startActivity(launchIntent);
            } else {
                Toast.makeText(this, "Photo Booth app not found. Is it installed?", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch Photo Booth: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void bringPhotoBoothToFront() {
        try {
            Intent intent = new Intent();
            intent.setClassName(PHOTO_BOOTH_PKG, PHOTO_BOOTH_ACTIVITY);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(intent);
        } catch (Exception e) {
            launchPhotoBooth(); // Fallback
        }
    }

    private void closePhotoBooth() {
        // Send the app to background by launching the home screen
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
    }

    /** Returns true if Photo Booth process is running (foreground or background). */
    @SuppressWarnings("deprecation")
    private boolean isPhotoBoothRunning() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return false;
        for (ActivityManager.RunningAppProcessInfo p : processes) {
            if (PHOTO_BOOTH_PKG.equals(p.processName)) return true;
        }
        return false;
    }

    /** Returns true if Photo Booth is the currently visible/foreground app. */
    @SuppressWarnings("deprecation")
    private boolean isPhotoBoothInForeground() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks == null || tasks.isEmpty()) return false;
        ActivityManager.RunningTaskInfo top = tasks.get(0);
        return top.topActivity != null
                && PHOTO_BOOTH_PKG.equals(top.topActivity.getPackageName());
    }

    // ── Foreground notification ─────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Photo Booth Overlay",
                    NotificationManager.IMPORTANCE_LOW  // silent, no sound
            );
            channel.setDescription("Keeps the Photo Booth launch button visible");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, SetupActivity.class);
        PendingIntent stopPending = PendingIntent.getActivity(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Photo Booth Overlay Active")
                .setContentText("Tap the floating camera button to open / close Photo Booth")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(stopPending)
                .build();
    }

    // ── Service lifecycle ───────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Restart automatically if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
