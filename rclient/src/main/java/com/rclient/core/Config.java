package com.rclient.core;

import com.google.gson.*;
import com.rclient.RClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** JSON persistence: global.json (active profile, favorites, recents) + one file per profile. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("rclient");
    private static final Path PROFILES = DIR.resolve("profiles");
    private static final Path GLOBAL = DIR.resolve("global.json");

    public static String activeProfile = "PvP";
    public static final LinkedHashSet<String> favorites = new LinkedHashSet<>();
    public static final LinkedList<String> recents = new LinkedList<>();
    public static String lastServer = "";
    public static boolean skipLoading = false;

    private static volatile boolean dirty;
    private static boolean loading;

    private Config() { }

    public static void markDirty() { if (!loading) dirty = true; }

    public static void flushIfDirty() { if (dirty) { dirty = false; saveAll(); } }

    public static void init() {
        try { Files.createDirectories(PROFILES); } catch (IOException e) { RClient.LOG.error("config dir", e); }
        loadGlobal();
        boolean first = !Files.exists(PROFILES.resolve(safe("PvP") + ".json"));
        if (first) Presets.createDefaults();
        loadProfile(activeProfile);
    }

    public static List<String> profiles() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(PROFILES)) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String n = p.getFileName().toString();
                out.add(n.substring(0, n.length() - 5));
            });
        } catch (IOException ignored) { }
        Collections.sort(out);
        return out;
    }

    public static String safe(String n) { return n.replaceAll("[^A-Za-z0-9 _-]", "_").trim(); }

    private static void loadGlobal() {
        try {
            if (!Files.exists(GLOBAL)) return;
            JsonObject o = JsonParser.parseString(Files.readString(GLOBAL)).getAsJsonObject();
            if (o.has("profile")) activeProfile = o.get("profile").getAsString();
            if (o.has("lastServer")) lastServer = o.get("lastServer").getAsString();
            if (o.has("skipLoading")) skipLoading = o.get("skipLoading").getAsBoolean();
            favorites.clear(); recents.clear();
            if (o.has("favorites")) for (JsonElement e : o.getAsJsonArray("favorites")) favorites.add(e.getAsString());
            if (o.has("recents")) for (JsonElement e : o.getAsJsonArray("recents")) recents.add(e.getAsString());
        } catch (Exception e) { RClient.LOG.warn("global config unreadable, using defaults", e); }
    }

    private static void saveGlobal() {
        JsonObject o = new JsonObject();
        o.addProperty("profile", activeProfile);
        o.addProperty("lastServer", lastServer);
        o.addProperty("skipLoading", skipLoading);
        JsonArray f = new JsonArray(); favorites.forEach(f::add); o.add("favorites", f);
        JsonArray r = new JsonArray(); recents.forEach(r::add); o.add("recents", r);
        write(GLOBAL, o);
    }

    public static void saveAll() { saveGlobal(); saveProfile(activeProfile); }

    public static JsonObject serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonObject mods = new JsonObject();
        for (Mod m : ModuleManager.all()) {
            JsonObject mo = new JsonObject();
            mo.addProperty("enabled", m.isEnabled());
            mo.addProperty("key", m.toggleKey);
            if (m instanceof HudModule h) { mo.addProperty("x", h.fx); mo.addProperty("y", h.fy); }
            JsonObject so = new JsonObject();
            for (Setting s : m.settings) {
                switch (s.type) {
                    case BOOL -> so.addProperty(s.id, s.bool());
                    case NUM -> so.addProperty(s.id, s.num());
                    case COLOR -> so.addProperty(s.id, s.color());
                    case KEY -> so.addProperty(s.id, s.key());
                    default -> so.addProperty(s.id, s.str());
                }
            }
            mo.add("settings", so);
            mods.add(m.id, mo);
        }
        root.add("modules", mods);
        return root;
    }

    public static void apply(JsonObject root) {
        loading = true;
        try {
            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Mod m : ModuleManager.all()) {
                JsonObject mo = mods.has(m.id) ? mods.getAsJsonObject(m.id) : null;
                for (Setting st : m.settings) st.value = st.def;
                m.toggleKey = -1;
                if (m instanceof HudModule hh) { hh.fx = hh.dfx; hh.fy = hh.dfy; }
                if (mo == null) { m.loadEnabled(false); continue; }
                if (mo.has("key")) m.toggleKey = mo.get("key").getAsInt();
                if (m instanceof HudModule h) {
                    if (mo.has("x")) h.fx = clamp(mo.get("x").getAsDouble());
                    if (mo.has("y")) h.fy = clamp(mo.get("y").getAsDouble());
                }
                if (mo.has("settings")) {
                    JsonObject so = mo.getAsJsonObject("settings");
                    for (Setting s : m.settings) {
                        if (!so.has(s.id)) continue;
                        JsonElement e = so.get(s.id);
                        if (!e.isJsonPrimitive()) continue;
                        JsonPrimitive p = e.getAsJsonPrimitive();
                        s.load(p.isBoolean() ? (Object) p.getAsBoolean() : p.isNumber() ? (Object) p.getAsDouble() : p.getAsString());
                    }
                }
                m.loadEnabled(mo.has("enabled") && mo.get("enabled").getAsBoolean());
            }
        } finally { loading = false; }
    }

    private static double clamp(double d) { return Math.max(0, Math.min(0.98, d)); }

    public static void saveProfile(String name) { write(PROFILES.resolve(safe(name) + ".json"), serialize()); }

    public static boolean loadProfile(String name) {
        Path p = PROFILES.resolve(safe(name) + ".json");
        try {
            if (!Files.exists(p)) return false;
            apply(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            activeProfile = name;
            return true;
        } catch (Exception e) {
            RClient.LOG.warn("profile {} unreadable", name, e);
            return false;
        }
    }

    public static void switchProfile(String name) {
        saveProfile(activeProfile);
        if (loadProfile(name)) saveGlobal();
    }

    public static boolean createProfile(String name, boolean copyCurrent) {
        String n = safe(name);
        if (n.isEmpty() || Files.exists(PROFILES.resolve(n + ".json"))) return false;
        if (copyCurrent) write(PROFILES.resolve(n + ".json"), serialize());
        else {
            JsonObject empty = new JsonObject();
            write(PROFILES.resolve(n + ".json"), empty);
        }
        return true;
    }

    public static boolean deleteProfile(String name) {
        if (profiles().size() <= 1 || name.equals(activeProfile)) return false;
        try { return Files.deleteIfExists(PROFILES.resolve(safe(name) + ".json")); } catch (IOException e) { return false; }
    }

    public static String export() { return GSON.toJson(serialize()); }

    public static boolean importJson(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (!o.has("modules")) return false;
            apply(o);
            markDirty();
            return true;
        } catch (Exception e) { return false; }
    }

    public static void touchRecent(String id) {
        recents.remove(id); recents.addFirst(id);
        while (recents.size() > 8) recents.removeLast();
        markDirty();
    }

    private static void write(Path p, JsonObject o) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { RClient.LOG.error("write {}", p, e); }
    }
}
