package dev.robin.v1;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

/** Saves module states and settings to config/robinv1.json. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {
    }

    private static File file() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        dir.mkdirs();
        return new File(dir, "robinv1.json");
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            for (Setting s : m.settings) {
                switch (s.type) {
                    case BOOL:
                        o.addProperty(s.name, s.bool);
                        break;
                    case MODE:
                        o.addProperty(s.name, s.index);
                        break;
                    default:
                        o.addProperty(s.name, s.value);
                }
            }
            root.add(m.name, o);
        }
        try (Writer w = new FileWriter(file())) {
            GSON.toJson(root, w);
        } catch (Exception ex) {
            System.err.println("[RobinV1] could not save config: " + ex);
        }
    }

    public static void load() {
        File f = file();
        if (!f.exists()) {
            return;
        }
        try (Reader r = new FileReader(f)) {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            for (Module m : ModuleManager.all()) {
                if (!root.has(m.name)) {
                    continue;
                }
                JsonObject o = root.getAsJsonObject(m.name);
                for (Setting s : m.settings) {
                    if (!o.has(s.name)) {
                        continue;
                    }
                    switch (s.type) {
                        case BOOL:
                            s.bool = o.get(s.name).getAsBoolean();
                            break;
                        case MODE:
                            s.index = Math.max(0, Math.min(s.modes.length - 1, o.get(s.name).getAsInt()));
                            break;
                        default:
                            s.setNum(o.get(s.name).getAsDouble());
                    }
                }
                if (o.has("enabled")) {
                    m.setEnabled(o.get("enabled").getAsBoolean());
                }
            }
        } catch (Exception ex) {
            System.err.println("[RobinV1] could not load config: " + ex);
        }
    }
}
