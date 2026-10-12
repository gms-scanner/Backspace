package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Experimental incremental movement probe with bounded packet/correction diagnostics. */
public final class Phase extends Module {
    private static final int MAX_EVENTS = 512;
    private static final long PRE_APPLY_CAPTURE_WAIT_MS = 1500L;
    private static final Deque<String> DIAGNOSTICS = new ArrayDeque<String>();
    private static volatile boolean captureEnabled;
    private static long outgoingMovementPackets;
    private static long serverCorrections;
    private static long correctionSequence;
    private static final Deque<CorrectionRecord> pendingCorrections = new ArrayDeque<CorrectionRecord>();
    private static volatile MovementRecord lastMovementRecord;
    private static volatile CorrectionRecord lastCorrectionRecord;
    private static volatile int lastFreecamHandoffPlayerTick = Integer.MIN_VALUE / 2;
    private static final Pattern NCP_ALERT_PATTERN = Pattern.compile("\\[(MOVING_[A-Z]+)\\].*?\\bVL=([0-9]+(?:\\.[0-9]+)?)\\(\\+([0-9]+(?:\\.[0-9]+)?)\\)");
    private static String precedingMovementEvent = "none";
    private static volatile int sampledTick = -1;
    private static volatile boolean freecamActive;
    private static volatile double sampledX, sampledY, sampledZ;
    private static volatile float sampledYaw, sampledPitch;
    private static long precedingMovementAt;

    public int sampleIntervalTicks = 1;
    public int positionThresholdMilli = 1;
    public int mode = 0;
    public int ncpWindowSeconds = 2;
    public int ncpPauseTicks = 4;
    public int stepThousandths = 3;
    public int sideBiasPercent = 20;
    public int biasSweepStepPercent = 5;
    public int retryDelayTicks = 1;
    public int correctionWindowTicks = 4;
    public int handoffProbePauseTicks = 3;
    private double lastX, lastY, lastZ, lastYaw, lastPitch;
    private boolean hasLastPosition;
    private int lastSampleTick;
    private int retryTicks;
    private int activeSideBias;
    private int lastConfiguredBias;
    private int biasSweepDirection = 1;
    private boolean pendingPhaseAttempt;
    private double pendingTargetX, pendingTargetY, pendingTargetZ;
    private int pendingAttemptTick;
    private long pendingAttemptAtMillis;
    private boolean pendingAttemptAlerted;
    private int lastHandoffPauseLogTick = Integer.MIN_VALUE;
    private boolean ncpFreecamSettingsCaptured;
    private boolean ncpPreviousFreecamEnabled;
    private int ncpPreviousHandoffSetting;
    private int ncpPreviousDragSetting;
    private int ncpFreecamStage;
    private int ncpStageStartTick;
    private int ncpLastStageLogTick = Integer.MIN_VALUE;

    public Phase() {
        super("Phase", "Experimental incremental movement probe with packet and correction diagnostics.", Category.MOVEMENT, 0);
        // Keep legacy saved mode=2 active: older configs used 2 for incremental movement.
        addIntSetting("Mode (0 NCP sweep, 1 direct, 2 legacy sweep, 3 trace only, 4 NCP)", 0, 4, 1, () -> mode, v -> mode = v);
        addIntSetting("NCP Freecam handoff window (seconds)", 1, 30, 1, () -> ncpWindowSeconds, v -> ncpWindowSeconds = v);
        addIntSetting("NCP Freecam pause after handoff (ticks)", 1, 40, 1, () -> ncpPauseTicks, v -> ncpPauseTicks = v);
        addIntSetting("Movement step (thousandths)", 1, 100, 1, () -> stepThousandths, v -> stepThousandths = v);
        addIntSetting("Diagonal side bias (%)", -200, 200, 1, () -> sideBiasPercent, v -> sideBiasPercent = v);
        addIntSetting("Automatic bias sweep step (%)", 1, 50, 1, () -> biasSweepStepPercent, v -> biasSweepStepPercent = v);
        addIntSetting("Retry delay (ticks)", 1, 10, 1, () -> retryDelayTicks, v -> retryDelayTicks = v);
        addIntSetting("Correction response window (ticks)", 2, 20, 1, () -> correctionWindowTicks, v -> correctionWindowTicks = v);
        addIntSetting("Pause Phase probes after camera handoff (ticks)", 0, 20, 1, () -> handoffProbePauseTicks, v -> handoffProbePauseTicks = v);
        addIntSetting("Position sample interval (ticks)", 1, 20, 1, () -> sampleIntervalTicks, v -> sampleIntervalTicks = v);
        addIntSetting("Position change threshold (mm)", 0, 1000, 1, () -> positionThresholdMilli, v -> positionThresholdMilli = v);
    }

    @Override protected void onEnable() {
        captureEnabled = true;
        hasLastPosition = false;
        retryTicks = 0;
        pendingPhaseAttempt = false;
        pendingAttemptAlerted = false;
        activeSideBias = clamp(sideBiasPercent, -200, 200);
        lastConfiguredBias = sideBiasPercent;
        biasSweepDirection = activeSideBias < 0 ? -1 : 1;
        ncpFreecamStage = 0;
        ncpFreecamSettingsCaptured = false;
        addDiagnostic("TRACE_START tick=" + currentTick());
    }

    @Override protected void onDisable() {
        addDiagnostic("TRACE_STOP tick=" + currentTick());
        restoreNcpFreecam();
        captureEnabled = false;
        hasLastPosition = false;
        retryTicks = 0;
        pendingPhaseAttempt = false;
        pendingAttemptAlerted = false;
    }

    @Override public void tick() {
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) return;
        if (mode == 4) {
            tickNcpFreecam(player);
        } else if (ncpFreecamSettingsCaptured) {
            restoreNcpFreecam();
        }
        sampledTick = player.ticksExisted;
        sampledX = player.posX; sampledY = player.posY; sampledZ = player.posZ;
        sampledYaw = player.rotationYaw; sampledPitch = player.rotationPitch;
        recordPostCorrection(player);
        if (retryTicks > 0) retryTicks--;
        if (sideBiasPercent != lastConfiguredBias && !pendingPhaseAttempt) {
            activeSideBias = clamp(sideBiasPercent, -200, 200);
            lastConfiguredBias = sideBiasPercent;
            biasSweepDirection = activeSideBias < 0 ? -1 : 1;
        }
        expirePendingAttempt(player);
        int handoffAge = player.ticksExisted - lastFreecamHandoffPlayerTick;
        boolean handoffProbePaused = handoffAge >= 0 && handoffAge < Math.max(0, handoffProbePauseTicks);
        if (handoffProbePaused && lastHandoffPauseLogTick != player.ticksExisted) {
            lastHandoffPauseLogTick = player.ticksExisted;
            addDiagnostic("PHASE_PROBE_PAUSED_AFTER_FREECAM_HANDOFF tick=" + player.ticksExisted + " age=" + handoffAge);
        }
        if (mode != 3 && mode != 4 && !pendingPhaseAttempt && retryTicks == 0 && !handoffProbePaused
                && (player.collidedHorizontally || intersectsSolid(player))) attemptMovement(player);
        if (player.ticksExisted - lastSampleTick < Math.max(1, sampleIntervalTicks)) return;
        lastSampleTick = player.ticksExisted;

        double dx = hasLastPosition ? player.posX - lastX : 0.0D;
        double dy = hasLastPosition ? player.posY - lastY : 0.0D;
        double dz = hasLastPosition ? player.posZ - lastZ : 0.0D;
        double threshold = Math.max(0, positionThresholdMilli) / 1000.0D;
        boolean changed = !hasLastPosition || Math.abs(dx) >= threshold || Math.abs(dy) >= threshold || Math.abs(dz) >= threshold
                || player.rotationYaw != lastYaw || player.rotationPitch != lastPitch;
        if (changed) {
            AxisAlignedBB box = player.getEntityBoundingBox();
            boolean intersects = !mc.world.getCollisionBoxes(player, box.contract(0.001D, 0.001D, 0.001D)).isEmpty();
            addDiagnostic(String.format(Locale.ROOT,
                    "LOCAL tick=%d pos=%.4f,%.4f,%.4f delta=%.4f,%.4f,%.4f rot=%.2f,%.2f motion=%.4f,%.4f,%.4f onGround=%s collidedH=%s collidedV=%s insideCollision=%s",
                    player.ticksExisted, player.posX, player.posY, player.posZ, dx, dy, dz,
                    player.rotationYaw, player.rotationPitch, player.motionX, player.motionY, player.motionZ,
                    player.onGround, player.collidedHorizontally, player.collidedVertically, intersects));
        }
        lastX = player.posX; lastY = player.posY; lastZ = player.posZ;
        lastYaw = player.rotationYaw; lastPitch = player.rotationPitch;
        hasLastPosition = true;
    }

    /** Runs Freecam's existing camera-window/handoff lifecycle without changing Freecam itself. */
    private void tickNcpFreecam(EntityPlayerSP player) {
        dev.goldforge.module.Module module = dev.goldforge.GoldForge.MODULES.get("Freecam");
        if (!(module instanceof Freecam)) {
            addDiagnostic("NCP_FREECAM_UNAVAILABLE tick=" + player.ticksExisted);
            return;
        }
        Freecam freecam = (Freecam) module;
        if (!ncpFreecamSettingsCaptured) {
            ncpPreviousFreecamEnabled = freecam.enabled;
            ncpPreviousHandoffSetting = freecam.handoffOnDisable;
            ncpPreviousDragSetting = freecam.dragPlayerWithCamera;
            ncpFreecamSettingsCaptured = true;
            ncpFreecamStage = 0;
            ncpStageStartTick = player.ticksExisted;
            addDiagnostic("NCP_FREECAM_START tick=" + player.ticksExisted + " priorEnabled=" + ncpPreviousFreecamEnabled);
        }

        // Phase owns these runtime values only while mode 4 is active. The
        // Freecam implementation and its normal user settings remain intact.
        freecam.handoffOnDisable = 1;
        freecam.dragPlayerWithCamera = 0;
        if (ncpFreecamStage == 0) {
            if (!freecam.enabled) {
                freecam.setEnabled(true);
                persistNcpFreecamSettings(freecam);
                ncpStageStartTick = player.ticksExisted;
                addDiagnostic("NCP_FREECAM_WINDOW_OPEN tick=" + player.ticksExisted);
            }
            int windowTicks = Math.max(1, ncpWindowSeconds) * 20;
            if (freecam.enabled && player.ticksExisted - ncpStageStartTick >= windowTicks) {
                freecam.setEnabled(false); // Existing Freecam disable path performs the handoff.
                persistNcpFreecamSettings(freecam);
                ncpFreecamStage = 1;
                ncpStageStartTick = player.ticksExisted;
                addDiagnostic("NCP_FREECAM_HANDOFF tick=" + player.ticksExisted + " windowTicks=" + windowTicks
                        + " player=" + formatPosition(player.posX, player.posY, player.posZ));
            }
        } else if (player.ticksExisted - ncpStageStartTick >= Math.max(1, ncpPauseTicks)) {
            ncpFreecamStage = 0;
            freecam.setEnabled(true);
            persistNcpFreecamSettings(freecam);
            ncpStageStartTick = player.ticksExisted;
            addDiagnostic("NCP_FREECAM_WINDOW_OPEN tick=" + player.ticksExisted);
        }
    }

    /** Rewrites config with the user's saved Freecam settings while retaining temporary runtime overrides. */
    private void persistNcpFreecamSettings(Freecam freecam) {
        if (!ncpFreecamSettingsCaptured) return;
        int runtimeHandoff = freecam.handoffOnDisable;
        int runtimeDrag = freecam.dragPlayerWithCamera;
        freecam.handoffOnDisable = ncpPreviousHandoffSetting;
        freecam.dragPlayerWithCamera = ncpPreviousDragSetting;
        dev.goldforge.ConfigManager.save();
        freecam.handoffOnDisable = runtimeHandoff;
        freecam.dragPlayerWithCamera = runtimeDrag;
    }

    private void restoreNcpFreecam() {
        if (!ncpFreecamSettingsCaptured) return;
        dev.goldforge.module.Module module = dev.goldforge.GoldForge.MODULES.get("Freecam");
        if (module instanceof Freecam) {
            Freecam freecam = (Freecam) module;
            if (!ncpPreviousFreecamEnabled && freecam.enabled) freecam.setEnabled(false);
            freecam.handoffOnDisable = ncpPreviousHandoffSetting;
            freecam.dragPlayerWithCamera = ncpPreviousDragSetting;
            if (ncpPreviousFreecamEnabled && !freecam.enabled) freecam.setEnabled(true);
            dev.goldforge.ConfigManager.save();
            addDiagnostic("NCP_FREECAM_RESTORE tick=" + currentTick() + " enabled=" + freecam.enabled
                    + " handoff=" + freecam.handoffOnDisable + " drag=" + freecam.dragPlayerWithCamera);
        }
        ncpFreecamSettingsCaptured = false;
        ncpFreecamStage = 0;
    }

    private static String formatPosition(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.4f,%.4f,%.4f", x, y, z);
    }

    /** Sends a small candidate move and records the server's response; it does not claim acceptance. */
    private void attemptMovement(EntityPlayerSP player) {
        if (mc.getConnection() == null) return;
        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        double magnitude = Math.sqrt(forward * forward + strafe * strafe);
        if (magnitude < 1.0E-4D) return;
        forward /= magnitude;
        strafe /= magnitude;

        double yaw = Math.toRadians(player.rotationYaw);
        double dirX = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double dirZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        if (mode != 1 && activeSideBias != 0) {
            double bias = activeSideBias / 100.0D;
            dirX += bias * Math.cos(yaw);
            dirZ += bias * Math.sin(yaw);
            double biasedMagnitude = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (biasedMagnitude > 1.0E-6D) { dirX /= biasedMagnitude; dirZ /= biasedMagnitude; }
        }

        double step = stepThousandths / 1000.0D;
        double fromX = player.posX, fromY = player.posY, fromZ = player.posZ;
        double nextX = fromX + dirX * step, nextZ = fromZ + dirZ * step;
        addDiagnostic(String.format(Locale.ROOT,
                "PHASE_ATTEMPT tick=%d mode=%d bias=%d%% step=%.4f from=%.4f,%.4f,%.4f to=%.4f,%.4f,%.4f input=%.2f,%.2f collidedH=%s insideCollision=%s freecam=%s",
                player.ticksExisted, mode, mode == 1 ? 0 : activeSideBias, step, fromX, fromY, fromZ, nextX, fromY, nextZ,
                player.movementInput.moveForward, player.movementInput.moveStrafe,
                player.collidedHorizontally, intersectsSolid(player), freecamActive));
        if (dev.goldforge.network.PacketManager.send(new CPacketPlayer.Position(nextX, fromY, nextZ, player.onGround), name)) {
            // Keep local prediction aligned with the report; inbound corrections replace it if rejected.
            player.setPosition(nextX, fromY, nextZ);
            retryTicks = Math.max(1, retryDelayTicks);
            pendingPhaseAttempt = true;
            pendingAttemptAlerted = false;
            pendingAttemptTick = player.ticksExisted;
            pendingAttemptAtMillis = System.currentTimeMillis();
            pendingTargetX = nextX;
            pendingTargetY = fromY;
            pendingTargetZ = nextZ;
        }
    }

    private void expirePendingAttempt(EntityPlayerSP player) {
        if (!pendingPhaseAttempt || player.ticksExisted - pendingAttemptTick < Math.max(2, correctionWindowTicks)) return;
        addDiagnostic(String.format(Locale.ROOT,
                "PHASE_NO_CORRECTION tick=%d target=%.4f,%.4f local=%.4f,%.4f; no correction observed within response window",
                player.ticksExisted, pendingTargetX, pendingTargetZ, player.posX, player.posZ));
        pendingPhaseAttempt = false;
        pendingAttemptAlerted = false;
    }

    private void advanceBiasAfterRejectedAttempt() {
        if (mode == 1 || mode == 3) return;
        int step = Math.max(1, biasSweepStepPercent);
        int next = activeSideBias + biasSweepDirection * step;
        if (next > 200) { next = 200; biasSweepDirection = -1; }
        else if (next < -200) { next = -200; biasSweepDirection = 1; }
        if (next == activeSideBias) {
            biasSweepDirection = -biasSweepDirection;
            next = clamp(activeSideBias + biasSweepDirection * step, -200, 200);
        }
        activeSideBias = next;
        addDiagnostic("BIAS_SWEEP rejected move; next side bias=" + activeSideBias + "%");
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private boolean intersectsSolid(EntityPlayerSP player) {
        AxisAlignedBB box = player.getEntityBoundingBox().contract(0.001D, 0.001D, 0.001D);
        return !mc.world.getCollisionBoxes(player, box).isEmpty();
    }

    /** Called by the Netty observer before forwarding an outbound packet unchanged. */
    public static synchronized void recordOutgoingMovement(CPacketPlayer packet) {
        if (!captureEnabled || packet == null) return;
        long sequence = ++outgoingMovementPackets;
        double x = sampledX, y = sampledY, z = sampledZ;
        float yaw = sampledYaw, pitch = sampledPitch;
        boolean includesPosition = packet instanceof CPacketPlayer.Position || packet instanceof CPacketPlayer.PositionRotation;
        boolean includesRotation = packet instanceof CPacketPlayer.Rotation || packet instanceof CPacketPlayer.PositionRotation;
        String packetPos = includesPosition
                ? String.format(Locale.ROOT, "%.4f,%.4f,%.4f", packet.getX(x), packet.getY(y), packet.getZ(z))
                : "unchanged";
        String packetRot = includesRotation
                ? String.format(Locale.ROOT, "%.2f,%.2f", packet.getYaw(yaw), packet.getPitch(pitch))
                : "unchanged";
        String kind = packet.getClass().getSimpleName();
        precedingMovementAt = System.currentTimeMillis();
        lastMovementRecord = new MovementRecord(sequence, precedingMovementAt, sampledTick, kind,
                includesPosition, includesRotation,
                includesPosition ? packet.getX(x) : x,
                includesPosition ? packet.getY(y) : y,
                includesPosition ? packet.getZ(z) : z,
                includesRotation ? packet.getYaw(yaw) : yaw,
                includesRotation ? packet.getPitch(pitch) : pitch, packet.isOnGround());
        precedingMovementEvent = String.format(Locale.ROOT,
                "OUT #%d tick=%d type=%s pos=%s rot=%s onGround=%s",
                sequence, sampledTick, kind, packetPos, packetRot, packet.isOnGround());
        addDiagnostic(precedingMovementEvent);
    }

    /** Called on the channel event loop; preserves the exact inbound packet and preceding movement packet. */
    public static synchronized CorrectionRecord recordServerCorrection(double x, double y, double z,
            float yaw, float pitch, Set<?> relativeFlags) {
        if (!captureEnabled) return null;
        long sequence = ++correctionSequence;
        serverCorrections++;
        CorrectionRecord record = new CorrectionRecord(sequence, System.currentTimeMillis(), sampledTick,
                x, y, z, yaw, pitch, relativeFlags, lastMovementRecord);
        if (pendingCorrections.size() == 32) pendingCorrections.removeFirst();
        pendingCorrections.addLast(record);
        lastCorrectionRecord = record;
        long ageMillis = precedingMovementAt == 0L ? -1L : Math.max(0L, System.currentTimeMillis() - precedingMovementAt);
        addDiagnostic(String.format(Locale.ROOT,
                "CORRECTION_IN #%d packet=SPacketPlayerPosLook recvMs=%d tick=%d rawPos=%.4f,%.4f,%.4f rawRot=%.2f,%.2f relative=%s precedingMovementAgeMs=%d preceding=%s",
                sequence, record.receivedAtMillis, sampledTick, x, y, z, yaw, pitch,
                record.relativeFlags, ageMillis, record.precedingMovement == null ? "none" : record.precedingMovement.describe()));
        return record;
    }

    /** Runs on Minecraft's main thread before NetHandlerPlayClient applies the queued correction. */
    public static void captureCorrectionClientPreApply(CorrectionRecord record) {
        if (record == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) {
            addDiagnostic("CORRECTION_CLIENT_PRE_MISSING #" + record.sequence + " reason=no-local-player");
            return;
        }
        record.captureClientPreApply(player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch, player.ticksExisted);
        addDiagnostic(String.format(Locale.ROOT,
                "CORRECTION_CLIENT_PRE #%d tick=%d pos=%.4f,%.4f,%.4f rot=%.2f,%.2f",
                record.sequence, player.ticksExisted, player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch));
    }

    private void recordPostCorrection(EntityPlayerSP player) {
        List<CorrectionRecord> records = new ArrayList<CorrectionRecord>();
        long now = System.currentTimeMillis();
        synchronized (Phase.class) {
            for (CorrectionRecord record : pendingCorrections) {
                if (record.preApplyCaptured || now - record.receivedAtMillis >= PRE_APPLY_CAPTURE_WAIT_MS) {
                    records.add(record);
                }
            }
            pendingCorrections.removeAll(records);
        }
        if (!records.isEmpty()) retryTicks = Math.max(retryTicks, Math.max(1, retryDelayTicks));
        for (CorrectionRecord record : records) {
            if (!record.preApplyCaptured) {
                addDiagnostic(String.format(Locale.ROOT,
                        "CORRECTION_CLIENT_PRE_TIMEOUT #%d recvMs=%d ageMs=%d",
                        record.sequence, record.receivedAtMillis, Math.max(0L, now - record.receivedAtMillis)));
            }
            double[] corrected = record.resolvePosition();
            boolean haveAuthoritativeTarget = corrected != null;
            addDiagnostic(String.format(Locale.ROOT,
                    "CORRECTION_RESOLVED #%d recvMs=%d raw=%.4f,%.4f,%.4f relative=%s preApply=%s prePos=%s resolvedTarget=%s",
                    record.sequence, record.receivedAtMillis, record.rawX, record.rawY, record.rawZ,
                    record.relativeFlags, record.preApplyCaptured,
                    record.preApplyCaptured ? String.format(Locale.ROOT, "%.4f,%.4f,%.4f", record.clientX, record.clientY, record.clientZ) : "missing",
                    haveAuthoritativeTarget ? String.format(Locale.ROOT, "%.4f,%.4f,%.4f", corrected[0], corrected[1], corrected[2]) : "unresolved"));
            boolean candidatePacketImmediatelyPreceded = record.precedingMovement != null
                    && record.precedingMovement.matchesPosition(pendingTargetX, pendingTargetY, pendingTargetZ);
            if (pendingPhaseAttempt && !record.phaseAttemptMatched
                    && record.receivedAtMillis >= pendingAttemptAtMillis
                    && candidatePacketImmediatelyPreceded && haveAuthoritativeTarget) {
                double error = Math.hypot(corrected[0] - pendingTargetX, corrected[2] - pendingTargetZ);
                double tolerance = Math.max(0.001D, stepThousandths / 2000.0D);
                boolean rejected = error > tolerance;
                addDiagnostic(String.format(Locale.ROOT,
                        "PHASE_CORRECTION_LINK correction=#%d movement=#%d recvMs=%d target=%.4f,%.4f correctionTarget=%.4f,%.4f error=%.4f result=%s",
                        record.sequence, record.precedingMovement.sequence, record.receivedAtMillis,
                        pendingTargetX, pendingTargetZ, corrected[0], corrected[2], error,
                        rejected ? "CORRECTED" : "NEAR_TARGET"));
                record.phaseAttemptMatched = true;
                if (rejected && !pendingAttemptAlerted) advanceBiasAfterRejectedAttempt();
                else if (rejected) addDiagnostic("BIAS_SWEEP correction feedback already counted from this attempt's server alert");
                pendingPhaseAttempt = false;
                pendingAttemptAlerted = false;
            } else {
                addDiagnostic(String.format(Locale.ROOT,
                        "CORRECTION_LINK_UNMATCHED #%d tick=%d reason=%s immediateMovement=%s",
                        record.sequence, player.ticksExisted,
                        !record.preApplyCaptured && record.hasRelativePosition() ? "pre-apply-client-state-missing-for-relative-correction"
                                : pendingPhaseAttempt && !candidatePacketImmediatelyPreceded ? "preceding-packet-was-not-this-phase-candidate" : "no-pending-phase-candidate",
                        record.precedingMovement == null ? "none" : record.precedingMovement.describe()));
            }
            addDiagnostic(String.format(Locale.ROOT,
                    "CORRECTION_POST_APPLY_STATE #%d tick=%d localNow=%.4f,%.4f,%.4f rot=%.2f,%.2f rawTarget=%.4f,%.4f,%.4f relative=%s",
                    record.sequence, player.ticksExisted, player.posX, player.posY, player.posZ,
                    player.rotationYaw, player.rotationPitch, record.rawX, record.rawY, record.rawZ, record.relativeFlags));
        }
    }

    public void onChatMessage(String message) {
        if (!captureEnabled || message == null) return;
        String plain = TextFormatting.getTextWithoutFormattingCodes(message);
        if (plain == null) return;
        String upper = plain.toUpperCase(Locale.ROOT);
        if (upper.contains("MOVING_PASSABLE") || upper.contains("MOVING_SURVIVALFLY") || upper.contains("MOVING_CREATIVEFLY")) {
            addDiagnostic("SERVER_ALERT tick=" + currentTick() + " text=" + plain.replace('\n', ' ').replace('\r', ' '));
            Matcher matcher = NCP_ALERT_PATTERN.matcher(plain);
            CorrectionRecord nearest = lastCorrectionRecord;
            long now = System.currentTimeMillis();
            if (matcher.find()) {
                addDiagnostic(String.format(Locale.ROOT,
                        "NCP_CHECK_VL_OBSERVED atMs=%d tick=%d check=%s vl=%s delta=%s nearestPriorCorrection=%s association=temporal-only correctionAgeMs=%d correctionRaw=%s correctionMovement=%s",
                        now, currentTick(), matcher.group(1), matcher.group(2), matcher.group(3),
                        nearest == null ? "none" : "#" + nearest.sequence,
                        nearest == null ? -1L : Math.max(0L, now - nearest.receivedAtMillis),
                        nearest == null ? "none" : String.format(Locale.ROOT, "%.4f,%.4f,%.4f", nearest.rawX, nearest.rawY, nearest.rawZ),
                        nearest == null || nearest.precedingMovement == null ? "none" : nearest.precedingMovement.describe()));
            }
            String username = mc.getSession() == null ? null : mc.getSession().getUsername();
            if (pendingPhaseAttempt && !pendingAttemptAlerted && username != null
                    && plain.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
                pendingAttemptAlerted = true;
                addDiagnostic("PHASE_ALERT_FEEDBACK tick=" + currentTick()
                        + " own-player movement alert; advancing diagonal bias once for next attempt");
                advanceBiasAfterRejectedAttempt();
            }
        }
    }

    /** Read-only correlation point for detached-camera transitions. */
    public static synchronized void recordFreecamTransition(boolean enabled,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        freecamActive = enabled;
        if (!captureEnabled) return;
        addDiagnostic(String.format(Locale.ROOT,
                "FREECAM_%s tick=%d player=%.4f,%.4f,%.4f camera=%.4f,%.4f,%.4f inputSuppression=%s",
                enabled ? "ENABLE" : "DISABLE", sampledTick, playerX, playerY, playerZ,
                cameraX, cameraY, cameraZ, enabled));
    }

    /** Records the opt-in Freecam camera-to-player handoff experiment. */
    public static synchronized void recordFreecamHandoff(double targetX, double targetY, double targetZ,
            boolean requested, boolean sent) {
        if (requested && sent) {
            net.minecraft.client.entity.EntityPlayerSP player = net.minecraft.client.Minecraft.getMinecraft().player;
            if (player != null) lastFreecamHandoffPlayerTick = player.ticksExisted;
        }
        if (!captureEnabled) return;
        addDiagnostic(String.format(Locale.ROOT,
                "FREECAM_HANDOFF tick=%d requested=%s target=%.4f,%.4f,%.4f packetSent=%s",
                sampledTick, requested, targetX, targetY, targetZ, sent));
    }

    public static boolean isCapturingDiagnostics() {
        return captureEnabled;
    }

    /** Samples the detached camera path and any collision blocks intersecting its player-sized box. */
    public static synchronized void recordFreecamSample(int tick, int dimension,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ,
            float forward, float strafe, boolean jump, boolean sneak, String intersectedBlocks) {
        if (!captureEnabled) return;
        addDiagnostic(String.format(Locale.ROOT,
                "FREECAM_SAMPLE tick=%d dimension=%d player=%.4f,%.4f,%.4f camera=%.4f,%.4f,%.4f input=%.2f,%.2f jump=%s sneak=%s cameraCollision=%s",
                tick, dimension, playerX, playerY, playerZ, cameraX, cameraY, cameraZ,
                forward, strafe, jump, sneak, intersectedBlocks == null ? "unknown" : intersectedBlocks));
    }

    public static synchronized void recordFreecamDrag(double targetX, double targetY, double targetZ,
            int tick, boolean sent) {
        if (!captureEnabled) return;
        addDiagnostic(String.format(Locale.ROOT,
                "FREECAM_DRAG tick=%d target=%.4f,%.4f,%.4f packetSent=%s",
                tick, targetX, targetY, targetZ, sent));
    }

    public static synchronized List<String> recentDiagnostics() {
        return Collections.unmodifiableList(new ArrayList<String>(DIAGNOSTICS));
    }

    public static synchronized void clearDiagnostics() {
        DIAGNOSTICS.clear();
        outgoingMovementPackets = 0L;
        serverCorrections = 0L;
        correctionSequence = 0L;
        pendingCorrections.clear();
        lastMovementRecord = null;
        lastCorrectionRecord = null;
        precedingMovementEvent = "none";
        precedingMovementAt = 0L;
    }

    public static synchronized String diagnosticStatus(Phase phase) {
        return "Movement trace=" + (captureEnabled ? "ON" : "OFF") + " outgoing=" + outgoingMovementPackets
                + " corrections=" + serverCorrections + " entries=" + DIAGNOSTICS.size() + "/" + MAX_EVENTS;
    }

    /** Export retained records as timestamped CSV lines; filename accepts only letters, digits, _ and -. */
    public static synchronized File exportDiagnostics(String requestedName) throws IOException {
        if (requestedName == null || !requestedName.matches("[A-Za-z0-9_-]{1,32}")) return null;
        File directory = new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "Backspace/diagnostics");
        if (!directory.exists() && !directory.mkdirs()) return null;
        File output = new File(directory, requestedName + ".csv");
        List<String> lines = new ArrayList<String>();
        lines.add("timestamp_ms,event");
        for (String entry : DIAGNOSTICS) {
            int split = entry.indexOf(' ');
            if (split <= 0) continue;
            String timestamp = entry.substring(0, split);
            String event = entry.substring(split + 1).replace("\"", "\"\"");
            lines.add(timestamp + ",\"" + event + "\"");
        }
        Files.write(output.toPath(), lines, StandardCharsets.UTF_8);
        return output;
    }

    private static synchronized void addDiagnostic(String line) {
        DIAGNOSTICS.addLast(System.currentTimeMillis() + " " + line);
        while (DIAGNOSTICS.size() > MAX_EVENTS) DIAGNOSTICS.removeFirst();
    }

    private static int currentTick() {
        return sampledTick;
    }

    public static final class CorrectionRecord {
        public final long sequence;
        public final long receivedAtMillis;
        public final int receivedTick;
        public final double rawX, rawY, rawZ;
        public final float rawYaw, rawPitch;
        public final Set<String> relativeFlags;
        public final MovementRecord precedingMovement;
        public volatile boolean preApplyCaptured;
        public volatile double clientX, clientY, clientZ;
        public volatile float clientYaw, clientPitch;
        public volatile int clientTick;
        private volatile boolean phaseAttemptMatched;

        private CorrectionRecord(long sequence, long receivedAtMillis, int receivedTick,
                double rawX, double rawY, double rawZ, float rawYaw, float rawPitch,
                Set<?> relativeFlags, MovementRecord precedingMovement) {
            this.sequence = sequence; this.receivedAtMillis = receivedAtMillis; this.receivedTick = receivedTick;
            this.rawX = rawX; this.rawY = rawY; this.rawZ = rawZ; this.rawYaw = rawYaw; this.rawPitch = rawPitch;
            Set<String> flags = new HashSet<String>();
            if (relativeFlags != null) for (Object flag : relativeFlags) flags.add(String.valueOf(flag));
            this.relativeFlags = java.util.Collections.unmodifiableSet(flags);
            this.precedingMovement = precedingMovement;
        }

        private void captureClientPreApply(double x, double y, double z, float yaw, float pitch, int tick) {
            clientX=x; clientY=y; clientZ=z; clientYaw=yaw; clientPitch=pitch; clientTick=tick; preApplyCaptured=true;
        }

        /** Returns the resolved correction target; relative corrections require the pre-apply snapshot. */
        private double[] resolvePosition() {
            boolean relativePosition = hasRelativePosition();
            if (relativePosition && !preApplyCaptured) return null;
            double baseX=preApplyCaptured?clientX:0D, baseY=preApplyCaptured?clientY:0D, baseZ=preApplyCaptured?clientZ:0D;
            return new double[]{relativeFlags.contains("X")?baseX+rawX:rawX,
                    relativeFlags.contains("Y")?baseY+rawY:rawY,
                    relativeFlags.contains("Z")?baseZ+rawZ:rawZ};
        }

        private boolean hasRelativePosition() {
            return relativeFlags.contains("X") || relativeFlags.contains("Y") || relativeFlags.contains("Z");
        }
    }

    public static final class MovementRecord {
        public final long sequence, timestampMillis;
        public final int tick;
        public final String packetType;
        public final boolean includesPosition, includesRotation, onGround;
        public final double x, y, z;
        public final float yaw, pitch;

        private MovementRecord(long sequence, long timestampMillis, int tick, String packetType,
                boolean includesPosition, boolean includesRotation, double x, double y, double z,
                float yaw, float pitch, boolean onGround) {
            this.sequence=sequence; this.timestampMillis=timestampMillis; this.tick=tick; this.packetType=packetType;
            this.includesPosition=includesPosition; this.includesRotation=includesRotation;
            this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch; this.onGround=onGround;
        }

        private boolean matchesPosition(double targetX, double targetY, double targetZ) {
            return includesPosition && Math.abs(x-targetX)<0.00051D && Math.abs(y-targetY)<0.00051D
                    && Math.abs(z-targetZ)<0.00051D;
        }

        private String describe() {
            return String.format(Locale.ROOT,"OUT #%d atMs=%d tick=%d type=%s pos=%s rot=%s onGround=%s",
                    sequence,timestampMillis,tick,packetType,
                    includesPosition?String.format(Locale.ROOT,"%.4f,%.4f,%.4f",x,y,z):"unchanged",
                    includesRotation?String.format(Locale.ROOT,"%.2f,%.2f",yaw,pitch):"unchanged",onGround);
        }
    }
}
