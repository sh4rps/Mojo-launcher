package com.rclient.core;

public final class Setting {
    public enum Type { BOOL, NUM, ENUM, COLOR, KEY, STRING }

    public final String id, label;
    public final Type type;
    public Object value;
    public final Object def;
    public double min, max, step = 1;
    public String[] options = new String[0];
    public int maxLen = 40;

    private Setting(String id, String label, Type type, Object def) {
        this.id = id; this.label = label; this.type = type; this.def = def; this.value = def;
    }
    public static Setting bool(String id, String label, boolean d) { return new Setting(id, label, Type.BOOL, d); }
    public static Setting num(String id, String label, double d, double min, double max, double step) {
        Setting s = new Setting(id, label, Type.NUM, d); s.min = min; s.max = max; s.step = step; return s;
    }
    public static Setting choice(String id, String label, String d, String... opts) {
        Setting s = new Setting(id, label, Type.ENUM, d); s.options = opts; return s;
    }
    public static Setting color(String id, String label, int argb) { return new Setting(id, label, Type.COLOR, argb); }
    public static Setting key(String id, String label, int glfw) { return new Setting(id, label, Type.KEY, glfw); }
    public static Setting string(String id, String label, String d) { return new Setting(id, label, Type.STRING, d); }

    public boolean bool() { return (Boolean) value; }
    public double num() { return ((Number) value).doubleValue(); }
    public int integer() { return (int) Math.round(num()); }
    public float flt() { return (float) num(); }
    public int color() { return ((Number) value).intValue(); }
    public int key() { return ((Number) value).intValue(); }
    public String str() { return String.valueOf(value); }

    public void setNum(double v) {
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) c = Math.round((c - min) / step) * step + min;
        c = Math.max(min, Math.min(max, c));
        value = Math.round(c * 10000.0) / 10000.0;
        Config.markDirty();
    }
    public void set(Object v) { value = v; Config.markDirty(); }
    public void cycle(int dir) {
        if (type != Type.ENUM || options.length == 0) return;
        int i = 0;
        for (int k = 0; k < options.length; k++) if (options[k].equals(str())) i = k;
        i = (i + dir + options.length) % options.length;
        set(options[i]);
    }
    public void reset() { value = def; Config.markDirty(); }

    /** Load a value read from JSON, coercing to this setting's type; bad values are ignored. */
    public void load(Object o) {
        if (o == null) return;
        try {
            switch (type) {
                case BOOL -> value = (o instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(o));
                case NUM -> { double d = ((Number) o).doubleValue(); value = Math.max(min, Math.min(max, d)); }
                case COLOR -> value = ((Number) o).intValue();
                case KEY -> value = ((Number) o).intValue();
                case ENUM -> { String s = String.valueOf(o); for (String p : options) if (p.equals(s)) value = s; }
                case STRING -> { String s = String.valueOf(o); value = s.length() > maxLen ? s.substring(0, maxLen) : s; }
            }
        } catch (Exception ignored) { }
    }
}
