package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Builds a supported path under the player's feet and ahead of edges. */
public final class Scaffold extends Module {
    public int actionDelay = 1;
    public int ncpProfile = 1;
    public int targetY = -1;
    public int predictionTicks = 2;
    public int placementTimeout = 5;
    public int retryLimit = 2;
    public int autoRefill = 1;

    private int cooldown;
    private int retries;
    private BlockPos retryTarget;
    private BlockPos pending;
    private int pendingTicks;

    public Scaffold() {
        super("Scaffold", "Builds a verified, supported path beneath you.", Category.MOVEMENT, 0);
        addIntSetting("Action delay (ticks)", 0, 10, 1, () -> actionDelay, value -> actionDelay = value);
        addIntSetting("NCP timing (0/1)", 0, 1, 1, () -> ncpProfile, value -> ncpProfile = value);
        addIntSetting("Target Y (-1=follow)", -1, 320, 1, () -> targetY, value -> targetY = value);
        addIntSetting("Look ahead (ticks)", 0, 4, 1, () -> predictionTicks, value -> predictionTicks = value);
        addIntSetting("Confirm timeout (ticks)", 2, 12, 1, () -> placementTimeout, value -> placementTimeout = value);
        addIntSetting("Retry limit", 0, 5, 1, () -> retryLimit, value -> retryLimit = value);
        addIntSetting("Auto refill (0/1)", 0, 1, 1, () -> autoRefill, value -> autoRefill = value);
    }

    @Override protected void onEnable() { clear(); }
    @Override protected void onDisable() { clear(); }

    private void clear() {
        cooldown = retries = pendingTicks = 0;
        retryTarget = pending = null;
    }

    @Override public void tick() {
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.player.isDead) {
            clear();
            return;
        }
        if (mc.currentScreen != null) return;
        if (cooldown > 0) --cooldown;
        if (pending != null) {
            if (!mc.world.isBlockLoaded(pending)) { clear(); return; }
            if (++pendingTicks >= 2 && isPlaced(pending)) {
                pending = null;
                pendingTicks = 0;
                retryTarget = null;
                retries = 0;
                return;
            }
            if (pendingTicks < Math.max(2, placementTimeout)) return;
            // SUCCESS only reports the client's interaction result. Await world confirmation.
            retryTarget = pending;
            pending = null;
            pendingTicks = 0;
            ++retries;
            cooldown = Math.max(cooldown, 2);
        }
        if (cooldown > 0) return;

        int y = targetY < 0 ? MathHelper.floor(mc.player.getEntityBoundingBox().minY) - 1 : targetY;
        for (BlockPos target : candidates(y)) {
            if (!mc.world.isBlockLoaded(target) || !isReplaceable(target)) continue;
            if (retryTarget != null && !retryTarget.equals(target)) {
                retryTarget = null;
                retries = 0;
            }
            if (retryTarget != null && retries > retryLimit) return;
            int slot = findBlockSlot();
            if (slot < 0) return;
            Placement placement = findPlacement(target, slot);
            if (placement == null) continue;
            place(placement, slot);
            return; // At most one interaction per tick.
        }
    }

    private List<BlockPos> candidates(int y) {
        List<BlockPos> result = new ArrayList<BlockPos>(4);
        BlockPos below = new BlockPos(mc.player.posX, y, mc.player.posZ);
        BlockPos ahead = new BlockPos(mc.player.posX + mc.player.motionX * Math.max(0, predictionTicks),
                y, mc.player.posZ + mc.player.motionZ * Math.max(0, predictionTicks));
        addUnique(result, below);
        addUnique(result, ahead);
        // A diagonal destination may need a cardinal bridge cell first.
        if (ahead.getX() != below.getX() && ahead.getZ() != below.getZ()) {
            BlockPos xStep = new BlockPos(ahead.getX(), y, below.getZ());
            BlockPos zStep = new BlockPos(below.getX(), y, ahead.getZ());
            addUnique(result, Math.abs(mc.player.motionX) >= Math.abs(mc.player.motionZ) ? xStep : zStep);
            addUnique(result, Math.abs(mc.player.motionX) >= Math.abs(mc.player.motionZ) ? zStep : xStep);
        }
        return result;
    }

    private static void addUnique(List<BlockPos> list, BlockPos pos) {
        if (!list.contains(pos)) list.add(pos);
    }

    private boolean isReplaceable(BlockPos pos) {
        return mc.world.getBlockState(pos).getMaterial().isReplaceable();
    }

    private boolean isPlaced(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        return !state.getMaterial().isReplaceable() && state.isFullBlock();
    }

    private int findBlockSlot() {
        int selected = mc.player.inventory.currentItem;
        if (validStack(mc.player.inventory.getStackInSlot(selected))) return selected;
        int best = -1;
        int count = -1;
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = mc.player.inventory.getStackInSlot(slot);
            if (validStack(stack) && stack.getCount() > count) {
                best = slot;
                count = stack.getCount();
            }
        }
        if (best >= 0 || autoRefill == 0 || mc.player.openContainer != mc.player.inventoryContainer) return best;
        int empty = -1;
        for (int slot = 0; slot < 9; ++slot) {
            if (mc.player.inventory.getStackInSlot(slot).isEmpty()) { empty = slot; break; }
        }
        if (empty < 0) return -1;
        for (int slot = 9; slot < 36; ++slot) {
            if (!validStack(mc.player.inventory.getStackInSlot(slot))) continue;
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId,
                    slot, empty, ClickType.SWAP, mc.player);
            cooldown = Math.max(cooldown, 1);
            return -1; // Recheck inventory on the next tick.
        }
        return -1;
    }

    private static boolean validStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        IBlockState state = block.getDefaultState();
        return state.isFullBlock() && !state.getMaterial().isReplaceable()
                && !(block instanceof BlockFalling) && !block.hasTileEntity(state)
                && block != Blocks.TNT && block != Blocks.CRAFTING_TABLE;
    }

    private Placement findPlacement(BlockPos target, int slot) {
        ItemStack stack = mc.player.inventory.getStackInSlot(slot);
        ItemBlock item = (ItemBlock) stack.getItem();
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        double reach = mc.playerController.getBlockReachDistance() - .1D;
        Placement best = null;
        for (EnumFacing towardSupport : EnumFacing.values()) {
            BlockPos support = target.offset(towardSupport);
            if (!mc.world.isBlockLoaded(support)) continue;
            IBlockState state = mc.world.getBlockState(support);
            EnumFacing face = towardSupport.getOpposite();
            if (!state.isSideSolid(mc.world, support, face) || state.getBlock().hasTileEntity(state)) continue;
            if (!item.canPlaceBlockOnSide(mc.world, support, face, mc.player, stack)) continue;
            Vec3d hit = InventoryBlocks.hitVec(support, face);
            double distance = eyes.squareDistanceTo(hit);
            if (distance > reach * reach) continue;
            if (best == null || distance < best.distance) best = new Placement(target, support, face, hit, distance);
        }
        return best;
    }

    private void place(Placement placement, int slot) {
        int oldSlot = mc.player.inventory.currentItem;
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        double dx = placement.hit.x - eyes.x;
        double dy = placement.hit.y - eyes.y;
        double dz = placement.hit.z - eyes.z;
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);
        boolean rotated = false;
        try {
            mc.player.inventory.currentItem = slot;
            if (mc.getConnection() != null) {
                PacketManager.send(new CPacketPlayer.Rotation(yaw, pitch, mc.player.onGround), name);
                rotated = true;
            }
            EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world,
                    placement.support, placement.face, placement.hit, EnumHand.MAIN_HAND);
            if (result == EnumActionResult.SUCCESS) {
                mc.player.swingArm(EnumHand.MAIN_HAND);
                pending = placement.target.toImmutable();
                pendingTicks = 0;
            } else {
                retryTarget = placement.target.toImmutable();
                ++retries;
            }
            cooldown = Math.max(actionDelay, ncpProfile != 0 ? 2 : 1);
        } finally {
            mc.player.inventory.currentItem = oldSlot;
            if (rotated && mc.getConnection() != null) {
                PacketManager.send(new CPacketPlayer.Rotation(mc.player.rotationYaw,
                        mc.player.rotationPitch, mc.player.onGround), name);
            }
        }
    }

    private static final class Placement {
        final BlockPos target;
        final BlockPos support;
        final EnumFacing face;
        final Vec3d hit;
        final double distance;

        Placement(BlockPos target, BlockPos support, EnumFacing face, Vec3d hit, double distance) {
            this.target = target;
            this.support = support;
            this.face = face;
            this.hit = hit;
            this.distance = distance;
        }
    }
}

