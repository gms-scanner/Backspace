package dev.goldforge.addon;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/** Loads addon JARs before Backspace restores module settings. */
public final class AddonManager {
    private static final Logger LOGGER = LogManager.getLogger("Backspace/Addons");
    private static final List<Loaded> LOADED = new ArrayList<Loaded>();
    private static boolean initialized;
    private AddonManager() { }

    public static synchronized void load() {
        if (initialized) return;
        initialized = true;
        Path folder = Minecraft.getMinecraft().mcDataDir.toPath().resolve("Backspace").resolve("addons");
        try { Files.createDirectories(folder); }
        catch (IOException failure) { LOGGER.error("Cannot create addon folder {}", folder, failure); return; }
        List<Path> jars = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.jar")) {
            for (Path path : stream) if (Files.isRegularFile(path)) jars.add(path);
        } catch (IOException failure) { LOGGER.error("Cannot list addons", failure); return; }
        Collections.sort(jars, Comparator.comparing(path -> path.getFileName().toString()));
        Set<String> ids = new HashSet<String>();
        for (Path jar : jars) {
            URLClassLoader loader = null;
            int loadedFromJar = 0;
            try {
                loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, AddonManager.class.getClassLoader());
                boolean found = false;
                for (BackspaceAddon addon : ServiceLoader.load(BackspaceAddon.class, loader)) {
                    found = true;
                    String id = addon.id();
                    if (id == null || !id.matches("[a-z][a-z0-9_]{0,15}") || !ids.add(id))
                        throw new IllegalArgumentException("Invalid or duplicate addon id: " + id);
                    AddonContext context = new AddonContext(id);
                    try { addon.onLoad(context); }
                    catch (Throwable failure) { context.rollback(); throw new IllegalStateException("Addon setup failed: " + id, failure); }
                    LOADED.add(new Loaded(addon, context, loader));
                    loadedFromJar++;
                    LOGGER.info("Loaded addon {} ({}) from {}", addon.name(), id, jar.getFileName());
                }
                if (!found) LOGGER.warn("No BackspaceAddon service entry in {}", jar.getFileName());
            } catch (Throwable failure) {
                LOGGER.error("Failed to load addon {}", jar.getFileName(), failure);
            } finally {
                if (loadedFromJar == 0 && loader != null)
                    try { loader.close(); } catch (IOException ignored) { }
            }
        }
    }
    public static synchronized void shutdown() {
        for (int i = LOADED.size() - 1; i >= 0; i--) {
            Loaded loaded = LOADED.get(i);
            try { loaded.addon.onUnload(); }
            catch (Throwable failure) { LOGGER.warn("Addon unload failed: {}", loaded.addon.id(), failure); }
            loaded.context.unload();
            try { loaded.loader.close(); }
            catch (IOException failure) { LOGGER.warn("Addon JAR close failed", failure); }
        }
        LOADED.clear();
    }
    private static final class Loaded {
        final BackspaceAddon addon; final AddonContext context; final URLClassLoader loader;
        Loaded(BackspaceAddon addon, AddonContext context, URLClassLoader loader) {
            this.addon = addon; this.context = context; this.loader = loader;
        }
    }
}
