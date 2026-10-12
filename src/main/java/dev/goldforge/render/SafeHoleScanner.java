package dev.goldforge.render;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only detection of single-block holes, including mixed obsidian/bedrock walls. */
public final class SafeHoleScanner {
    private SafeHoleScanner() {}

    public static boolean isHole(World world, BlockPos feet, boolean bedrockOnly) {
        if (feet.getY() < 1 || feet.getY() > 254
                || !world.isAreaLoaded(feet.add(-1, -1, -1), feet.add(1, 1, 1))
                || !world.getWorldBorder().contains(feet)
                || !world.isAirBlock(feet) || !world.isAirBlock(feet.up())) return false;
        if (!safe(world.getBlockState(feet.down()).getBlock(), bedrockOnly)) return false;
        for (EnumFacing side : EnumFacing.HORIZONTALS)
            if (!safe(world.getBlockState(feet.offset(side)).getBlock(), bedrockOnly)) return false;
        return true;
    }

    public static List<BlockPos> scan(World world, BlockPos center, int radius, int vertical, boolean bedrockOnly) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        int r = Math.max(2, Math.min(24, radius)), h = Math.max(1, Math.min(8, vertical));
        for (BlockPos pos : BlockPos.getAllInBox(center.add(-r, -h, -r), center.add(r, h, r))) {
            if (pos.distanceSq(center) <= r * r && isHole(world, pos, bedrockOnly)) result.add(pos.toImmutable());
        }
        result.sort(Comparator.comparingDouble(p -> p.distanceSq(center)));
        return new ArrayList<BlockPos>(result.subList(0, Math.min(128, result.size())));
    }

    private static boolean safe(Block block, boolean bedrockOnly) {
        return block == Blocks.BEDROCK || (!bedrockOnly && block == Blocks.OBSIDIAN);
    }
}
