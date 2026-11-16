package com.rclient.core;

public enum Category {
    PVP("PvP"), HUD("HUD"), VISUAL("Visual"), MOVEMENT("Movement"), WORLD("World"), UTILITY("Utility"), PERFORMANCE("Performance");
    public final String label;
    Category(String l) { label = l; }
}
