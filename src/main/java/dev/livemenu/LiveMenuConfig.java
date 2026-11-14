package dev.livemenu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

public final class LiveMenuConfig {
    private LiveMenuConfig() {}

    public static boolean showHint = true;
    public static boolean dayNightCycle = true;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("livemenu.properties"); }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (InputStream in = Files.newInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            showHint = Boolean.parseBoolean(p.getProperty("showHint", "true"));
            dayNightCycle = Boolean.parseBoolean(p.getProperty("dayNightCycle", "true"));
            PartyManager.I.autoJoin = Boolean.parseBoolean(p.getProperty("partyAutoJoin", "true"));
        } catch (IOException ignored) {}
    }

    public static void save() {
        try (OutputStream out = Files.newOutputStream(file())) {
            Properties p = new Properties();
            p.setProperty("showHint", String.valueOf(showHint));
            p.setProperty("dayNightCycle", String.valueOf(dayNightCycle));
            p.setProperty("partyAutoJoin", String.valueOf(PartyManager.I.autoJoin));
            p.store(out, "Live Main Menu");
        } catch (IOException ignored) {}
    }

    /** Essential has no public friends-list API that this mod could verify, so this only detects it. */
    public static boolean essentialDetected() {
        FabricLoader l = FabricLoader.getInstance();
        return l.isModLoaded("essential") || l.isModLoaded("essential-loader");
    }
}
