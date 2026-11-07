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
        require(LavaCast.estimatedFlowTicks(200,64,30,3)==4200,"height must extend overworld flow estimate");
        require(LavaCast.estimatedFlowTicks(200,64,10,7)==1440,"Nether rate and spread must be included");
        require(LavaCast.estimatedFlowTicks(76,60,30,3)-LavaCast.estimatedFlowTicks(74,60,30,3)==60,
                "each two-block climb must extend the flow estimate by two lava ticks");
        LavaCast.FlowTracker falling=new LavaCast.FlowTracker(origin,0,100);
        falling.observe(1,sample(origin,8),true); falling.stabilizeIfUnchanged(50,10);
        require(falling.status==LavaCast.FlowStatus.TRACKING,"falling lava must not count as source");
        LavaCast.FlowTracker unloaded=new LavaCast.FlowTracker(origin,0,100);
        unloaded.observe(1,sample(origin,0),false);
        require(unloaded.status==LavaCast.FlowStatus.INVALID,"unloaded flow must not finish");
        require(LavaCast.estimatedSurfaceFlowTicks(100,64,72,30,3)>LavaCast.estimatedFlowTicks(100,64,30,3),"stepped slopes must include horizontal travel");
        require(LavaCast.estimatedSurfaceFlowTicks(100,64,10,30,3)==LavaCast.estimatedFlowTicks(100,64,30,3),"partial paths must not shorten height estimate");
        predictionCannotBlockStableFootprint(origin);
        growthResetsSettling(origin);
        stableFlow(origin);
        continuouslyChangingFlowTimesOut(origin);
        replacedSourceIsInvalid(origin);
        System.out.println("LavaCast flow harness: PASS");
    }

    private static void predictionCannotBlockStableFootprint(BlockPos origin) {
        LavaCast.FlowTracker t=new LavaCast.FlowTracker(origin,0,1000);
        for(int tick=1;tick<=21;tick++)
            t.observeCompleteScan(tick,sample(origin,0,origin.east(),tick%2+2),true,true,10,20);
        require(t.status==LavaCast.FlowStatus.STABILIZED,"false growth prediction and level churn must not prevent water stage");
        LavaCast.FlowTracker fast=new LavaCast.FlowTracker(origin,0,1000);
        for(int tick=1;tick<=80;tick++)fast.observeCompleteScan(tick,sample(origin,0),true,true,160,80);
        require(fast.status==LavaCast.FlowStatus.TRACKING,"edge verification must honor configured interval");
        fast.observeCompleteScan(81,sample(origin,0),true,true,160,80);
        require(fast.status==LavaCast.FlowStatus.STABILIZED,"edge verification finishes in 80 ticks instead of 320");
        LavaCast.FlowTracker growing=new LavaCast.FlowTracker(origin,0,1000);
        for(int tick=1;tick<=40;tick++)
            growing.observeCompleteScan(tick,sample(origin,0,origin.east(tick),2),true,true,10,20);
        require(growing.status==LavaCast.FlowStatus.TRACKING,"equal block counts with moving edges must keep waiting");
        LavaCast.FlowTracker partial=new LavaCast.FlowTracker(origin,0,1000);
        for(int tick=1;tick<=40;tick++)partial.observeCompleteScan(tick,sample(origin,0),false,false,10,20);
        require(partial.status==LavaCast.FlowStatus.TRACKING,"partial scans cannot finish a layer");
        LavaCast.FlowTracker stable=new LavaCast.FlowTracker(origin,0,1000);
        for(int tick=1;tick<=11;tick++)stable.observeCompleteScan(tick,sample(origin,0),true,false,10,20);
        require(stable.status==LavaCast.FlowStatus.STABILIZED,"stable complete scans should finish after one quiet window");
    }

    private static void growthResetsSettling(BlockPos origin) {
        LavaCast.FlowTracker tracker=new LavaCast.FlowTracker(origin,0,1000);
        tracker.observe(10,sample(origin,0),true);
        tracker.stabilizeIfUnchanged(15,10);
        require(tracker.status==LavaCast.FlowStatus.TRACKING,"first quiet moment must not finish flow");
        tracker.observe(18,sample(origin,0,origin.east(),2),true);
        tracker.stabilizeIfUnchanged(22,10);
        require(tracker.status==LavaCast.FlowStatus.TRACKING,"new outward growth resets settling");
        tracker.observe(28,sample(origin,0,origin.east(),2),true);
        tracker.stabilizeIfUnchanged(28,10);
        require(tracker.status==LavaCast.FlowStatus.STABILIZED,"finished footprint must not wait for unrelated height estimate");
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
