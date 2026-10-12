package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Client-side visual and sound effects for player deaths. */
public final class KillFX extends Module {
    /** 0=Thunderstrike, 1=Totem burst, 2=Soul burst, 3=Shockwave. */
    public int effect = 0;
    /** 1 triggers only when the local player is the damage source; 0 accepts any remote player death. */
    public int localKillsOnly = 1;
    public int soundEnabled = 1;
    public int soundVolume = 80;
    /** Persisted path to the imported WAV. */
    public String soundPath = "";

    private static final long DEATH_DEDUPE_MS = 1500L;
    private static final int MAX_DEATH_CACHE = 128;
    private static final long MAX_SOUND_BYTES = 10L * 1024L * 1024L;
    private final Map<UUID, Long> recentDeaths = new LinkedHashMap<UUID, Long>();
    private final Random random = new Random();
    private Clip soundClip;
    private String soundStatus = "No sound imported";

    public KillFX() {
        super("KillFX", "Plays a local effect at player death locations.", Category.RENDER, 0);
        addIntSetting("Effect (0 Thunder, 1 Totem, 2 Soul, 3 Ring)", 0, 3, 1, () -> effect, v -> effect = v);
        addIntSetting("Local player kills only (0/1)", 0, 1, 1, () -> localKillsOnly, v -> localKillsOnly = v);
        addIntSetting("Imported sound (0/1)", 0, 1, 1, () -> soundEnabled, v -> soundEnabled = v);
        addIntSetting("Sound volume", 0, 100, 5, () -> soundVolume, v -> soundVolume = v);
    }

    @Override protected void onEnable() { loadSound(); }
    @Override protected void onDisable() { closeSound(); }

    public String soundLabel() {
        return soundPath == null || soundPath.isEmpty() ? "No WAV" : new File(soundPath).getName();
    }

    public String soundStatus() { return soundStatus; }

    public void chooseSound() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import a kill effect sound");
        chooser.setFileFilter(new FileNameExtensionFilter("WAV audio (*.wav)", "wav"));
        if (soundPath != null && !soundPath.isEmpty()) {
            File parent = new File(soundPath).getParentFile();
            if (parent != null && parent.isDirectory()) chooser.setCurrentDirectory(parent);
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        if (selected == null || !selected.isFile()) return;
        try {
            if (Files.size(selected.toPath()) > MAX_SOUND_BYTES) throw new IOException("WAV must be 10 MB or smaller");
            Path directory = mc.mcDataDir.toPath().resolve("Backspace").resolve("killfx");
            Files.createDirectories(directory);
            Path destination = directory.resolve("kill.wav");
            Path temporary = directory.resolve("kill.wav.tmp");
            Files.copy(selected.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            soundPath = destination.toAbsolutePath().toString();
            loadSound();
            dev.goldforge.ConfigManager.save();
        } catch (IOException failure) {
            soundStatus = "Import failed: " + failure.getMessage();
        }
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (!enabled || mc.world == null || !mc.world.isRemote || !(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer target = (EntityPlayer) event.getEntityLiving();
        if (target == mc.player) return;
        DamageSource source = event.getSource();
        if (localKillsOnly != 0 && !isLocalDamage(source)) return;

        long now = System.currentTimeMillis();
        Long last = recentDeaths.get(target.getUniqueID());
        if (last != null && now - last < DEATH_DEDUPE_MS) return;
        recentDeaths.put(target.getUniqueID(), now);
        while (recentDeaths.size() > MAX_DEATH_CACHE) recentDeaths.remove(recentDeaths.keySet().iterator().next());

        double x = target.posX, y = target.posY, z = target.posZ;
        spawnEffect(x, y, z);
        if (soundEnabled != 0) playSound();
    }

    private boolean isLocalDamage(DamageSource source) {
        if (source == null || mc.player == null) return false;
        Entity trueSource = source.getTrueSource();
        Entity immediateSource = source.getImmediateSource();
        return trueSource == mc.player || immediateSource == mc.player;
    }

    private void spawnEffect(double x, double y, double z) {
        int selected = Math.max(0, Math.min(3, effect));
        if (selected == 0) {
            mc.world.addWeatherEffect(new EntityLightningBolt(mc.world, x, y, z, true));
            particleBurst(EnumParticleTypes.CRIT_MAGIC, x, y + 0.8D, z, 24, 0.75D, 0.35D);
        } else if (selected == 1) {
            particleBurst(EnumParticleTypes.TOTEM, x, y + 0.9D, z, 42, 0.85D, 0.7D);
            particleBurst(EnumParticleTypes.CRIT, x, y + 0.9D, z, 16, 0.65D, 0.6D);
        } else if (selected == 2) {
            particleBurst(EnumParticleTypes.SPELL_MOB, x, y + 0.75D, z, 36, 0.7D, 0.55D);
            particleBurst(EnumParticleTypes.SMOKE_NORMAL, x, y + 0.4D, z, 14, 0.5D, 0.35D);
        } else {
            particleBurst(EnumParticleTypes.FIREWORKS_SPARK, x, y + 0.5D, z, 32, 1.1D, 0.12D);
            particleBurst(EnumParticleTypes.EXPLOSION_NORMAL, x, y + 0.45D, z, 8, 0.7D, 0.25D);
        }
    }

    private void particleBurst(EnumParticleTypes type, double x, double y, double z, int count, double spread, double speed) {
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() * 2.0D - 1.0D) * spread;
            double dy = (random.nextDouble() * 2.0D - 1.0D) * spread;
            double dz = (random.nextDouble() * 2.0D - 1.0D) * spread;
            mc.world.spawnParticle(type, x + dx, y + dy, z + dz, dx * speed, dy * speed, dz * speed);
        }
    }

    private void loadSound() {
        closeSound();
        if (soundPath == null || soundPath.trim().isEmpty()) { soundStatus = "No sound imported"; return; }
        File file = new File(soundPath);
        if (!file.isFile()) { soundStatus = "WAV file missing"; return; }
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            soundClip = AudioSystem.getClip();
            soundClip.open(stream);
            soundStatus = "WAV ready";
        } catch (Exception failure) {
            closeSound();
            soundStatus = "WAV could not be opened";
        }
    }

    private void playSound() {
        if (soundClip == null && soundPath != null && !soundPath.isEmpty()) loadSound();
        if (soundClip == null) return;
        try {
            soundClip.stop();
            soundClip.setFramePosition(0);
            soundClip.start();
        } catch (Exception ignored) { soundStatus = "WAV playback failed"; }
    }

    private void closeSound() {
        if (soundClip != null) {
            try { soundClip.stop(); soundClip.close(); } catch (Exception ignored) {}
            soundClip = null;
        }
    }
}
