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

    private static final int PEEK_WIDTH     = 14;
    private static final int EXPANDED_WIDTH = 130;
    private static final int BUTTON_HEIGHT  = 52;
    private static final int AUTO_HIDE_MS   = 4000;

    private WindowManager windowManager;
    private LinearLayout  overlayView;
    private WindowManager.LayoutParams layoutParams;

    private boolean isExpanded     = false;
    private boolean photoBoothOpen = false;

    private TextView  arrowTab;
    private ImageView cameraIcon;
    private TextView  statusLabel;

    private final Handler  handler          = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::collapse;

    private float downRawX, downRawY;

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
        arrowTab.setTextSize(12f);
        arrowTab.setPadding(3, 0, 3, 0);
        arrowTab.setGravity(Gravity.CENTER);

        cameraIcon = new ImageView(this);
        cameraIcon.setImageResource(android.R.drawable.ic_menu_camera);
        cameraIcon.setColorFilter(Color.WHITE);
        cameraIcon.setPadding(10, 8, 6, 8);
        cameraIcon.setVisibility(View.GONE);

        statusLabel = new TextView(this);
        statusLabel.setText("Open");
        statusLabel.setTextColor(Color.WHITE);
        statusLabel.setTextSize(11f);
        statusLabel.setPadding(2, 0, 10, 0);
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
        layoutParams.gravity = Gravity.TOP | Gravity.END;
        layoutParams.x = 0;
        layoutParams.y = 220;

        overlayView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = Math.abs(event.getRawX() - downRawX);
                    float dy = Math.abs(event.getRawY() - downRawY);
                    if (dx < 20 && dy < 20) {
                        onButtonTapped();
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(overlayView, layoutParams);
    }

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
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            photoBoothOpen = false;
            statusLabel.setText("Open");
            applyPillBackground(0xEEe94560);
        }
    }

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
                .setContentText("Tap the sliver on the right edge to expand")
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
