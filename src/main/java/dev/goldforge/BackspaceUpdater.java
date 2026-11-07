package dev.goldforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Locale;

/** Checks official releases and stages a replacement after Minecraft exits. */
public final class BackspaceUpdater {
    private static final String API = "https://api.github.com/repos/gms-scanner/Backspace/releases?per_page=30";
    private static volatile boolean busy;
    public enum Phase { IDLE, CHECKING, CURRENT, DOWNLOADING, READY, FAILED }
    public static final class Status {
        public final Phase phase;
        public final String detail, version;
        public final float progress;
        private Status(Phase phase,String detail,String version,float progress) {
            this.phase=phase;this.detail=detail;this.version=version;this.progress=progress;
        }
    }
    private static volatile Status status=new Status(Phase.IDLE,"Check for updates",GoldForge.VERSION,0F);
    public static Status status(){return status;}
    private static void set(Phase phase,String detail,String version,float progress) {
        status=new Status(phase,detail,version,Math.max(0F,Math.min(1F,progress)));
    }
    private BackspaceUpdater() {}

    public static void check(final boolean install) {
        if (busy) return;
        busy = true;
        set(Phase.CHECKING,"Checking Backspace releases...",GoldForge.VERSION,0F);
        Thread worker = new Thread(() -> {
            try {
                JsonArray releases = new JsonParser().parse(read(API)).getAsJsonArray();
                JsonObject selected = null;
                int best = version(GoldForge.VERSION);
                for (JsonElement element : releases) {
                    JsonObject release = element.getAsJsonObject();
                    if (release.get("draft").getAsBoolean()) continue;
                    int candidate = version(release.get("tag_name").getAsString());
                    if (candidate > best && asset(release) != null) { selected = release; best = candidate; }
                }
                if (selected == null) { set(Phase.CURRENT,"Backspace is up to date",GoldForge.VERSION,1F); return; }
                String tag = selected.get("tag_name").getAsString();
                if (!install) { set(Phase.CURRENT,"Update available: "+tag,tag,1F); return; }
                set(Phase.DOWNLOADING,"Updating now: "+tag,tag,0F);
                CodeSource source = GoldForge.class.getProtectionDomain().getCodeSource();
                if (source == null) throw new IllegalStateException("Cannot locate installed JAR");
                Path current = Paths.get(source.getLocation().toURI());
                if (!Files.isRegularFile(current) || !current.getFileName().toString().endsWith(".jar"))
                    throw new IllegalStateException("Updates require a packaged JAR");
                Path staged = current.resolveSibling("backspace-update-staged.jar.download");
                download(asset(selected), staged, tag);
                if (Files.size(staged) < 100000 || !jarContainsMod(staged)) {
                    Files.deleteIfExists(staged);
                    throw new IllegalStateException("Downloaded file is not a valid Backspace mod JAR");
                }
                Path replacement = current.resolveSibling("backspace-" + tag.replaceAll("[^a-zA-Z0-9._-]", "") + ".jar");
                scheduleSwap(current, staged, replacement);
                set(Phase.READY,"Close Minecraft to install "+tag,tag,1F);
            } catch (Exception failure) {
                String detail=failure.getMessage();
                if(detail==null||detail.trim().isEmpty())detail=failure.getClass().getSimpleName();
                set(Phase.FAILED,"Update failed: "+detail,status.version,0F);
                org.apache.logging.log4j.LogManager.getLogger("Backspace/Updater").error("Update failed",failure);
            }
            finally { busy = false; }
        }, "Backspace-updater");
        worker.setDaemon(true);
        worker.start();
    }

    private static int version(String tag) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("^dev-(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(beta|dev)(\\d*)?)?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!match.matches()) return -1;
        int major = Integer.parseInt(match.group(1)), minor = Integer.parseInt(match.group(2));
        int patch = match.group(3) == null ? 0 : Integer.parseInt(match.group(3));
        int stage = match.group(4) == null ? 9 : "beta".equalsIgnoreCase(match.group(4)) ? 5 : 1;
        return major * 1000000 + minor * 10000 + patch * 100 + stage;
    }

    private static String asset(JsonObject release) {
        if(!release.has("assets") || !release.get("assets").isJsonArray())return null;
        for (JsonElement element : release.getAsJsonArray("assets")) {
            JsonObject item = element.getAsJsonObject();
            String name = item.get("name").getAsString().toLowerCase(Locale.ROOT);
            if (name.startsWith("backspace") && name.endsWith(".jar") && !name.contains("source")) {
                String url = item.get("browser_download_url").getAsString();
                if (url.startsWith("https://github.com/gms-scanner/Backspace/releases/download/")) return url;
            }
        }
        return null;
    }

    private static String read(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Backspace-Updater");
        try {
            int response=connection.getResponseCode();
            if(response!=200)throw new java.io.IOException("GitHub returned HTTP "+response);
            try (InputStream input = connection.getInputStream()) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 2000000) throw new IllegalStateException("Release response too large");
            }
            return output.toString("UTF-8");
            }
        } finally { connection.disconnect(); }
    }

    private static void download(String url, Path destination,String tag) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Backspace-Updater");
        try {
            int response=connection.getResponseCode();
            if(response!=200)throw new java.io.IOException("Download returned HTTP "+response);
            long total=connection.getContentLengthLong();
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192]; int count; long size = 0;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > 30000000) throw new IllegalStateException("Update exceeds size limit");
                output.write(buffer, 0, count);
                set(Phase.DOWNLOADING,"Updating now: "+tag,tag,total>0?(float)size/total:0F);
            }
            }
        } finally { connection.disconnect(); }
    }

    private static boolean jarContainsMod(Path path) throws Exception {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(path.toFile())) {
            return jar.getEntry("dev/goldforge/GoldForge.class") != null && jar.getEntry("mcmod.info") != null;
        }
    }

    private static void scheduleSwap(Path current, Path staged, Path replacement) throws Exception {
        long pid = Long.parseLong(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        Path script = Files.createTempFile("backspace-update-", ".ps1");
        String body = "param([int]$GamePid,[string]$Old,[string]$Staged,[string]$New)\n"
                + "Wait-Process -Id $GamePid -ErrorAction SilentlyContinue\n"
                + "Start-Sleep -Milliseconds 500\n"
                + "Move-Item -LiteralPath $Old -Destination ($Old + '.disabled') -Force\n"
                + "Move-Item -LiteralPath $Staged -Destination $New -Force\n"
                + "Remove-Item -LiteralPath $PSCommandPath -Force\n";
        Files.write(script, body.getBytes("UTF-8"));
        new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                Integer.toString((int)pid), current.toString(), staged.toString(), replacement.toString()).start();
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.player != null) mc.player.sendMessage(new TextComponentString("§e[Backspace] §f" + text));
            else org.apache.logging.log4j.LogManager.getLogger("Backspace/Updater").info(text);
        });
    }
}
