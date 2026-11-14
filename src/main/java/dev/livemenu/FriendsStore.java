package dev.livemenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's own friends list, saved to config/livemenu-friends.txt.
 * Everything in the UI goes through this class, so another source (e.g. Mojang's native friends list
 * or an Essential adapter) can be plugged in here later.
 */
public final class FriendsStore {
    private FriendsStore() {}

    public static final class Friend {
        public final String name;
        public boolean fav;
        Friend(String name, boolean fav) { this.name = name; this.fav = fav; }
    }

    private static final List<Friend> LIST = new ArrayList<>();
    private static boolean loaded = false;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("livemenu-friends.txt"); }

    public static synchronized List<Friend> list() {
        if (!loaded) load();
        return LIST;
    }

    private static void load() {
        loaded = true;
        LIST.clear();
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            for (String line : Files.readAllLines(f)) {
                String[] p = line.split("\\|");
                if (p.length >= 1 && !p[0].isBlank()) LIST.add(new Friend(p[0], p.length > 1 && p[1].equals("1")));
            }
        } catch (IOException ignored) {}
    }

    public static synchronized void save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Friend f : LIST) sb.append(f.name).append('|').append(f.fav ? 1 : 0).append('\n');
            Files.writeString(file(), sb.toString());
        } catch (IOException ignored) {}
    }

    /** @return null on success, otherwise a short error message. */
    public static synchronized String add(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (!n.matches("[A-Za-z0-9_]{1,16}")) return "Names are 1-16 letters, numbers or _";
        for (Friend f : list()) if (f.name.equalsIgnoreCase(n)) return n + " is already in your list";
        LIST.add(new Friend(n, false));
        save();
        return null;
    }

    public static synchronized void remove(String name) {
        list().removeIf(f -> f.name.equalsIgnoreCase(name));
        save();
    }

    public static synchronized void toggleFavorite(String name) {
        for (Friend f : list()) if (f.name.equalsIgnoreCase(name)) f.fav = !f.fav;
        save();
    }
}
