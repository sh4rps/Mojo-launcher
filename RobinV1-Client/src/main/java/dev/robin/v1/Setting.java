package dev.robin.v1;

/** A single configurable value of a module: toggle, number slider or a list of modes. */
public class Setting {
    public enum Type { BOOL, NUM, MODE }

    public final String name;
    public final Type type;
    public boolean bool;
    public double value, min, max, step;
    public String[] modes;
    public int index;

    private Setting(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public static Setting bool(String name, boolean def) {
        Setting s = new Setting(name, Type.BOOL);
        s.bool = def;
        return s;
    }

    public static Setting num(String name, double def, double min, double max, double step) {
        Setting s = new Setting(name, Type.NUM);
        s.value = def;
        s.min = min;
        s.max = max;
        s.step = step;
        return s;
    }

    public static Setting mode(String name, int def, String... modes) {
        Setting s = new Setting(name, Type.MODE);
        s.modes = modes;
        s.index = def;
        return s;
    }

    public boolean on() {
        return bool;
    }

    public double num() {
        return value;
    }

    public int idx() {
        return index;
    }

    public void setNum(double v) {
        if (step > 0) {
            v = Math.round((v - min) / step) * step + min;
        }
        value = Math.max(min, Math.min(max, v));
    }

    public void cycle() {
        index = (index + 1) % modes.length;
    }

    public String display() {
        switch (type) {
            case BOOL:
                return bool ? "On" : "Off";
            case MODE:
                return modes[index];
            default:
                return step >= 1 ? String.valueOf((int) Math.round(value)) : String.format("%.1f", value);
        }
    }
}
