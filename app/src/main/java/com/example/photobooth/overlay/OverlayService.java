package com.example.photobooth.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String CHANNEL_ID      = "overlay_channel";
    private static final int    NOTIFICATION_ID  = 42;
    private static final String PHOTO_BOOTH_PKG  = "com.example.photobooth";

    private WindowManager windowManager;
    private ImageView overlayView;
    private WindowManager.LayoutParams layoutParams;

    // Track whether WE launched Photo Booth
    private boolean photoBoothOpen = false;

    private int   initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean dragged;
    private static final int DRAG_THRESHOLD = 8;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        addOverlayButton();
    }

    private void addOverlayButton() {
        overlayView = new ImageView(this);
        overlayView.setImageResource(android.R.drawable.ic_menu_camera);
        overlayView.setBackgroundColor(0xCCe94560);
        overlayView.setPadding(24, 24, 24, 24);
        overlayView.setColorFilter(0xFFFFFFFF);
        overlayView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                150, 150,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 20;
        layoutParams.y = 100;

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX      = layoutParams.x;
                        initialY      = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        dragged       = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(event.getRawX() - initialTouchX);
                        int dy = (int)(event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                            dragged = true;
                        }
                        if (dragged) {
                            layoutParams.x = initialX + dx;
                            layoutParams.y = initialY + dy;
                            windowManager.updateViewLayout(overlayView, layoutParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!dragged) {
                            togglePhotoBooth();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, layoutParams);
    }

    private void togglePhotoBooth() {
        if (!photoBoothOpen) {
            // Launch Photo Booth
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(PHOTO_BOOTH_PKG);
                if (launch != null) {
                    launch.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );
                    startActivity(launch);
                    photoBoothOpen = true;
                    // Change button color to green = "tap to close"
                    overlayView.setBackgroundColor(0xCC2ecc71);
                } else {
                    Toast.makeText(this, "Photo Booth not found!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            // Close Photo Booth — go home
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            photoBoothOpen = false;
            // Change button color back to red = "tap to open"
            overlayView.setBackgroundColor(0xCCe94560);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Photo Booth Overlay", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, SetupActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Photo Booth Overlay Active")
                .setContentText("Red = open  |  Green = close")
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
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
