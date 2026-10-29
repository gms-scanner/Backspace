package dev.goldforge.module.impl;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-thread observations of server block updates, never a claim about hidden blocks. */
public final class RecentSurroundBlocks {
    private static final int LIMIT = 128;
    private static final long MAX_AGE_MS = 5000L;
    private static final Map<BlockPos, Observation> observations = new LinkedHashMap<BlockPos, Observation>();
    private static World world;
    private static long revision;

    private RecentSurroundBlocks() {}

    public static void onServerUpdate(BlockPos pos, IBlockState state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || pos == null || state == null) return;
        if (world != mc.world) { observations.clear(); world = mc.world; }
        if (!mc.world.isBlockLoaded(pos)) return;
        if (state.getMaterial().isReplaceable()) {
            observations.remove(pos);
            return;
        }
        // A surround block can be broken and replaced between client ticks.
        // Treat every authoritative solid update as a fresh observation.
        observations.remove(pos);
        observations.put(pos.toImmutable(), new Observation(System.currentTimeMillis(), ++revision));
        trim();
    }

    public static long revision(BlockPos pos, int windowTicks) {
        Observation observation = observations.get(pos);
        if (observation == null || System.currentTimeMillis() - observation.time >
                Math.min(MAX_AGE_MS, Math.max(1, windowTicks) * 50L)) return 0L;
        return observation.revision;
    }

    public static void clear() { observations.clear(); world = null; }

    private static void trim() {
        while (observations.size() > LIMIT) {
            BlockPos oldest = observations.keySet().iterator().next();
            observations.remove(oldest);
        }
    }

    private static final class Observation {
        final long time, revision;
        Observation(long time, long revision) { this.time = time; this.revision = revision; }
    }
}
