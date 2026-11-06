package dev.robin.v1;

public enum Category {
    COMBAT("Combat"), MOVEMENT("Movement"), VISUALS("Visuals"), PLAYER("Player"), SETTINGS("Settings");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
