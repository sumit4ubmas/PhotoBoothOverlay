package com.example.photobooth.overlay;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class OverlayAccessibilityService extends AccessibilityService {

    private static OverlayAccessibilityService instance;

    public static OverlayAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used
    }

    @Override
    public void onInterrupt() {
        // Not used
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public void pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void openRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    }
}
