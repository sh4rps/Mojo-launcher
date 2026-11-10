package com.robin.cord;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Intentionally empty. Android gives apps that run an enabled accessibility service priority for microphone
 * capture while a call is active; without it other apps' microphone is silenced. We never read the screen.
 */
public class RobinA11y extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
}
