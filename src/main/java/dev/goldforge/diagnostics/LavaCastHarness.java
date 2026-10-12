package dev.goldforge.diagnostics;

import dev.goldforge.module.impl.LavaCast;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Offline checks for LavaCast's pure world-state tracker. Run with gradle lavaCastHarness. */
public final class LavaCastHarness {
    private LavaCastHarness() {}

    public static void main(String[] args) {
        BlockPos origin = new BlockPos(8, 64, 8);
        stableFlow(origin);
        continuouslyChangingFlowTimesOut(origin);
        replacedSourceIsInvalid(origin);
        System.out.println("LavaCast flow harness: PASS");
    }

    private static void stableFlow(BlockPos origin) {
        LavaCast.FlowTracker tracker = new LavaCast.FlowTracker(origin, 0, 40);
        Map<BlockPos, Integer> sample = sample(origin, 0);
        tracker.observe(1, sample, true);
        tracker.stabilizeIfUnchanged(4, 3);
        require(tracker.status == LavaCast.FlowStatus.STABILIZED, "stable source must stabilize");
        require(tracker.sourceBlock, "level zero must be identified as a source");
    }

    private static void continuouslyChangingFlowTimesOut(BlockPos origin) {
        LavaCast.FlowTracker tracker = new LavaCast.FlowTracker(origin, 0, 6);
        for (int tick = 1; tick <= 6; tick++) {
            tracker.observe(tick, sample(origin, 0, origin.east(), tick & 7), true);
            tracker.stabilizeIfUnchanged(tick, 3);
        }
        require(tracker.status == LavaCast.FlowStatus.TIMED_OUT, "changing flow must time out rather than stabilize");
    }

    private static void replacedSourceIsInvalid(BlockPos origin) {
        LavaCast.FlowTracker tracker = new LavaCast.FlowTracker(origin, 0, 30);
        tracker.observe(1, sample(origin, 0), true);
        tracker.observe(2, sample(origin.east(), 1), true);
        require(tracker.status == LavaCast.FlowStatus.INVALID, "replaced source must invalidate the tracker");
    }

    private static Map<BlockPos, Integer> sample(BlockPos first, int firstLevel, Object... additional) {
        Map<BlockPos, Integer> result = new LinkedHashMap<BlockPos, Integer>();
        result.put(first, firstLevel);
        for (int i = 0; i < additional.length; i += 2) result.put((BlockPos) additional[i], (Integer) additional[i + 1]);
        return result;
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
