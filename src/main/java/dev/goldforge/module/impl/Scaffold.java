package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.MovementManager;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Builds a supported path under the player's feet and ahead of edges. */
public final class Scaffold extends Module {
    public int actionDelay = 0;
    public int ncpProfile = 0;
    public int targetY = -1;
    public int predictionTicks = 2;
    public int placementTimeout = 20;
    public int retryLimit = 2;
    public int autoRefill = 1;
    public int descentSpeed = 28;

    private int cooldown;
    private int retries;
    private BlockPos retryTarget;
    private BlockPos pending;
    private int pendingTicks;
    private boolean pendingAccepted;
    private boolean pendingDenied;
    private int descentBaseY = Integer.MIN_VALUE;
    private final Set<BlockPos> unverified = new HashSet<BlockPos>();

    public Scaffold() {
        super("Scaffold", "Builds a verified, supported path beneath you.", Category.MOVEMENT, 0);
        addIntSetting("Action delay (ticks)", 0, 10, 1, () -> actionDelay, value -> actionDelay = value);
        addIntSetting("NCP timing (0/1)", 0, 1, 1, () -> ncpProfile, value -> ncpProfile = value);
        addIntSetting("Target Y (-1=follow)", -1, 320, 1, () -> targetY, value -> targetY = value);
        addIntSetting("Look ahead (ticks)", 0, 4, 1, () -> predictionTicks, value -> predictionTicks = value);
        addIntSetting("Confirm timeout (ticks)", 4, 80, 1, () -> placementTimeout, value -> placementTimeout = value);
        addIntSetting("Retry limit", 0, 5, 1, () -> retryLimit, value -> retryLimit = value);
        addIntSetting("Auto refill (0/1)", 0, 1, 1, () -> autoRefill, value -> autoRefill = value);
        addIntSetting("Crouch descent speed", 10, 50, 2, () -> descentSpeed, value -> descentSpeed = value);
    }

    @Override protected void onEnable() { clear(); }
    @Override protected void onDisable() { clear(); }

    private void clear() {
        cooldown = retries = pendingTicks = 0;
        retryTarget = pending = null;
        pendingAccepted = pendingDenied = false;
        descentBaseY = Integer.MIN_VALUE;
        unverified.clear();
    }

    @Override public void tick() {
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.player.isDead) {
            clear();
            return;
        }
        unverified.removeIf(pos -> !pos.equals(pending) && mc.world.isBlockLoaded(pos)
                && isReplaceable(pos));
        if (mc.player.onGround) descentBaseY = MathHelper.floor(mc.player.getEntityBoundingBox().minY);
        enforceSafety();
        if (mc.currentScreen != null) return;
        if (cooldown > 0) --cooldown;
        if (pending != null) {
            if (!mc.world.isBlockLoaded(pending)) { clear(); return; }
            ++pendingTicks;
            if (pendingAccepted && isPlaced(pending)) {
                pending = null;
                pendingTicks = 0;
                pendingAccepted = pendingDenied = false;
                retryTarget = null;
                retries = 0;
                return;
            }
            if (!pendingDenied && pendingTicks < Math.max(4, placementTimeout)) return;
            // The local block can be a ghost. Only a solid server response confirms it.
            retryTarget = pending;
            pending = null;
            pendingTicks = 0;
            pendingAccepted = pendingDenied = false;
            ++retries;
            cooldown = Math.max(cooldown, 2);
        }
        if (cooldown > 0) return;
        if (retryTarget != null && unverified.contains(retryTarget)
                && (!mc.world.isBlockLoaded(retryTarget) || !isReplaceable(retryTarget))) return;

        boolean descending = wantsDescent();
        int baseY = descentBaseY == Integer.MIN_VALUE
                ? MathHelper.floor(mc.player.getEntityBoundingBox().minY) : descentBaseY;
        int y = descending ? baseY - 2 : targetY < 0 ? baseY - 1 : targetY;
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

    /** The server responds with the attempted target block's authoritative state. */
    public void onServerBlockChange(BlockPos pos, IBlockState state) {
        if (!enabled || pos == null || state == null || !unverified.contains(pos)) return;
        boolean solid = !state.getMaterial().isReplaceable() && state.isFullBlock();
        if (solid) unverified.remove(pos);
        if (pos.equals(pending)) {
            pendingAccepted = solid;
            pendingDenied = !solid;
        }
    }

    @Override public void onInputUpdate(InputUpdateEvent event) {
        if (mc.player == null || mc.world == null || event.getEntityPlayer() != mc.player
                || mc.player.isRiding() || mc.player.capabilities.isFlying) return;
        net.minecraft.util.MovementInput input = event.getMovementInput();
        if (input.moveForward == 0F && input.moveStrafe == 0F) return;
        AxisAlignedBB next = predictedBox(input.moveForward, input.moveStrafe);
        if (wantsDescent() && hasTrustedSupport(next, 1D)) {
            // Release vanilla edge-sneak only when a lower, server-verified landing exists.
            input.sneak = false;
            return;
        }
        if (!hasTrustedSupport(next, 0D)) {
            input.moveForward = input.moveStrafe = 0F;
            mc.player.motionX = mc.player.motionZ = 0D;
        }
    }

    private void enforceSafety() {
        if (mc.player.isRiding() || mc.player.capabilities.isFlying) return;
        net.minecraft.util.MovementInput input = mc.player.movementInput;
        if (input == null) return;
        AxisAlignedBB next = predictedBox(input.moveForward, input.moveStrafe);
        boolean descending = wantsDescent() && hasTrustedSupport(next, 1D);
        if (!descending && !hasTrustedSupport(next, 0D)) {
            mc.player.motionX = mc.player.motionZ = 0D;
            MovementManager.requestHorizontalVelocity(this, 0D, 0D, 1000);
        }
        if (descending && !mc.player.onGround && hasTrustedSupport(mc.player.getEntityBoundingBox(), 1D)) {
            mc.player.motionY = Math.min(mc.player.motionY, -descentSpeed / 100D);
        }
    }

    private boolean wantsDescent() {
        if (descentBaseY == Integer.MIN_VALUE) return false;
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        return key >= 0 ? Keyboard.isKeyDown(key)
                : key <= -100 && key >= -115 && Mouse.isButtonDown(key + 100);
    }

    private AxisAlignedBB predictedBox(float forward, float strafe) {
        double yaw = Math.toRadians(mc.player.rotationYaw);
        double dx = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * .30D + mc.player.motionX;
        double dz = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * .30D + mc.player.motionZ;
        dx = MathHelper.clamp(dx, -.75D, .75D);
        dz = MathHelper.clamp(dz, -.75D, .75D);
        return mc.player.getEntityBoundingBox().offset(dx, 0D, dz);
    }

    private boolean hasTrustedSupport(AxisAlignedBB box, double drop) {
        AxisAlignedBB probe = new AxisAlignedBB(box.minX + .03D, box.minY - drop - .13D,
                box.minZ + .03D, box.maxX - .03D, box.minY - drop + .03D, box.maxZ - .03D);
        for (int x = MathHelper.floor(probe.minX); x <= MathHelper.floor(probe.maxX); ++x)
            for (int y = MathHelper.floor(probe.minY); y <= MathHelper.floor(probe.maxY); ++y)
                for (int z = MathHelper.floor(probe.minZ); z <= MathHelper.floor(probe.maxZ); ++z) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.world.isBlockLoaded(pos) || unverified.contains(pos)) continue;
                    AxisAlignedBB collision = mc.world.getBlockState(pos).getCollisionBoundingBox(mc.world, pos);
                    if (collision != null && collision.offset(pos).intersects(probe)) return true;
                }
        return false;
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
            if (!mc.world.isBlockLoaded(support) || unverified.contains(support)) continue;
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
            pending = placement.target.toImmutable();
            pendingTicks = 0;
            pendingAccepted = pendingDenied = false;
            unverified.add(pending);
            EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world,
                    placement.support, placement.face, placement.hit, EnumHand.MAIN_HAND);
            if (result == EnumActionResult.SUCCESS) {
                mc.player.swingArm(EnumHand.MAIN_HAND);
            } else {
                unverified.remove(pending);
                pending = null;
                pendingAccepted = pendingDenied = false;
                retryTarget = placement.target.toImmutable();
                ++retries;
            }
            cooldown = Math.max(actionDelay, ncpProfile != 0 ? 2 : 0);
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
