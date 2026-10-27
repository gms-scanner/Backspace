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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
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
    public int allowDescent = 0;
    public int maxPending = 3;

    private int cooldown;
    private int retries;
    private BlockPos retryTarget;
    private final Map<BlockPos, Attempt> inFlight = new LinkedHashMap<BlockPos, Attempt>();
    private float intendedForward, intendedStrafe;
    private int descentBaseY = Integer.MIN_VALUE;
    private final Set<BlockPos> unverified = new HashSet<BlockPos>();

    public Scaffold() {
        super("Scaffold", "Places bridge blocks ahead while stopping at unconfirmed edges without sneaking.", Category.MOVEMENT, 0);
        addIntSetting("Action delay (ticks)", 0, 10, 1, () -> actionDelay, value -> actionDelay = value);
        addIntSetting("NCP timing (0/1)", 0, 1, 1, () -> ncpProfile, value -> ncpProfile = value);
        addIntSetting("Target Y (-1=follow)", -1, 320, 1, () -> targetY, value -> targetY = value);
        addIntSetting("Look ahead (ticks)", 0, 4, 1, () -> predictionTicks, value -> predictionTicks = value);
        addIntSetting("Confirm timeout (ticks)", 4, 80, 1, () -> placementTimeout, value -> placementTimeout = value);
        addIntSetting("Retry limit", 0, 5, 1, () -> retryLimit, value -> retryLimit = value);
        addIntSetting("Auto refill (0/1)", 0, 1, 1, () -> autoRefill, value -> autoRefill = value);
        addIntSetting("Pending placements", 1, 5, 1, () -> maxPending, value -> maxPending = value);
        addIntSetting("Manual crouch descent (0/1)", 0, 1, 1, () -> allowDescent, value -> allowDescent = value);
        addIntSetting("Crouch descent speed", 10, 50, 2, () -> descentSpeed, value -> descentSpeed = value);
    }

    @Override protected void onEnable() { clear(); }
    @Override protected void onDisable() { clear(); }

    private void clear() {
        cooldown = retries = 0;
        retryTarget = null;
        inFlight.clear();
        intendedForward = intendedStrafe = 0F;
        descentBaseY = Integer.MIN_VALUE;
        unverified.clear();
    }

    @Override public void tick() {
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.player.isDead) {
            clear();
            return;
        }
        unverified.removeIf(pos -> !inFlight.containsKey(pos)
                && (!mc.world.isBlockLoaded(pos) || isReplaceable(pos)
                || pos.distanceSq(mc.player.posX, mc.player.posY, mc.player.posZ) > 1024D));
        if (mc.player.onGround) descentBaseY = MathHelper.floor(mc.player.getEntityBoundingBox().minY);
        updatePending();
        enforceSafety();
        if (mc.currentScreen != null) return;
        if (cooldown > 0) --cooldown;
        if (cooldown > 0 || inFlight.size() >= maxPending) return;
        boolean descending = wantsDescent();
        int baseY = descentBaseY == Integer.MIN_VALUE
                ? MathHelper.floor(mc.player.getEntityBoundingBox().minY) : descentBaseY;
        int y = descending ? baseY - 2 : targetY < 0 ? baseY - 1 : targetY;
        for (BlockPos target : candidates(y)) {
            if (!mc.world.isBlockLoaded(target) || !isReplaceable(target) || unverified.contains(target)) continue;
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

    private void updatePending() {
        Iterator<Map.Entry<BlockPos, Attempt>> iterator = inFlight.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Attempt> entry = iterator.next();
            BlockPos pos = entry.getKey();
            Attempt attempt = entry.getValue();
            if (!mc.world.isBlockLoaded(pos)) { iterator.remove(); continue; }
            if (attempt.accepted && isPlaced(pos)) {
                unverified.remove(pos);
                iterator.remove();
                if (pos.equals(retryTarget)) { retryTarget = null; retries = 0; }
                continue;
            }
            if (attempt.denied || ++attempt.ticks >= Math.max(4, placementTimeout)) {
                // A locally predicted block remains untrusted until the server corrects it.
                iterator.remove();
                retryTarget = pos;
                retries++;
                cooldown = Math.max(cooldown, 1);
            }
        }
    }

    /** The server responds with the attempted target block's authoritative state. */
    public void onServerBlockChange(BlockPos pos, IBlockState state) {
        if (!enabled || pos == null || state == null) return;
        Attempt attempt = inFlight.get(pos);
        if (attempt == null) return;
        boolean solid = !state.getMaterial().isReplaceable() && state.isFullBlock();
        attempt.accepted = solid;
        attempt.denied = !solid;
    }

    @Override public void onInputUpdate(InputUpdateEvent event) {
        if (mc.player == null || mc.world == null || event.getEntityPlayer() != mc.player
                || mc.player.isRiding() || mc.player.capabilities.isFlying) return;
        net.minecraft.util.MovementInput input = event.getMovementInput();
        intendedForward = input.moveForward;
        intendedStrafe = input.moveStrafe;
        if (intendedForward == 0F) intendedForward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1F : 0F)
                - (mc.gameSettings.keyBindBack.isKeyDown() ? 1F : 0F);
        if (intendedStrafe == 0F) intendedStrafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1F : 0F)
                - (mc.gameSettings.keyBindRight.isKeyDown() ? 1F : 0F);
        if (input.moveForward == 0F && input.moveStrafe == 0F) return;
        AxisAlignedBB next = predictedBox(input.moveForward, input.moveStrafe);
        if (wantsDescent() && hasTrustedSupport(next, 1D)) return;
        AxisAlignedBB current = mc.player.getEntityBoundingBox();
        double fraction = safeMovementFraction(current, next.minX - current.minX, next.minZ - current.minZ);
        if (fraction < 1D) {
            input.moveForward *= fraction;
            input.moveStrafe *= fraction;
            mc.player.motionX *= fraction;
            mc.player.motionZ *= fraction;
        }
    }

    private void enforceSafety() {
        if (mc.player.isRiding() || mc.player.capabilities.isFlying) return;
        net.minecraft.util.MovementInput input = mc.player.movementInput;
        if (input == null) return;
        AxisAlignedBB next = predictedBox(input.moveForward, input.moveStrafe);
        boolean descending = wantsDescent() && hasTrustedSupport(next, 1D);
        if (!descending) {
            AxisAlignedBB current = mc.player.getEntityBoundingBox();
            double fraction = safeMovementFraction(current, next.minX - current.minX, next.minZ - current.minZ);
            if (fraction < 1D) {
                mc.player.motionX *= fraction;
                mc.player.motionZ *= fraction;
                MovementManager.requestHorizontalVelocity(this, mc.player.motionX, mc.player.motionZ, 1000);
            }
        }
        if (descending && !mc.player.onGround && hasTrustedSupport(mc.player.getEntityBoundingBox(), 1D)) {
            mc.player.motionY = Math.min(mc.player.motionY, -descentSpeed / 100D);
        }
    }

    private boolean wantsDescent() {
        if (allowDescent == 0 || descentBaseY == Integer.MIN_VALUE) return false;
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        return key >= 0 ? Keyboard.isKeyDown(key)
                : key <= -100 && key >= -115 && Mouse.isButtonDown(key + 100);
    }

    private AxisAlignedBB predictedBox(float forward, float strafe) {
        double yaw = Math.toRadians(mc.player.rotationYaw);
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < .001D) return mc.player.getEntityBoundingBox().offset(mc.player.motionX, 0D, mc.player.motionZ);
        double speed = Math.min(.45D, Math.max(.28D * Math.min(1D, length),
                Math.sqrt(mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ)));
        double dx = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) / length * speed;
        double dz = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) / length * speed;
        return mc.player.getEntityBoundingBox().offset(dx, 0D, dz);
    }

    private double safeMovementFraction(AxisAlignedBB current, double dx, double dz) {
        for (int step = 10; step >= 0; --step) {
            double fraction = step / 10D;
            if (hasTrustedSupport(current.offset(dx * fraction, 0D, dz * fraction), 0D)) return fraction;
        }
        return 0D;
    }

    private boolean hasTrustedSupport(AxisAlignedBB box, double drop) {
        AxisAlignedBB probe = new AxisAlignedBB(box.minX + .03D, box.minY - drop - .13D,
                box.minZ + .03D, box.maxX - .03D, box.minY - drop + .03D, box.maxZ - .03D);
        double covered = 0D;
        double required = (probe.maxX - probe.minX) * (probe.maxZ - probe.minZ) * .4D;
        for (int x = MathHelper.floor(probe.minX); x <= MathHelper.floor(probe.maxX); ++x)
            for (int y = MathHelper.floor(probe.minY); y <= MathHelper.floor(probe.maxY); ++y)
                for (int z = MathHelper.floor(probe.minZ); z <= MathHelper.floor(probe.maxZ); ++z) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.world.isBlockLoaded(pos) || unverified.contains(pos)) continue;
                    AxisAlignedBB collision = mc.world.getBlockState(pos).getCollisionBoundingBox(mc.world, pos);
                    if (collision == null) continue;
                    AxisAlignedBB worldBox = collision.offset(pos);
                    if (worldBox.intersects(probe)) {
                        covered += Math.max(0D, Math.min(worldBox.maxX, probe.maxX) - Math.max(worldBox.minX, probe.minX))
                                * Math.max(0D, Math.min(worldBox.maxZ, probe.maxZ) - Math.max(worldBox.minZ, probe.minZ));
                        if (covered >= required) return true;
                    }
                }
        return false;
    }

    private List<BlockPos> candidates(int y) {
        List<BlockPos> result = new ArrayList<BlockPos>(10);
        BlockPos below = new BlockPos(mc.player.posX, y, mc.player.posZ);
        addUnique(result, below);
        float forward = intendedForward;
        float strafe = intendedStrafe;
        double yaw = Math.toRadians(mc.player.rotationYaw);
        double dx = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double dz = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < .01D) {
            dx = mc.player.motionX; dz = mc.player.motionZ;
            length = Math.sqrt(dx * dx + dz * dz);
        }
        if (length < .01D) return result;
        dx /= length; dz /= length;
        BlockPos previous = below;
        double ahead = .65D + Math.max(0, predictionTicks) * .65D;
        for (double distance = .45D; distance <= ahead + .01D; distance += .45D) {
            BlockPos next = new BlockPos(mc.player.posX + dx * distance, y, mc.player.posZ + dz * distance);
            if (next.equals(previous)) continue;
            if (next.getX() != previous.getX() && next.getZ() != previous.getZ()) {
                addUnique(result, Math.abs(dx) >= Math.abs(dz)
                        ? new BlockPos(next.getX(), y, previous.getZ())
                        : new BlockPos(previous.getX(), y, next.getZ()));
            }
            addUnique(result, next);
            previous = next;
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
            if (unverified.contains(support) && (!inFlight.containsKey(support)
                    || inFlight.get(support).denied || !isPlaced(support))) continue;
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
            BlockPos target = placement.target.toImmutable();
            inFlight.put(target, new Attempt());
            unverified.add(target);
            EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world,
                    placement.support, placement.face, placement.hit, EnumHand.MAIN_HAND);
            if (result == EnumActionResult.SUCCESS) {
                mc.player.swingArm(EnumHand.MAIN_HAND);
            } else {
                inFlight.remove(target);
                unverified.remove(target);
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
    private static final class Attempt {
        int ticks;
        boolean accepted, denied;
    }
}
