package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Bounded multi-tree search with observed-fire tracking and route recovery. */
public final class TreeBurner extends Module {
    private static final EnumFacing[] HORIZONTAL = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
    private static final int MAX_PATH_NODES = 2400;
    private static final int MAX_TREES_PER_SCAN = 96;
    private static final int MAX_LOGS_PER_TREE = 128;
    private final Map<BlockPos, Integer> visited = new HashMap<BlockPos, Integer>();
    private final Map<BlockPos, Integer> blocked = new HashMap<BlockPos, Integer>();
    private final Map<IBlockState, Boolean> oreLogs = new HashMap<IBlockState, Boolean>();
    private Plan plan;
    private boolean baritoneOwned, warnedMissingBaritone, warnedMissingTool;
    private int waypointIndex, ticks, nextScan, nextClick, stagnantTicks, failedClicks;
    private double lastWaypointDistance = Double.MAX_VALUE;
    private BlockPos pendingFire, pendingTree;
    private int pendingUntil;
    private boolean movingForward, jumping;
    public int searchRadius = 18, scanInterval = 8, clickDelay = 2, stuckTimeout = 14;

    public TreeBurner() {
        super("TreeBurner", "Finds reachable trees, avoids burning trees, and recovers from blocked routes.", Category.WORLD, 0);
        addIntSetting("Search radius", 4, 32, 2, () -> searchRadius, v -> searchRadius = v);
        addIntSetting("Scan interval", 2, 30, 1, () -> scanInterval, v -> scanInterval = v);
        addIntSetting("Click delay", 1, 12, 1, () -> clickDelay, v -> clickDelay = v);
        addIntSetting("Stuck timeout", 6, 40, 1, () -> stuckTimeout, v -> stuckTimeout = v);
    }

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }
    @Override protected void onWorldUnload() { reset(); }

    private void reset() {
        cancelBaritone();
        visited.clear(); blocked.clear(); plan = null; pendingFire = pendingTree = null;
        oreLogs.clear(); warnedMissingBaritone = warnedMissingTool = false;
        waypointIndex = ticks = nextScan = nextClick = stagnantTicks = failedClicks = 0;
        lastWaypointDistance = Double.MAX_VALUE; stopMoving();
    }

    @Override public void tick() {
        if (mc.player == null || mc.world == null) { stopMoving(); return; }
        ticks++;
        if (isAutomationBlockedByScreen()) { stopMoving(); return; }
        expire(visited); expire(blocked);
        if (pendingFire != null) {
            if (isFire(pendingFire)) {
                visited.put(pendingTree, ticks + 20 * 60);
                pendingFire = pendingTree = null;
                clearPlan(); nextScan = ticks;
            } else if (ticks >= pendingUntil) {
                pendingFire = pendingTree = null;
                if (++failedClicks >= 3) abandon(60);
            }
        }
        if (plan != null && (treeBurning(plan.logs) || !isLog(plan.log))) {
            visited.put(plan.anchor, ticks + 20 * 60);
            clearPlan(); nextScan = ticks;
        }
        if (plan == null && ticks >= nextScan) {
            nextScan = ticks + scanInterval;
            plan = findPlan();
            waypointIndex = plan == null ? 0 : Math.min(1, plan.path.size() - 1);
            stagnantTicks = failedClicks = 0;
            lastWaypointDistance = Double.MAX_VALUE;
        }
        if (plan == null) { stopMoving(); return; }
        if (!hasIgniter() && !warnedMissingTool) {
            warn("Need flint and steel in inventory or offhand to ignite; continuing toward tree.");
            warnedMissingTool = true;
        }
        if (hasIgniter()) warnedMissingTool = false;
        if (canSeeTreeNow()) {
            cancelBaritone();
            stopMoving();
            if (!hasIgniter()) return;
            if (pendingFire == null && ticks >= nextClick) {
                nextClick = ticks + clickDelay;
                if (!tryIgnite()) {
                    if (++failedClicks >= 3) abandon(80);
                } else failedClicks = 0;
            }
            return;
        }
        followRoute();
    }

    private void expire(Map<BlockPos, Integer> entries) {
        entries.entrySet().removeIf(entry -> entry.getValue() <= ticks);
    }

    private void clearPlan() {
        cancelBaritone();
        plan = null; waypointIndex = 0; stagnantTicks = 0;
        lastWaypointDistance = Double.MAX_VALUE; stopMoving();
    }

    private void abandon(int cooldown) {
        if (plan != null) blocked.put(plan.anchor, ticks + cooldown);
        clearPlan(); nextScan = ticks;
    }

    private boolean withinReach(BlockPos log) {
        return mc.player.getDistanceSqToCenter(log) <= Math.pow(mc.playerController.getBlockReachDistance(), 2);
    }

    private boolean canSeeTreeNow() {
        Vec3d eye = mc.player.getPositionEyes(1.0F);
        double reachSq = Math.pow(mc.playerController.getBlockReachDistance(), 2);
        for (BlockPos log : plan.logs) for (EnumFacing face : EnumFacing.values()) {
            if (!isLog(log) || !loaded(log.offset(face)) || !mc.world.isAirBlock(log.offset(face))) continue;
            Vec3d point = facePoint(log, face);
            if (eye.squareDistanceTo(point) <= reachSq) return true;
        }
        return false;
    }

    private boolean tryIgnite() {
        Vec3d eye = mc.player.getPositionEyes(1.0F);
        double reachSq = Math.pow(mc.playerController.getBlockReachDistance(), 2);
        EnumFacing bestFace = null;
        Vec3d bestPoint = null;
        BlockPos bestLog = null, bestFire = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos log : plan.logs) {
            if (!isLog(log)) continue;
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos fire = log.offset(face);
                if (!loaded(fire) || !mc.world.isAirBlock(fire)) continue;
                Vec3d point = facePoint(log, face);
                double distance = eye.squareDistanceTo(point);
                if (distance > reachSq) continue;
                RayTraceResult hit = mc.world.rayTraceBlocks(eye, point, false, true, false);
                boolean visible = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                        && log.equals(hit.getBlockPos()) && hit.sideHit == face;
                double score = distance + (visible ? 0 : reachSq * 2);
                if (score >= bestDistance) continue;
                bestLog = log; bestFace = face; bestFire = fire;
                bestPoint = visible ? hit.hitVec : point; bestDistance = score;
            }
        }
        if (bestLog == null) return false;
        Vec3d point = bestPoint;
        double dx = point.x - mc.player.posX, dy = point.y - mc.player.posY - mc.player.getEyeHeight();
        double dz = point.z - mc.player.posZ;
        mc.player.rotationYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        mc.player.rotationPitch = (float) (-MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);
        mc.player.rotationYawHead = mc.player.rotationYaw;
        mc.player.renderYawOffset = mc.player.rotationYaw;
        EnumHand hand = mc.player.getHeldItemOffhand().getItem() == Items.FLINT_AND_STEEL
                ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        int slot = hand == EnumHand.MAIN_HAND ? findFlintAndSteel() : -1;
        if (hand == EnumHand.MAIN_HAND && slot < 0) return false;
        if (slot >= 0) mc.player.inventory.currentItem = slot;
        mc.playerController.updateController();
        EnumActionResult result = mc.playerController.processRightClickBlock(
                mc.player, mc.world, bestLog, bestFace, bestPoint, hand);
        if (result != EnumActionResult.SUCCESS) return false;
        mc.player.swingArm(hand);
        pendingFire = bestFire; pendingTree = plan.anchor; pendingUntil = ticks + 12;
        return true;
    }

    private static Vec3d facePoint(BlockPos log, EnumFacing face) {
        return new Vec3d(log.getX() + .5 + face.getFrontOffsetX() * .501,
                log.getY() + .5 + face.getFrontOffsetY() * .501,
                log.getZ() + .5 + face.getFrontOffsetZ() * .501);
    }

    private void followRoute() {
        BlockPos goal = plan.path.get(0);
        if (!baritoneOwned && !pathTo(goal)) {
            if (!warnedMissingBaritone) { warn("Baritone is unavailable; install the Forge 1.12.2 Baritone mod."); warnedMissingBaritone = true; }
            abandon(100); return;
        }
        double distance = mc.player.getDistanceSqToCenter(goal);
        if (distance < lastWaypointDistance - .01) {
            lastWaypointDistance = distance; stagnantTicks = 0;
        } else if (++stagnantTicks >= Math.max(40, stuckTimeout * 4)) {
            abandon(100);
        }
    }

    @Override public void onInputUpdate(InputUpdateEvent event) {
        // Baritone owns movement input while the target is active.
    }

    private boolean pathTo(BlockPos goal) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            Object baritone = Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
            Object process = Class.forName("baritone.api.IBaritone").getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goalType = Class.forName("baritone.api.pathing.goals.Goal");
            Class<?> goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object location = goalBlock.getConstructor(int.class, int.class, int.class)
                    .newInstance(goal.getX(), goal.getY(), goal.getZ());
            Class.forName("baritone.api.process.ICustomGoalProcess").getMethod("setGoalAndPath", goalType).invoke(process, location);
            baritoneOwned = true; warnedMissingBaritone = false; return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
    }

    private void cancelBaritone() {
        if (!baritoneOwned) return;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            Object baritone = Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
            Object process = Class.forName("baritone.api.IBaritone").getMethod("getCustomGoalProcess").invoke(baritone);
            Class.forName("baritone.api.process.IBaritoneProcess").getMethod("onLostControl").invoke(process);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            try {
                Class<?> api = Class.forName("baritone.api.BaritoneAPI");
                Object provider = api.getMethod("getProvider").invoke(null);
                Object baritone = Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
                Object behavior = Class.forName("baritone.api.IBaritone").getMethod("getPathingBehavior").invoke(baritone);
                Class.forName("baritone.api.behavior.IPathingBehavior").getMethod("cancelEverything").invoke(behavior);
            } catch (ReflectiveOperationException | LinkageError ignoredAgain) { }
        }
        baritoneOwned = false;
    }

    private void warn(String message) {
        if (mc.player != null) mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("[Backspace] TreeBurner: " + message));
    }

    private Plan findPlan() {
        BlockPos origin = new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
        Set<BlockPos> seenLogs = new HashSet<BlockPos>();
        Plan best = null;
        int examined = 0, foundLogs = 0, candidateGoals = 0, bestScore = Integer.MAX_VALUE;
        for (BlockPos cursor : BlockPos.getAllInBoxMutable(
                origin.add(-searchRadius, -4, -searchRadius),
                origin.add(searchRadius, 6, searchRadius))) {
            if (!loaded(cursor) || !isLog(cursor) || seenLogs.contains(cursor)) continue;
            foundLogs++;
            Tree tree = collectTree(cursor.toImmutable(), seenLogs);
            if (++examined > MAX_TREES_PER_SCAN) break;
            if (tree.burning || visited.containsKey(tree.anchor) || blocked.containsKey(tree.anchor)) continue;
            for (BlockPos log : tree.logs) {
                if (!hasExposedSide(log)) continue;
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    if (x == 0 && z == 0) continue;
                    for (int y = -3; y <= 2; y++) {
                        BlockPos goal = new BlockPos(log.getX() + x, log.getY() + y, log.getZ() + z);
                        if (!canStand(goal)) continue;
                        candidateGoals++;
                        int score = (int) origin.distanceSq(goal);
                        if (score >= bestScore) continue;
                        best = new Plan(tree.anchor, log, tree.logs, Collections.singletonList(goal));
                        bestScore = score;
                    }
                }
            }
        }
        if (best == null && ticks % 200 < scanInterval)
            warn("Scan saw " + foundLogs + " logs, " + examined + " trees, " + candidateGoals + " approach spots.");
        return best;
    }

    private Tree collectTree(BlockPos seed, Set<BlockPos> seen) {
        Queue<BlockPos> queue = new ArrayDeque<BlockPos>();
        List<BlockPos> logs = new ArrayList<BlockPos>();
        queue.add(seed); seen.add(seed);
        BlockPos anchor = seed;
        boolean burning = false;
        while (!queue.isEmpty() && logs.size() < MAX_LOGS_PER_TREE) {
            BlockPos log = queue.remove();
            logs.add(log);
            if (log.getY() < anchor.getY()
                    || (log.getY() == anchor.getY() && (log.getX() < anchor.getX()
                    || (log.getX() == anchor.getX() && log.getZ() < anchor.getZ())))) anchor = log;
            if (!burning && nearFire(log)) burning = true;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                BlockPos next = log.add(x, y, z);
                if (!seen.contains(next) && isLog(next)) { seen.add(next); queue.add(next); }
            }
        }
        return new Tree(anchor, logs, burning);
    }

    private boolean treeBurning(List<BlockPos> logs) {
        for (BlockPos log : logs) if (nearFire(log)) return true;
        return false;
    }

    private boolean nearFire(BlockPos log) {
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 2; y++)
            for (int z = -2; z <= 2; z++)
                if (isFire(log.add(x, y, z))) return true;
        return false;
    }

    private boolean canSeeFrom(BlockPos feet, BlockPos log) {
        Vec3d eye = new Vec3d(feet.getX() + .5, feet.getY() + mc.player.getEyeHeight(), feet.getZ() + .5);
        double reachSq = Math.pow(mc.playerController.getBlockReachDistance(), 2);
        for (EnumFacing face : EnumFacing.values()) {
            if (!loaded(log.offset(face)) || !mc.world.isAirBlock(log.offset(face))) continue;
            Vec3d point = facePoint(log, face);
            if (eye.squareDistanceTo(point) > reachSq) continue;
            RayTraceResult hit = mc.world.rayTraceBlocks(eye, point, false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && log.equals(hit.getBlockPos()) && hit.sideHit == face) return true;
        }
        return false;
    }

    private boolean hasExposedSide(BlockPos log) {
        for (EnumFacing face : EnumFacing.values())
            if (loaded(log.offset(face)) && mc.world.isAirBlock(log.offset(face))) return true;
        return false;
    }

    private boolean loaded(BlockPos pos) { return mc.world.isBlockLoaded(pos); }
    private boolean isLog(BlockPos pos) {
        if (!loaded(pos)) return false;
        IBlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.LOG || block == Blocks.LOG2 || block.isWood(mc.world, pos)) return true;
        Boolean cached = oreLogs.get(state);
        if (cached != null) return cached;
        ItemStack stack = new ItemStack(block, 1, block.getMetaFromState(state));
        boolean log = false;
        if (!stack.isEmpty()) for (int id : OreDictionary.getOreIDs(stack))
            if (id == OreDictionary.getOreID("logWood")) { log = true; break; }
        oreLogs.put(state, log);
        return log;
    }
    private boolean isFire(BlockPos pos) { return loaded(pos) && mc.world.getBlockState(pos).getBlock() == Blocks.FIRE; }
    private boolean canStand(BlockPos feet) {
        if (!loaded(feet) || !loaded(feet.up()) || !loaded(feet.down())) return false;
        if (!mc.world.getBlockState(feet).getBlock().isPassable(mc.world, feet)) return false;
        if (!mc.world.getBlockState(feet.up()).getBlock().isPassable(mc.world, feet.up())) return false;
        BlockPos floor = feet.down();
        return mc.world.getBlockState(floor).isSideSolid(mc.world, floor, EnumFacing.UP);
    }
    private int findFlintAndSteel() {
        int slot = InventoryManager.findItem(mc, Items.FLINT_AND_STEEL);
        if (slot < 0) return -1;
        return slot < 9 ? slot : InventoryManager.moveToHotbar(mc, slot, 0) ? 0 : -1;
    }
    private boolean hasIgniter() {
        return mc.player.getHeldItemOffhand().getItem() == Items.FLINT_AND_STEEL
                || InventoryManager.findItem(mc, Items.FLINT_AND_STEEL) >= 0;
    }
    private void stopMoving() { movingForward = jumping = false; }

    private static final class Tree {
        final BlockPos anchor; final List<BlockPos> logs; final boolean burning;
        Tree(BlockPos anchor, List<BlockPos> logs, boolean burning) {
            this.anchor = anchor; this.logs = logs; this.burning = burning;
        }
    }
    private static final class Plan {
        final BlockPos anchor, log; final List<BlockPos> logs, path;
        Plan(BlockPos anchor, BlockPos log, List<BlockPos> logs, List<BlockPos> path) {
            this.anchor = anchor; this.log = log; this.logs = logs; this.path = path;
        }
    }
}
