package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds a conservative lava-casting cycle using only ordinary 1.12 controller actions.
 * Every transition waits for inventory or block-state confirmation from the local world.
 */
public final class LavaCast extends Module {
    private static final Logger LOGGER = LogManager.getLogger("Backspace/LavaCast");

    public int flowStabilityTicks = 12;
    public int maxFlowTrackingTicks = 120;
    public int actionDelayTicks = 2;
    public int placementRetryLimit = 3;
    public int continueAfterFlowTimeout = 1;
    public int debugLogging = 0;

    private State state = State.PREPARE;
    private int ticks, stateEnteredTick, nextActionTick, retries, collectRetries;
    private int savedHotbarSlot = -1, dimension = Integer.MIN_VALUE;
    private int lavaBucketsBeforeCollect, emptyBucketsBeforeCollect;
    private boolean collectReturnsToPrepare;
    private FlowTracker flow;

    // The names are deliberate: each target in the casting geometry remains explicit.
    private BlockPos lavaPos, lowerBlockPos, airGapPos, upperBlockPos, nextLavaPos;

    private enum State {
        IDLE, PREPARE, PLACE_LAVA, WAIT_FLOW, COLLECT_LAVA, VERIFY_COLLECT,
        JUMP, PLACE_LOWER, VERIFY_LOWER, PLACE_UPPER, VERIFY_UPPER,
        BREAK_LOWER, PLACE_NEXT_LAVA
    }

    public LavaCast() {
        super("LavaCast", "Tracks lava flow and automates the casting sequence.", Category.WORLD, 0);
        addIntSetting("Flow stability ticks", 4, 60, 1, () -> flowStabilityTicks, value -> flowStabilityTicks = value);
        addIntSetting("Maximum flow tracking", 20, 400, 10, () -> maxFlowTrackingTicks, value -> maxFlowTrackingTicks = value);
        addIntSetting("Action delay (ticks)", 0, 20, 1, () -> actionDelayTicks, value -> actionDelayTicks = value);
        addIntSetting("Placement retry limit", 1, 8, 1, () -> placementRetryLimit, value -> placementRetryLimit = value);
        addIntSetting("Continue after timeout (0/1)", 0, 1, 1, () -> continueAfterFlowTimeout, value -> continueAfterFlowTimeout = value);
        addIntSetting("Debug logging (0/1)", 0, 1, 1, () -> debugLogging, value -> debugLogging = value);
    }

    @Override protected void onEnable() {
        resetCycle();
        if (mc.player != null) {
            savedHotbarSlot = mc.player.inventory.currentItem;
            dimension = mc.player.dimension;
        }
        transition(State.PREPARE, "enabled");
    }

    @Override public void tick() {
        ticks++;
        if (!usableWorld()) { if (state != State.IDLE) pause("player, world, or controller unavailable"); return; }
        if (mc.player.dimension != dimension) { dimension = mc.player.dimension; resetCycle(); transition(State.IDLE, "dimension changed"); return; }
        if (mc.currentScreen != null) return;

        switch (state) {
            case IDLE:
                if (readyForAction()) {
                    if (planCycle()) transition(State.PLACE_LAVA, "valid position found");
                    else nextActionTick = ticks + 20;
                }
                break;
            case PREPARE:
                if (!planCycle()) { pause("no supported position near the player"); return; }
                transition(State.PLACE_LAVA, "geometry prepared");
                break;
            case PLACE_LAVA:
                if (!readyForAction()) return;
                if (placeLavaAt(lavaPos)) beginFlowTracking(false);
                else retryAction("lava placement was rejected", State.PLACE_LAVA);
                break;
            case WAIT_FLOW:
                observeFlow();
                break;
            case COLLECT_LAVA:
                if (!readyForAction()) return;
                collectLava();
                break;
            case VERIFY_COLLECT:
                verifyCollection();
                break;
            case JUMP:
                if (!readyForAction()) return;
                if (!mc.player.onGround) {
                    if (timedOut(30)) pause("player could not ground before jump");
                    return;
                }
                if (!validBuildGeometry()) { pause("build geometry became invalid"); return; }
                mc.player.jump();
                afterAction();
                transition(State.PLACE_LOWER, "jumped");
                break;
            case PLACE_LOWER:
                if (!readyForAction()) return;
                if (placeSolidBlock(lowerBlockPos)) transition(State.VERIFY_LOWER, "lower placement sent");
                else retryAction("lower block placement was rejected", State.PLACE_LOWER);
                break;
            case VERIFY_LOWER:
                if (isSolid(lowerBlockPos)) { retries = 0; transition(State.PLACE_UPPER, "lower block verified"); }
                else if (timedOut(operationTimeout())) retryAction("lower block was not confirmed", State.PLACE_LOWER);
                break;
            case PLACE_UPPER:
                if (!readyForAction()) return;
                if (!mc.world.isAirBlock(airGapPos)) { pause("air-gap position is occupied"); return; }
                if (placeSolidBlock(upperBlockPos)) transition(State.VERIFY_UPPER, "upper placement sent");
                else retryAction("upper block has no reachable support", State.PLACE_UPPER);
                break;
            case VERIFY_UPPER:
                if (isSolid(upperBlockPos) && mc.world.isAirBlock(airGapPos)) { retries = 0; transition(State.BREAK_LOWER, "upper block and air gap verified"); }
                else if (timedOut(operationTimeout())) retryAction("upper block or air gap was not confirmed", State.PLACE_UPPER);
                break;
            case BREAK_LOWER:
                breakLowerBlock();
                break;
            case PLACE_NEXT_LAVA:
                if (!readyForAction()) return;
                if (placeLavaAt(nextLavaPos)) beginFlowTracking(true);
                else retryAction("next lava placement was rejected", State.PLACE_NEXT_LAVA);
                break;
            default:
                pause("unknown state");
        }
    }

    private boolean planCycle() {
        EnumFacing facing = EnumFacing.fromAngle(mc.player.rotationYaw);
        BlockPos playerFeet = new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
        lavaPos = playerFeet.offset(facing).toImmutable();
        lowerBlockPos = lavaPos.offset(facing).toImmutable();
        airGapPos = lowerBlockPos.up().toImmutable();
        upperBlockPos = lowerBlockPos.up(2).toImmutable();
        nextLavaPos = lowerBlockPos.toImmutable();
        return mc.world.isAirBlock(lavaPos) && hasReachableSupport(lavaPos);
    }

    private void beginFlowTracking(boolean returnToPrepare) {
        flow = new FlowTracker(lavaPos, ticks, maxFlowTrackingTicks);
        collectReturnsToPrepare = returnToPrepare;
        retries = 0;
        afterAction();
        transition(State.WAIT_FLOW, "lava placement sent at " + format(lavaPos));
    }

    private void observeFlow() {
        if (flow == null) { pause("missing lava tracker"); return; }
        flow.observe(ticks, sampledLava(), relevantChunksLoaded());
        flow.stabilizeIfUnchanged(ticks, flowStabilityTicks);
        if (flow.status == FlowStatus.INVALID) { retryAction("lava flow became invalid", collectReturnsToPrepare ? State.PLACE_NEXT_LAVA : State.PLACE_LAVA); return; }
        if (flow.status == FlowStatus.STABILIZED) { transition(State.COLLECT_LAVA, "flow stabilized"); return; }
        if (flow.status == FlowStatus.TIMED_OUT) {
            if (continueAfterFlowTimeout != 0) transition(State.COLLECT_LAVA, "flow tracking timed out; fallback collection");
            else pause("lava flow did not stabilize before timeout");
        }
    }

    private Map<BlockPos, Integer> sampledLava() {
        if (lavaPos == null) return Collections.emptyMap();
        Map<BlockPos, Integer> result = new LinkedHashMap<BlockPos, Integer>();
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 1; y++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = lavaPos.add(x, y, z);
            IBlockState state = mc.world.getBlockState(pos);
            if (state.getBlock() == Blocks.LAVA) result.put(pos.toImmutable(), state.getValue(BlockLiquid.LEVEL));
        }
        return result;
    }

    private boolean relevantChunksLoaded() {
        if (lavaPos == null) return false;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            if (!mc.world.isBlockLoaded(lavaPos.add(x, 0, z))) return false;
        return true;
    }

    private void collectLava() {
        if (!isLava(lavaPos)) { retryAction("source disappeared before collection", collectReturnsToPrepare ? State.PLACE_NEXT_LAVA : State.PLACE_LAVA); return; }
        int slot = selectItem(Items.BUCKET);
        if (slot < 0) { pause("empty bucket is unavailable"); return; }
        lavaBucketsBeforeCollect = InventoryManager.count(mc, Items.LAVA_BUCKET);
        emptyBucketsBeforeCollect = InventoryManager.count(mc, Items.BUCKET);
        mc.player.inventory.currentItem = slot;
        Vec3d hit = new Vec3d(lavaPos.getX() + .5D, lavaPos.getY() + .5D, lavaPos.getZ() + .5D);
        EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world, lavaPos, EnumFacing.UP, hit, EnumHand.MAIN_HAND);
        if (result == EnumActionResult.SUCCESS) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
            afterAction();
            transition(State.VERIFY_COLLECT, "bucket interaction sent");
        } else if (++collectRetries > placementRetryLimit) pause("bucket interaction was rejected");
        else afterAction();
    }

    private void verifyCollection() {
        boolean inventoryChanged = InventoryManager.count(mc, Items.LAVA_BUCKET) > lavaBucketsBeforeCollect
                || InventoryManager.count(mc, Items.BUCKET) < emptyBucketsBeforeCollect;
        if (inventoryChanged && !isLava(lavaPos)) {
            collectRetries = 0;
            transition(collectReturnsToPrepare ? State.PREPARE : State.JUMP, "lava collection verified");
        } else if (timedOut(operationTimeout())) {
            if (++collectRetries > placementRetryLimit) pause("lava collection was not confirmed");
            else transition(State.COLLECT_LAVA, "retry collection");
        }
    }

    private void breakLowerBlock() {
        if (mc.world.isAirBlock(lowerBlockPos)) { transition(State.PLACE_NEXT_LAVA, "lower block broken"); return; }
        if (!isSolid(lowerBlockPos) || playerIntersects(lowerBlockPos) || !withinReach(lowerBlockPos)) {
            pause("lower block cannot be broken safely"); return;
        }
        if (timedOut(operationTimeout() * 3)) { pause("lower block breaking timed out"); return; }
        if (!readyForAction()) return;
        mc.playerController.clickBlock(lowerBlockPos, EnumFacing.UP);
        mc.playerController.onPlayerDamageBlock(lowerBlockPos, EnumFacing.UP);
        mc.player.swingArm(EnumHand.MAIN_HAND);
        afterAction();
    }

    private boolean validBuildGeometry() {
        return mc.world.isAirBlock(lowerBlockPos) && mc.world.isAirBlock(airGapPos) && mc.world.isAirBlock(upperBlockPos)
                && hasReachableSupport(lowerBlockPos) && hasReachableSupport(upperBlockPos);
    }

    private boolean placeLavaAt(BlockPos target) {
        if (!mc.world.isAirBlock(target)) return isLava(target);
        int slot = selectItem(Items.LAVA_BUCKET);
        if (slot < 0) { pause("lava bucket is unavailable"); return false; }
        return placeAgainstSupport(target, slot);
    }

    private boolean placeSolidBlock(BlockPos target) {
        if (!mc.world.isAirBlock(target)) return isSolid(target);
        int slot = InventoryBlocks.selectFullBlock(mc, savedHotbarSlot >= 0 ? savedHotbarSlot : mc.player.inventory.currentItem);
        if (slot < 0) { pause("full building block is unavailable"); return false; }
        return placeAgainstSupport(target, slot);
    }

    private boolean placeAgainstSupport(BlockPos target, int slot) {
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos support = target.offset(side);
            // Buckets may legitimately target slabs, stairs, and other collidable
            // non-full blocks. Full cubes are only required for the cast structure.
            if (!isPlacementSupport(support)) continue;
            EnumFacing face = side.getOpposite();
            Vec3d hit = InventoryBlocks.hitVec(support, face);
            if (mc.player.getPositionEyes(1F).squareDistanceTo(hit) > mc.playerController.getBlockReachDistance() * mc.playerController.getBlockReachDistance()) continue;
            mc.player.inventory.currentItem = slot;
            rotateTo(hit);
            EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world, support, face, hit, EnumHand.MAIN_HAND);
            restoreRotation();
            if (result == EnumActionResult.SUCCESS) { mc.player.swingArm(EnumHand.MAIN_HAND); return true; }
        }
        return false;
    }

    private boolean hasReachableSupport(BlockPos target) {
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos support = target.offset(side);
            if (!isPlacementSupport(support)) continue;
            if (withinReach(InventoryBlocks.hitVec(support, side.getOpposite()))) return true;
        }
        return false;
    }

    private boolean withinReach(BlockPos pos) { return withinReach(new Vec3d(pos).addVector(.5D, .5D, .5D)); }
    private boolean withinReach(Vec3d hit) {
        double reach = mc.playerController.getBlockReachDistance();
        return mc.player.getPositionEyes(1F).squareDistanceTo(hit) <= reach * reach;
    }

    private boolean isLava(BlockPos pos) { return pos != null && mc.world.getBlockState(pos).getMaterial() == Material.LAVA; }
    private boolean isSolid(BlockPos pos) { return pos != null && mc.world.getBlockState(pos).isFullBlock(); }
    private boolean isPlacementSupport(BlockPos pos) {
        if (pos == null) return false;
        IBlockState state = mc.world.getBlockState(pos);
        return !state.getMaterial().isReplaceable() && state.getMaterial().blocksMovement();
    }
    private boolean playerIntersects(BlockPos pos) { return mc.player.getEntityBoundingBox().intersects(new AxisAlignedBB(pos)); }

    private int selectItem(Item item) {
        int inventorySlot = InventoryManager.findItem(mc, item);
        if (inventorySlot < 0) return -1;
        int hotbar = savedHotbarSlot >= 0 ? savedHotbarSlot : mc.player.inventory.currentItem;
        if (inventorySlot < 9) return inventorySlot;
        return InventoryManager.moveToHotbar(mc, inventorySlot, hotbar) ? hotbar : -1;
    }

    private void rotateTo(Vec3d hit) {
        if (mc.getConnection() == null) return;
        Vec3d eye = mc.player.getPositionEyes(1F);
        double dx = hit.x - eye.x, dy = hit.y - eye.y, dz = hit.z - eye.z;
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180D / Math.PI) - 90F;
        float pitch = (float) (-MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180D / Math.PI);
        PacketManager.send(new CPacketPlayer.Rotation(yaw, pitch, mc.player.onGround), name);
    }

    private void restoreRotation() {
        if (mc.getConnection() != null) PacketManager.send(new CPacketPlayer.Rotation(mc.player.rotationYaw, mc.player.rotationPitch, mc.player.onGround), name);
    }

    private boolean usableWorld() {
        return mc.player != null && mc.world != null && mc.playerController != null && !mc.player.isDead && mc.player.getHealth() > 0F;
    }
    private boolean readyForAction() { return ticks >= nextActionTick; }
    private int operationTimeout() { return Math.max(8, actionDelayTicks * 4 + 10); }
    private boolean timedOut(int limit) { return ticks - stateEnteredTick > limit; }
    private void afterAction() { nextActionTick = ticks + Math.max(0, actionDelayTicks); }

    private void retryAction(String reason, State retryState) {
        if (++retries > placementRetryLimit) { pause(reason + " (retry limit)"); return; }
        flow = null;
        afterAction();
        transition(retryState, reason + " (retry " + retries + ")");
    }

    private void transition(State next, String reason) {
        state = next;
        stateEnteredTick = ticks;
        if (debugLogging != 0) LOGGER.info("{} -> {}: {}", name, next, reason);
    }

    private static String format(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void pause(String reason) {
        flow = null;
        collectRetries = retries = 0;
        nextActionTick = ticks + 20;
        transition(State.IDLE, "paused: " + reason);
    }

    private void resetCycle() {
        ticks = stateEnteredTick = nextActionTick = retries = collectRetries = 0;
        flow = null; collectReturnsToPrepare = false;
        lavaPos = lowerBlockPos = airGapPos = upperBlockPos = nextLavaPos = null;
    }

    @Override protected void onDisable() {
        if (mc.player != null && savedHotbarSlot >= 0 && savedHotbarSlot < 9) mc.player.inventory.currentItem = savedHotbarSlot;
        savedHotbarSlot = -1;
        resetCycle();
    }
    @Override protected void onWorldUnload() { onDisable(); }
    @Override public String hudTag() { return "[" + state.name() + "]"; }

    /** Pure observed-state tracker; also used by the offline diagnostic harness. */
    public static final class FlowTracker {
        public final BlockPos origin;
        public final int placementTick, maxTicks;
        public boolean sourceBlock, sawLava;
        public int lastChangedTick;
        public FlowStatus status = FlowStatus.TRACKING;
        public Set<EnumFacing> observedDirections = Collections.emptySet();
        public Map<BlockPos, Integer> neighboringLava = Collections.emptyMap();
        private Map<BlockPos, Integer> previous = Collections.emptyMap();

        public FlowTracker(BlockPos origin, int placementTick, int maxTicks) {
            this.origin = origin.toImmutable(); this.placementTick = placementTick; this.maxTicks = Math.max(1, maxTicks);
            this.lastChangedTick = placementTick;
        }

        public void observe(int tick, Map<BlockPos, Integer> current, boolean chunksLoaded) {
            if (status != FlowStatus.TRACKING) return;
            if (!chunksLoaded) { status = FlowStatus.INVALID; return; }
            Map<BlockPos, Integer> copy = new LinkedHashMap<BlockPos, Integer>(current);
            Integer originLevel = copy.get(origin);
            if (originLevel != null) { sawLava = true; sourceBlock = originLevel.intValue() == 0; }
            if (sawLava && originLevel == null) { status = FlowStatus.INVALID; return; }
            if (!copy.equals(previous)) { previous = copy; lastChangedTick = tick; }
            neighboringLava = Collections.unmodifiableMap(copy);
            EnumSet<EnumFacing> directions = EnumSet.noneOf(EnumFacing.class);
            for (EnumFacing face : EnumFacing.HORIZONTALS) if (copy.containsKey(origin.offset(face))) directions.add(face);
            observedDirections = Collections.unmodifiableSet(directions);
            if (tick - placementTick >= maxTicks) { status = FlowStatus.TIMED_OUT; return; }
        }

        public void stabilizeIfUnchanged(int tick, int stabilityTicks) {
            if (status == FlowStatus.TRACKING && sawLava && tick - lastChangedTick >= Math.max(1, stabilityTicks)) status = FlowStatus.STABILIZED;
        }
    }

    public enum FlowStatus { TRACKING, STABILIZED, TIMED_OUT, INVALID }
}
