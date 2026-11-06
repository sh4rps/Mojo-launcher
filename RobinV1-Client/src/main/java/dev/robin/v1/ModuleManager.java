package dev.robin.v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.robin.v1.module.AimAssist;
import dev.robin.v1.module.AutoClicker;
import dev.robin.v1.module.AutoFireball;
import dev.robin.v1.module.Chams;
import dev.robin.v1.module.FastPlace;
import dev.robin.v1.module.FullBright;
import dev.robin.v1.module.GuiModule;
import dev.robin.v1.module.HudModule;
import dev.robin.v1.module.NoHurtCam;
import dev.robin.v1.module.SafeWalk;

public final class ModuleManager {
    private static final List<Module> modules = new ArrayList<Module>();

    private ModuleManager() {
    }

    public static void init() {
        register(new AimAssist());
        register(new AutoClicker());
        register(new AutoFireball());
        register(new SafeWalk());
        register(new FastPlace());
        register(new Chams());
        register(new FullBright());
        register(new NoHurtCam());
        register(new GuiModule());
        register(new HudModule());
    }

    private static void register(Module m) {
        modules.add(m);
    }

    public static List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> c) {
        for (Module m : modules) {
            if (m.getClass() == c) {
                return (T) m;
            }
        }
        throw new IllegalStateException(c.getName());
    }

    public static List<Module> in(Category c) {
        List<Module> out = new ArrayList<Module>();
        for (Module m : modules) {
            if (m.category == c) {
                out.add(m);
            }
        }
        return out;
    }
}
