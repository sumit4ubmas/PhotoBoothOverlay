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

    private static final int PEEK_WIDTH     = 52;   // wide enough for camera icon
    private static final int EXPANDED_WIDTH = 170;
    private static final int BUTTON_HEIGHT  = 72;
    private static final int AUTO_HIDE_MS   = 4000;
    private static final int DRAG_THRESHOLD = 12;

    private WindowManager windowManager;
    private LinearLayout  overlayView;
    private WindowManager.LayoutParams layoutParams;

    private boolean isExpanded     = false;
    private boolean photoBoothOpen = false;
    private boolean isDragging     = false;

    private ImageView peekIcon;    // camera icon shown when collapsed
    private ImageView cameraIcon;  // camera icon shown when expanded
    private TextView  statusLabel;

    private final Handler  handler          = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::collapse;

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

        // ── Peek icon — camera icon visible when collapsed ─────────────────
        peekIcon = new ImageView(this);
        peekIcon.setImageResource(android.R.drawable.ic_menu_camera);
        peekIcon.setColorFilter(Color.WHITE);
        peekIcon.setPadding(10, 10, 10, 10);
        peekIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // ── Expanded camera icon ───────────────────────────────────────────
        cameraIcon = new ImageView(this);
        cameraIcon.setImageResource(android.R.drawable.ic_menu_camera);
        cameraIcon.setColorFilter(Color.WHITE);
        cameraIcon.setPadding(6, 8, 4, 8);
        cameraIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cameraIcon.setVisibility(View.GONE);

        // ── Status label ───────────────────────────────────────────────────
        statusLabel = new TextView(this);
        statusLabel.setText("Open");
        statusLabel.setTextColor(Color.WHITE);
        statusLabel.setTextSize(13f);
        statusLabel.setPadding(2, 0, 12, 0);
        statusLabel.setGravity(Gravity.CENTER_VERTICAL);
        statusLabel.setMaxLines(1);
        statusLabel.setSingleLine(true);
        statusLabel.setVisibility(View.GONE);

        overlayView.addView(peekIcon);
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
        layoutParams.x = getScreenWidth() - PEEK_WIDTH;
        layoutParams.y = 220;

        overlayView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX    = event.getRawX();
                    downRawY    = event.getRawY();
                    downLayoutX = layoutParams.x;
                    downLayoutY = layoutParams.y;
                    isDragging  = false;
                    handler.removeCallbacks(autoHideRunnable);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float moveDx = event.getRawX() - downRawX;
                    float moveDy = event.getRawY() - downRawY;
                    if (!isDragging && (Math.abs(moveDx) > DRAG_THRESHOLD
                            || Math.abs(moveDy) > DRAG_THRESHOLD)) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        layoutParams.x = downLayoutX + (int) moveDx;
                        layoutParams.y = downLayoutY + (int) moveDy;
                        if (layoutParams.x < 0) layoutParams.x = 0;
                        if (layoutParams.x > getScreenWidth() - PEEK_WIDTH)
                            layoutParams.x = getScreenWidth() - PEEK_WIDTH;
                        if (layoutParams.y < 0) layoutParams.y = 0;
                        windowManager.updateViewLayout(overlayView, layoutParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        onButtonTapped();
                    } else {
                        snapToEdge();
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

    private void snapToEdge() {
        int screenWidth = getScreenWidth();
        if (layoutParams.x + EXPANDED_WIDTH / 2 >= screenWidth / 2) {
            layoutParams.x = screenWidth - PEEK_WIDTH;
        } else {
            layoutParams.x = 0;
        }
        windowManager.updateViewLayout(overlayView, layoutParams);
    }

    private int getScreenWidth() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.widthPixels;
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
        // Hide peek icon, show expanded icons
        peekIcon.setVisibility(View.GONE);
        cameraIcon.setVisibility(View.VISIBLE);
        statusLabel.setVisibility(View.VISIBLE);
        layoutParams.width = EXPANDED_WIDTH;
        windowManager.updateViewLayout(overlayView, layoutParams);
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_MS);
    }

    private void collapse() {
        isExpanded = false;
        // Show peek icon, hide expanded icons
        peekIcon.setVisibility(View.VISIBLE);
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
            // Use Accessibility Service to press Back
            OverlayAccessibilityService svc =
                    OverlayAccessibilityService.getInstance();
            if (svc != null) {
                svc.pressBack();
            } else {
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
            }
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
                .setContentText("Tap the camera icon on the edge to expand")
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
