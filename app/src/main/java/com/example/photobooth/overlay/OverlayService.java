package com.example.photobooth.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String CHANNEL_ID     = "overlay_channel";
    private static final int    NOTIFICATION_ID = 42;
    private static final String PHOTO_BOOTH_PKG = "com.example.photobooth";

    private static final int PEEK_WIDTH     = 36;
    private static final int EXPANDED_WIDTH = 140;
    private static final int BUTTON_HEIGHT  = 72;
    private static final int AUTO_HIDE_MS   = 4000;
    private static final int DRAG_THRESHOLD = 12;

    private WindowManager windowManager;
    private LinearLayout  overlayView;
    private WindowManager.LayoutParams layoutParams;

    private boolean isExpanded     = false;
    private boolean photoBoothOpen = false;
    private boolean isDragging     = false;

    private TextView  arrowTab;
    private ImageView cameraIcon;
    private TextView  statusLabel;

    private final Handler  handler          = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::collapse;

    // Touch tracking
    private float downRawX, downRawY;
    private int   downLayoutX, downLayoutY;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildOverlay();
    }

    private void buildOverlay() {
        overlayView = new LinearLayout(this);
        overlayView.setOrientation(LinearLayout.HORIZONTAL);
        overlayView.setGravity(Gravity.CENTER_VERTICAL);
        applyPillBackground(0xEEe94560);

        arrowTab = new TextView(this);
        arrowTab.setText("<");
        arrowTab.setTextColor(Color.WHITE);
        arrowTab.setTextSize(18f);
        arrowTab.setPadding(8, 0, 8, 0);
        arrowTab.setGravity(Gravity.CENTER);

        cameraIcon = new ImageView(this);
        cameraIcon.setImageResource(android.R.drawable.ic_menu_camera);
        cameraIcon.setColorFilter(Color.WHITE);
        cameraIcon.setPadding(10, 8, 6, 8);
        cameraIcon.setVisibility(View.GONE);

        statusLabel = new TextView(this);
        statusLabel.setText("Open");
        statusLabel.setTextColor(Color.WHITE);
        statusLabel.setTextSize(12f);
        statusLabel.setPadding(2, 0, 12, 0);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setVisibility(View.GONE);

        overlayView.addView(arrowTab);
        overlayView.addView(cameraIcon);
        overlayView.addView(statusLabel);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                PEEK_WIDTH, BUTTON_HEIGHT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = getScreenWidth() - PEEK_WIDTH; // start on right edge
        layoutParams.y = 220;

        overlayView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    downRawX    = event.getRawX();
                    downRawY    = event.getRawY();
                    downLayoutX = layoutParams.x;
                    downLayoutY = layoutParams.y;
                    isDragging  = false;
                    // Pause auto-hide while touching
                    handler.removeCallbacks(autoHideRunnable);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float moveDx = event.getRawX() - downRawX;
                    float moveDy = event.getRawY() - downRawY;
                    if (!isDragging &&
                        (Math.abs(moveDx) > DRAG_THRESHOLD
                         || Math.abs(moveDy) > DRAG_THRESHOLD)) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        layoutParams.x = downLayoutX + (int) moveDx;
                        layoutParams.y = downLayoutY + (int) moveDy;
                        // Clamp x so button never fully hides off screen
                        int maxX = getScreenWidth() - PEEK_WIDTH;
                        if (layoutParams.x < 0) layoutParams.x = 0;
                        if (layoutParams.x > maxX) layoutParams.x = maxX;
                        if (layoutParams.y < 0) layoutParams.y = 0;
                        windowManager.updateViewLayout(overlayView, layoutParams);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        // It was a tap
                        onButtonTapped();
                    } else {
                        // Finished dragging — snap to nearest edge
                        snapToEdge();
                        // Resume auto-hide if expanded
                        if (isExpanded) {
                            handler.postDelayed(autoHideRunnable, AUTO_HIDE_MS);
                        }
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(overlayView, layoutParams);
    }

    // ── Snap to nearest left or right edge ────────────────────────────────

    private void snapToEdge() {
        int screenWidth = getScreenWidth();
        int midPoint    = screenWidth / 2;
        if (layoutParams.x + PEEK_WIDTH / 2 >= midPoint) {
            // Snap to right edge — only peek shows
            layoutParams.x = screenWidth - PEEK_WIDTH;
        } else {
            // Snap to left edge — only peek shows
            layoutParams.x = 0;
            // Mirror the arrow direction
            arrowTab.setText(">");
        }
        windowManager.updateViewLayout(overlayView, layoutParams);
    }

    private int getScreenWidth() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    // ── Tap logic ─────────────────────────────────────────────────────────

    private void onButtonTapped() {
        if (!isExpanded) {
            expand();
        } else {
            togglePhotoBooth();
            handler.removeCallbacks(autoHideRunnable);
            handler.postDelayed(autoHideRunnable, 1200);
        }
    }

    private void expand() {
        isExpanded = true;
        arrowTab.setText(">");
        cameraIcon.setVisibility(View.VISIBLE);
        statusLabel.setVisibility(View.VISIBLE);
        layoutParams.width = EXPANDED_WIDTH;
        windowManager.updateViewLayout(overlayView, layoutParams);
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_MS);
    }

    private void collapse() {
        isExpanded = false;
        arrowTab.setText("<");
        cameraIcon.setVisibility(View.GONE);
        statusLabel.setVisibility(View.GONE);
        layoutParams.width = PEEK_WIDTH;
        windowManager.updateViewLayout(overlayView, layoutParams);
        handler.removeCallbacks(autoHideRunnable);
    }

    // ── Launch / close Photo Booth ─────────────────────────────────────────

    private void togglePhotoBooth() {
        if (!photoBoothOpen) {
            try {
                Intent launch = getPackageManager()
                        .getLaunchIntentForPackage(PHOTO_BOOTH_PKG);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(launch);
                    photoBoothOpen = true;
                    statusLabel.setText("Close");
                    applyPillBackground(0xEE27ae60);
                }
            } catch (Exception ignored) {
            }
        } else {
            // Move Photo Booth to back, restore previous app
            try {
                android.app.ActivityManager am =
                        (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am != null) {
                    for (android.app.ActivityManager.AppTask task : am.getAppTasks()) {
                        android.content.ComponentName cn =
                                task.getTaskInfo().baseActivity;
                        if (cn != null && PHOTO_BOOTH_PKG.equals(cn.getPackageName())) {
                            task.moveToFront();
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                Intent pressBack = new Intent(Intent.ACTION_MAIN);
                                pressBack.addCategory(Intent.CATEGORY_HOME);
                                pressBack.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(pressBack);
                            }, 100);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            photoBoothOpen = false;
            statusLabel.setText("Open");
            applyPillBackground(0xEEe94560);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void applyPillBackground(int color) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadii(new float[]{40, 40, 0, 0, 0, 0, 40, 40});
        pill.setColor(color);
        overlayView.setBackground(pill);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Photo Booth Overlay",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, SetupActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Photo Booth Overlay Active")
                .setContentText("Drag to move | Tap to expand | Tap again to open/close")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
