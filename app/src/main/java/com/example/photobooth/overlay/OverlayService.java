package com.example.photobooth.overlay;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
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

import java.util.List;

public class OverlayService extends Service {

    private static final String CHANNEL_ID      = "overlay_channel";
    private static final int    NOTIFICATION_ID  = 42;
    private static final String PHOTO_BOOTH_PKG  = "com.example.photobooth";

    private WindowManager windowManager;
    private ImageView overlayView;
    private WindowManager.LayoutParams layoutParams;

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
        if (isPhotoBoothInForeground()) {
            // Send to background
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } else {
            // Launch Photo Booth
            Intent launch = getPackageManager().getLaunchIntentForPackage(PHOTO_BOOTH_PKG);
            if (launch != null) {
                launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                );
                startActivity(launch);
            } else {
                Toast.makeText(this,
                    "Photo Booth app not found! Is com.example.photobooth installed?",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isPhotoBoothInForeground() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return false;
            return PHOTO_BOOTH_PKG.equals(tasks.get(0).topActivity.getPackageName());
        } catch (Exception e) {
            return false;
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
                .setContentText("Tap 📷 button to open/close Photo Booth")
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
