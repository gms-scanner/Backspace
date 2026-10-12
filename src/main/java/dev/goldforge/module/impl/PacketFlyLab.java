package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded, read-only PacketFly experiment recorder. It observes ordinary client
 * input, outbound movement packets, server position corrections, and NCP chat
 * alerts; it never creates, changes, or suppresses network packets.
 */
public final class PacketFlyLab extends Module {
    private static final Pattern NCP_MOVEMENT = Pattern.compile("(?i)\\[(MOVING_[A-Z_]+|PASSABLE)\\].*?\\bVL\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)(?:\\(\\+([0-9]+(?:\\.[0-9]+)?)\\))?");
    private static final Deque<TraceEvent> EVENTS = new ArrayDeque<TraceEvent>();
    private static volatile boolean capturing;
    private static long eventSequence;
    private static long movementSequence;
    private static long correctionSequence;
    private static Snapshot latestSnapshot;
    private static MovementPacket latestMovementPacket;
    private static Correction latestCorrection;
    private long enabledAtMillis;
    private int lastSampleTick = Integer.MIN_VALUE;

    public int sampleIntervalTicks = 1;
    public int traceLimit = 4096;
    /** Zero disables timed stop. */
    public int durationSeconds = 0;

    public PacketFlyLab() {
        super("PacketFlyLab", "Read-only movement, packet, correction, and NCP experiment trace.", Category.MOVEMENT, 0);
        addIntSetting("Sample interval (ticks)", 1, 20, 1, () -> sampleIntervalTicks, v -> sampleIntervalTicks = v);
        addIntSetting("Trace limit (events)", 256, 16384, 256, () -> traceLimit, v -> traceLimit = v);
        addIntSetting("Capture duration (seconds, 0=unlimited)", 0, 900, 1, () -> durationSeconds, v -> durationSeconds = v);
    }

    @Override protected void onEnable() {
        synchronized (PacketFlyLab.class) {
            EVENTS.clear();
            eventSequence = movementSequence = correctionSequence = 0L;
            latestSnapshot = null;
            latestMovementPacket = null;
            latestCorrection = null;
            capturing = true;
            enabledAtMillis = System.currentTimeMillis();
            lastSampleTick = Integer.MIN_VALUE;
            appendLocked("TRACE_START", enabledAtMillis, -1, "", "", "", "", "", "", "", "", "");
        }
    }

    @Override protected void onDisable() {
        synchronized (PacketFlyLab.class) {
            appendLocked("TRACE_STOP", System.currentTimeMillis(), currentTick(), "", "", "", "", "", "", "", "", "");
            capturing = false;
        }
    }

    @Override public void tick() {
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        if (durationSeconds > 0 && now - enabledAtMillis >= durationSeconds * 1000L) {
            addEvent("AUTO_STOP", now, player.ticksExisted, "durationSeconds=" + durationSeconds, "", "", "", "", "", "", "", "");
            setEnabled(false);
            return;
        }
        if (player.ticksExisted - lastSampleTick < Math.max(1, sampleIntervalTicks)) return;
        lastSampleTick = player.ticksExisted;
        float forward = player.movementInput == null ? 0F : player.movementInput.moveForward;
        float strafe = player.movementInput == null ? 0F : player.movementInput.moveStrafe;
        double yaw = Math.toRadians(player.rotationYaw);
        double wishX = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double wishZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        String input = String.format(Locale.ROOT, "forward=%.3f;strafe=%.3f;jump=%s;sneak=%s;sprint=%s",
                forward, strafe, player.movementInput != null && player.movementInput.jump,
                player.movementInput != null && player.movementInput.sneak, player.isSprinting());
        String position = vec(player.posX, player.posY, player.posZ);
        String rotation = String.format(Locale.ROOT, "%.3f,%.3f", player.rotationYaw, player.rotationPitch);
        String motion = vec(player.motionX, player.motionY, player.motionZ);
        String bounds = String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
                player.getEntityBoundingBox().minX, player.getEntityBoundingBox().minY, player.getEntityBoundingBox().minZ,
                player.getEntityBoundingBox().maxX, player.getEntityBoundingBox().maxY, player.getEntityBoundingBox().maxZ);
        String state = String.format(Locale.ROOT, "ground=%s;collidedH=%s;collidedV=%s;dimension=%d",
                player.onGround, player.collidedHorizontally, player.collidedVertically, player.dimension);
        synchronized (PacketFlyLab.class) {
            if (!capturing) return;
            latestSnapshot = new Snapshot(player.ticksExisted, now, player.posX, player.posY, player.posZ,
                    player.rotationYaw, player.rotationPitch, position, rotation, motion, input,
                    String.format(Locale.ROOT, "%.5f,%.5f", wishX, wishZ), bounds, state);
            appendLocked("CLIENT_SAMPLE", now, player.ticksExisted, "", position, rotation, motion,
                    input, latestSnapshot.wish, bounds, state, "");
        }
    }

    /** Called by the Netty observer before forwarding the outbound movement packet unchanged. */
    public static synchronized void recordOutbound(CPacketPlayer packet) {
        if (!capturing || packet == null) return;
        long now = System.currentTimeMillis();
        Snapshot sample = latestSnapshot;
        double x = sample == null ? 0D : sample.x;
        double y = sample == null ? 0D : sample.y;
        double z = sample == null ? 0D : sample.z;
        float yaw = sample == null ? 0F : sample.yaw;
        float pitch = sample == null ? 0F : sample.pitch;
        boolean hasPosition = packet instanceof CPacketPlayer.Position || packet instanceof CPacketPlayer.PositionRotation;
        boolean hasRotation = packet instanceof CPacketPlayer.Rotation || packet instanceof CPacketPlayer.PositionRotation;
        MovementPacket record = new MovementPacket(++movementSequence, now, sample == null ? -1 : sample.tick,
                packet.getClass().getSimpleName(), hasPosition, hasRotation,
                hasPosition ? packet.getX(x) : x, hasPosition ? packet.getY(y) : y, hasPosition ? packet.getZ(z) : z,
                hasRotation ? packet.getYaw(yaw) : yaw, hasRotation ? packet.getPitch(pitch) : pitch, packet.isOnGround());
        latestMovementPacket = record;
        String pos = hasPosition ? vec(record.x, record.y, record.z) : "unchanged";
        String rot = hasRotation ? String.format(Locale.ROOT, "%.3f,%.3f", record.yaw, record.pitch) : "unchanged";
        appendLocked("OUT_MOVEMENT", now, record.tick, "seq=" + record.sequence + ";type=" + record.type
                + ";positionPresent=" + hasPosition + ";rotationPresent=" + hasRotation + ";onGround=" + record.onGround,
                sample == null ? "" : sample.position, rot, "", "", "", "", "", "packetPos=" + pos);
    }

    /** Called on the channel event loop; raw absolute/relative packet values are retained verbatim. */
    public static synchronized Correction recordCorrection(SPacketPlayerPosLook packet) {
        if (!capturing || packet == null) return null;
        long now = System.currentTimeMillis();
        Snapshot sample = latestSnapshot;
        Collection<?> flags = packet.getFlags();
        MovementPacket preceding = latestMovementPacket;
        Correction correction = new Correction(++correctionSequence, now, sample == null ? -1 : sample.tick,
                packet.getX(), packet.getY(), packet.getZ(), packet.getYaw(), packet.getPitch(),
                flags == null ? "" : flags.toString(), preceding,
                sample == null ? "" : sample.position, sample == null ? "" : sample.rotation);
        latestCorrection = correction;
        appendLocked("CORRECTION_IN", now, correction.tick,
                "seq=" + correction.sequence + ";packet=SPacketPlayerPosLook;relativeFlags=" + correction.flags
                        + ";precedingMovementSeq=" + (preceding == null ? "" : preceding.sequence)
                + ";precedingMovement=" + (preceding == null ? "none" : preceding.describe()),
                correction.clientPositionAtReceive, correction.clientRotationAtReceive, "", "", "", "", "",
                "rawServerPos=" + vec(correction.x, correction.y, correction.z)
                        + ";rawServerRot=" + correction.yaw + "," + correction.pitch);
        return correction;
    }

    /** Runs on the client thread before vanilla applies the queued server correction. */
    public static synchronized void captureCorrectionPreApply(Correction correction) {
        if (!capturing || correction == null || mcStaticPlayer() == null) return;
        EntityPlayerSP player = mcStaticPlayer();
        correction.clientPreApply = vec(player.posX, player.posY, player.posZ);
        correction.clientPreApplyRotation = String.format(Locale.ROOT, "%.3f,%.3f", player.rotationYaw, player.rotationPitch);
        correction.clientPreApplyTick = player.ticksExisted;
        appendLocked("CORRECTION_PRE_APPLY", System.currentTimeMillis(), player.ticksExisted,
                "correctionSeq=" + correction.sequence + ";precedingMovementSeq="
                        + (correction.preceding == null ? "" : correction.preceding.sequence),
                correction.clientPreApply, correction.clientPreApplyRotation, "", "", "", "", "",
                "rawServerPos=" + vec(correction.x, correction.y, correction.z));
    }

    /** Captures NCP movement flags from server chat; correction linkage is explicitly temporal. */
    public static synchronized void recordNcpMessage(String formattedMessage) {
        if (!capturing || formattedMessage == null) return;
        String plain = TextFormatting.getTextWithoutFormattingCodes(formattedMessage);
        if (plain == null) return;
        Matcher matcher = NCP_MOVEMENT.matcher(plain);
        if (!matcher.find()) return;
        long now = System.currentTimeMillis();
        EntityPlayerSP player = mcStaticPlayer();
        int tick = player == null ? -1 : player.ticksExisted;
        Correction correction = latestCorrection;
        appendLocked("NCP_ALERT", now, tick,
                "check=" + matcher.group(1) + ";vl=" + matcher.group(2) + ";delta=" + nullToEmpty(matcher.group(3))
                        + ";association=temporal-only;nearestCorrectionSeq=" + (correction == null ? "" : correction.sequence),
                player == null ? "" : vec(player.posX, player.posY, player.posZ),
                player == null ? "" : String.format(Locale.ROOT, "%.3f,%.3f", player.rotationYaw, player.rotationPitch),
                player == null ? "" : vec(player.motionX, player.motionY, player.motionZ), "", "", "", "", plain);
    }

    public static synchronized boolean isCapturing() { return capturing; }
    public static synchronized int eventCount() { return EVENTS.size(); }
    public static synchronized List<String> recent(int count) {
        List<String> lines = new ArrayList<String>();
        int skip = Math.max(0, EVENTS.size() - Math.max(1, count));
        int index = 0;
        for (TraceEvent event : EVENTS) if (index++ >= skip) lines.add(event.describe());
        return Collections.unmodifiableList(lines);
    }
    public static synchronized void clearTrace() {
        EVENTS.clear(); eventSequence = movementSequence = correctionSequence = 0L;
        latestMovementPacket = null; latestCorrection = null;
    }
    public static synchronized String status() {
        return "PacketFlyLab capture=" + capturing + " events=" + EVENTS.size() + " limit="
                + (dev.goldforge.GoldForge.MODULES.get("PacketFlyLab") instanceof PacketFlyLab
                ? ((PacketFlyLab) dev.goldforge.GoldForge.MODULES.get("PacketFlyLab")).traceLimit : 0)
                + " movementPackets=" + movementSequence + " corrections=" + correctionSequence;
    }

    public static synchronized Path export(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) return null;
        Path directory = mcStaticDataDir().toPath().resolve("Backspace").resolve("packetfly");
        Files.createDirectories(directory);
        Path destination = directory.resolve(name + ".csv");
        Path temporary = directory.resolve(name + ".csv.tmp");
        List<String> lines = new ArrayList<String>();
        lines.add("event_seq,timestamp_ms,tick,event_type,details,client_position,client_rotation,motion,input,wish_direction,bounding_box,state,raw_server_response");
        synchronized (PacketFlyLab.class) { for (TraceEvent event : EVENTS) lines.add(event.csv()); }
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        try { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        return destination;
    }

    private void addEvent(String type, long timestamp, int tick, String details, String pos, String rot,
            String motion, String input, String wish, String bounds, String state, String response) {
        synchronized (PacketFlyLab.class) {
            if (capturing) appendLocked(type, timestamp, tick, details, pos, rot, motion, input, wish, bounds, state, response);
        }
    }

    private static void appendLocked(String type, long timestamp, int tick, String details, String pos,
            String rot, String motion, String input, String wish, String bounds, String state, String response) {
        PacketFlyLab module = dev.goldforge.GoldForge.MODULES.get("PacketFlyLab") instanceof PacketFlyLab
                ? (PacketFlyLab) dev.goldforge.GoldForge.MODULES.get("PacketFlyLab") : null;
        int limit = module == null ? 4096 : Math.max(256, Math.min(16384, module.traceLimit));
        EVENTS.addLast(new TraceEvent(++eventSequence, timestamp, tick, type, details, pos, rot, motion,
                input, wish, bounds, state, response));
        while (EVENTS.size() > limit) EVENTS.removeFirst();
    }

    private static int currentTick() { EntityPlayerSP player = mcStaticPlayer(); return player == null ? -1 : player.ticksExisted; }
    private static EntityPlayerSP mcStaticPlayer() { return net.minecraft.client.Minecraft.getMinecraft().player; }
    private static java.io.File mcStaticDataDir() { return net.minecraft.client.Minecraft.getMinecraft().mcDataDir; }
    private static String vec(double x, double y, double z) { return String.format(Locale.ROOT, "%.5f,%.5f,%.5f", x, y, z); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private static final class Snapshot {
        final int tick; final long timestamp; final double x, y, z; final float yaw, pitch;
        final String position, rotation, motion, input, wish, bounds, state;
        Snapshot(int tick, long timestamp, double x, double y, double z, float yaw, float pitch,
                String position, String rotation, String motion, String input, String wish, String bounds, String state) {
            this.tick=tick; this.timestamp=timestamp; this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch;
            this.position=position; this.rotation=rotation;
            this.motion=motion; this.input=input; this.wish=wish; this.bounds=bounds; this.state=state;
        }
    }

    private static final class MovementPacket {
        final long sequence, timestamp; final int tick; final String type;
        final boolean hasPosition, hasRotation, onGround; final double x, y, z; final float yaw, pitch;
        MovementPacket(long sequence, long timestamp, int tick, String type, boolean hasPosition,
                boolean hasRotation, double x, double y, double z, float yaw, float pitch, boolean onGround) {
            this.sequence=sequence; this.timestamp=timestamp; this.tick=tick; this.type=type;
            this.hasPosition=hasPosition; this.hasRotation=hasRotation; this.x=x; this.y=y; this.z=z;
            this.yaw=yaw; this.pitch=pitch; this.onGround=onGround;
        }
        String describe() {
            return String.format(Locale.ROOT, "#%d@%d tick=%d %s pos=%s rot=%s onGround=%s", sequence, timestamp,
                    tick, type, hasPosition ? vec(x,y,z) : "unchanged", hasRotation
                            ? String.format(Locale.ROOT, "%.3f,%.3f", yaw, pitch) : "unchanged", onGround);
        }
    }

    public static final class Correction {
        public final long sequence, timestamp; public final int tick;
        public final double x, y, z; public final float yaw, pitch; public final String flags;
        private final MovementPacket preceding; private final String clientPositionAtReceive, clientRotationAtReceive;
        private String clientPreApply="", clientPreApplyRotation=""; private int clientPreApplyTick=-1;
        Correction(long sequence, long timestamp, int tick, double x, double y, double z, float yaw,
                float pitch, String flags, MovementPacket preceding, String clientPosition, String clientRotation) {
            this.sequence=sequence; this.timestamp=timestamp; this.tick=tick; this.x=x; this.y=y; this.z=z;
            this.yaw=yaw; this.pitch=pitch; this.flags=flags; this.preceding=preceding;
            this.clientPositionAtReceive=clientPosition; this.clientRotationAtReceive=clientRotation;
        }
    }

    private static final class TraceEvent {
        final long sequence, timestamp; final int tick; final String type, details, position, rotation, motion,
                input, wish, bounds, state, response;
        TraceEvent(long sequence, long timestamp, int tick, String type, String details, String position,
                String rotation, String motion, String input, String wish, String bounds, String state, String response) {
            this.sequence=sequence; this.timestamp=timestamp; this.tick=tick; this.type=type; this.details=details;
            this.position=position; this.rotation=rotation; this.motion=motion; this.input=input; this.wish=wish;
            this.bounds=bounds; this.state=state; this.response=response;
        }
        String describe() { return String.format(Locale.ROOT, "#%d ms=%d tick=%d %s %s pos=%s response=%s",
                sequence, timestamp, tick, type, details, position, response); }
        String csv() {
            return sequence+","+timestamp+","+tick+","+quote(type)+","+quote(details)+","+quote(position)+","+
                    quote(rotation)+","+quote(motion)+","+quote(input)+","+quote(wish)+","+quote(bounds)+","+
                    quote(state)+","+quote(response);
        }
        private static String quote(String value) { return "\""+(value == null ? "" : value.replace("\"", "\"\""))+"\""; }
    }
}
