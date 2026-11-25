package com.robin.cord;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Intentionally empty. Android gives apps that run an enabled accessibility service priority for microphone
 * capture while a call is active; without it other apps' microphone is silenced. We never read the screen.
 * Whenever it connects it also starts GuardService, which keeps the process alive and warns if Android turns it off.
 */
public class RobinA11y extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        GuardService.start(this);
    }
}
