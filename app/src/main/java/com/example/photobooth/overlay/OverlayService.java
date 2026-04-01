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

    private static final int PEEK_SIZE      = 52;  // size when collapsed
    private static final int EXPANDED_LONG  = 170; // expanded length
    private static final int BUTTON_THICK   = 72;  // thickness of button
    private static final int AUTO_HIDE_MS   = 4000;
    private static final int DRAG_THRESHOLD = 12;

    // Colors — blue theme
    private static final int COLOR_OPEN  = 0xEE1A6FC6; // blue = tap to open
    private static final int COLOR_CLOSE = 0xEE27ae60; // green = tap to close

    // Which edge the button is snapped to
    private enum Edge { LEFT, RIGHT, TOP, BOTTOM }
    private Edge currentEdge = Edge.RIGHT;

    private WindowManager windowManager;
    private LinearLayout  overlayView;
    private WindowManager.LayoutParams layoutParams;

    private boolean isExpanded     = false;
    private boolean photoBoothOpen = false;
    private boolean isDragging     = false;

    private ImageView peekIcon;
    private ImageView cameraIcon;
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
        overlayView.setGravity(Gravity.CENTER);
        applyBackground(COLOR_OPEN, currentEdge);

        // Peek icon — camera shown when collapsed
        peekIcon = new ImageView(this);
        peekIcon.setImageResource(android.R.drawable.ic_menu_camera);
        peekIcon.setColorFilter(Color.WHITE);
        peekIcon.setPadding(10, 10, 10, 10);
        peekIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // Expanded camera icon
        cameraIcon = new ImageView(this);
        cameraIcon.setImageResource(android.R.drawable.ic_menu_camera);
        cameraIcon.setColorFilter(Color.WHITE);
        cameraIcon.setPadding(6, 8, 4, 8);
        cameraIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cameraIcon.setVisibility(View.GONE);

        // Status label
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
                PEEK_SIZE, PEEK_SIZE,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = getScreenWidth() - PEEK_SIZE;
        layoutParams.y = getScreenHeight() / 2;

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
                        // Collapse while dragging for clean movement
                        if (isExpanded) collapse();
                    }
                    if (isDragging) {
                        layoutParams.x = downLayoutX + (int) moveDx;
                        layoutParams.y = downLayoutY + (int) moveDy;
                        // Keep at least partially visible
                        layoutParams.x = Math.max(-PEEK_SIZE / 2,
                                Math.min(layoutParams.x, getScreenWidth() - PEEK_SIZE / 2));
                        layoutParams.y = Math.max(-PEEK_SIZE / 2,
                                Math.min(layoutParams.y, getScreenHeight() - PEEK_SIZE / 2));
                        windowManager.updateViewLayout(overlayView, layoutParams);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        onButtonTapped();
                    } else {
                        snapToNearestEdge();
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

    // ── Snap to whichever of the 4 edges is closest ────────────────────────

    private void snapToNearestEdge() {
        int sw = getScreenWidth();
        int sh = getScreenHeight();
        int cx = layoutParams.x + PEEK_SIZE / 2;
        int cy = layoutParams.y + PEEK_SIZE / 2;

        int distLeft   = cx;
        int distRight  = sw - cx;
        int distTop    = cy;
        int distBottom = sh - cy;

        int minDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));

        if (minDist == distRight) {
            currentEdge = Edge.RIGHT;
            layoutParams.x = sw - PEEK_SIZE;
        } else if (minDist == distLeft) {
            currentEdge = Edge.LEFT;
            layoutParams.x = 0;
        } else if (minDist == distTop) {
            currentEdge = Edge.TOP;
            layoutParams.y = 0;
        } else {
            currentEdge = Edge.BOTTOM;
            layoutParams.y = sh - PEEK_SIZE;
        }

        // Set collapsed size
        setCollapsedSize();
        applyBackground(photoBoothOpen ? COLOR_CLOSE : COLOR_OPEN, currentEdge);
        windowManager.updateViewLayout(overlayView, layoutParams);
    }

    private void setCollapsedSize() {
        if (currentEdge == Edge.TOP || currentEdge == Edge.BOTTOM) {
            // Horizontal strip on top/bottom edge
            layoutParams.width  = BUTTON_THICK;
            layoutParams.height = PEEK_SIZE;
            overlayView.setOrientation(LinearLayout.HORIZONTAL);
        } else {
            // Vertical strip on left/right edge
            layoutParams.width  = PEEK_SIZE;
            layoutParams.height = BUTTON_THICK;
            overlayView.setOrientation(LinearLayout.HORIZONTAL);
        }
    }

    private void setExpandedSize() {
        if (currentEdge == Edge.TOP || currentEdge == Edge.BOTTOM) {
            layoutParams.width  = EXPANDED_LONG;
            layoutParams.height = BUTTON_THICK;
        } else {
            layoutParams.width  = EXPANDED_LONG;
            layoutParams.height = BUTTON_THICK;
        }
        overlayView.setOrientation(LinearLayout.HORIZONTAL);
    }

    private int getScreenWidth() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    private int getScreenHeight() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.heightPixels;
    }

    // ── Tap logic ──────────────────────────────────────────────────────────

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
        peekIcon.setVisibility(View.GONE);
        cameraIcon.setVisibility(View.VISIBLE);
        statusLabel.setVisibility(View.VISIBLE);
        setExpandedSize();
        windowManager.updateViewLayout(overlayView, layoutParams);
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_MS);
    }

    private void collapse() {
        isExpanded = false;
        peekIcon.setVisibility(View.VISIBLE);
        cameraIcon.setVisibility(View.GONE);
        statusLabel.setVisibility(View.GONE);
        setCollapsedSize();
        applyBackground(photoBoothOpen ? COLOR_CLOSE : COLOR_OPEN, currentEdge);
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
                    applyBackground(COLOR_CLOSE, currentEdge);
                }
            } catch (Exception ignored) {
            }
        } else {
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
            applyBackground(COLOR_OPEN, currentEdge);
        }
    }

    // ── Background with rounded corners on the exposed side only ──────────

    private void applyBackground(int color, Edge edge) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        float r = 40f;
        // Round the corners on the side facing away from the edge
        switch (edge) {
            case RIGHT:  // button on right → round left corners
                pill.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
                break;
            case LEFT:   // button on left → round right corners
                pill.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
                break;
            case TOP:    // button on top → round bottom corners
                pill.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
                break;
            case BOTTOM: // button on bottom → round top corners
                pill.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                break;
        }
        pill.setColor(color);
        overlayView.setBackground(pill);
    }

    // ── Notification ───────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Photo Booth Overlay",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, SetupActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Photo Booth Overlay Active")
                .setContentText("Drag to any edge | Tap to expand | Tap to open/close")
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
    public IBinder onBind(Intent intent) { return null; }
}
