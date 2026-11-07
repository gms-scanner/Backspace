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

    public int flowStabilityTicks = 160;
    public int edgeVerificationTicks = 80;
    public int maxFlowTrackingTicks = 12000;
    public int flowScanRadius = 128;
    public int actionDelayTicks = 2;
    public int placementRetryLimit = 3;
    public int continueAfterFlowTimeout = 0; // Retained for old configurations; timeouts always stop.
    public int debugLogging = 0;

    private State state = State.PREPARE;
    private String stopReason="";
    private BlockPos waterPos;
    private int waterBefore;
    public int waterReachDepth=4;
    private int observedWaterDepth;
    private int scanLongestPath, longestFlowPath;
    private int displayedFlowBlocks;
    private boolean scanCanSpread, displayedCanSpread, displayedIncomplete;
    private final Map<BlockPos,Integer> flowDistance = new java.util.HashMap<BlockPos,Integer>();
    private int completedCycles;
    private int ticks, stateEnteredTick, nextActionTick, retries, collectRetries;
    private int savedHotbarSlot = -1, dimension = Integer.MIN_VALUE;
    private int lavaBucketsBeforeCollect, emptyBucketsBeforeCollect;
    private boolean collectReturnsToPrepare;
    private FlowTracker flow;
    private BlockPos towerBase;
    private EnumFacing castFacing;
    private int minimumFlowTicks, liquidTickRate, castFloorY = Integer.MAX_VALUE;
    private float miningProgress;
    private int lastMineTick=-1;
    private boolean miningStarted, miningFinished;
    private final java.util.ArrayDeque<BlockPos> flowQueue = new java.util.ArrayDeque<BlockPos>();
    private final java.util.HashSet<BlockPos> flowVisited = new java.util.HashSet<BlockPos>();
    private final Map<BlockPos,Integer> flowSample = new LinkedHashMap<BlockPos,Integer>();
    private boolean scanIncomplete;

    // The names are deliberate: each target in the casting geometry remains explicit.
    private BlockPos lavaPos, lowerBlockPos, airGapPos, upperBlockPos, nextLavaPos;

    private enum State {
        IDLE, PREPARE, PLACE_LAVA, WAIT_FLOW, COLLECT_LAVA, VERIFY_COLLECT,
        PLACE_WATER, VERIFY_WATER, WAIT_WATER, COLLECT_WATER, VERIFY_WATER_COLLECT, DRAIN_WATER,
        JUMP, PLACE_LOWER, VERIFY_LOWER, JUMP_UPPER, PLACE_UPPER, VERIFY_UPPER,
        BREAK_LOWER, PLACE_NEXT_LAVA
    }

    public LavaCast() {
        super("LavaCast", "Tracks lava flow and automates the casting sequence.", Category.WORLD, 0);
        addIntSetting("Water reach below feet", 3, 5, 1, () -> waterReachDepth, value -> waterReachDepth = value);
        addIntSetting("Edge verification ticks", 20, 240, 10, () -> edgeVerificationTicks, value -> edgeVerificationTicks = value);
        addIntSetting("Flow stability ticks", 120, 600, 10, () -> flowStabilityTicks, value -> flowStabilityTicks = value);
        addIntSetting("Maximum flow tracking", 1200, 24000, 200, () -> maxFlowTrackingTicks, value -> maxFlowTrackingTicks = value);
        addIntSetting("Flow scan radius", 32, 192, 8, () -> flowScanRadius, value -> flowScanRadius = value);
        addIntSetting("Action delay (ticks)", 0, 20, 1, () -> actionDelayTicks, value -> actionDelayTicks = value);
        addIntSetting("Placement retry limit", 1, 8, 1, () -> placementRetryLimit, value -> placementRetryLimit = value);
        addIntSetting("Debug logging (0/1)", 0, 1, 1, () -> debugLogging, value -> debugLogging = value);
    }

    @Override protected void onEnable() {
        resetCycle();
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.player != null) {
            savedHotbarSlot = mc.player.inventory.currentItem;
            dimension = mc.player.dimension;
        }
        transition(State.PREPARE, "enabled");
    }

    @Override public void tick() {
        if (!usableWorld()) { if (state != State.IDLE) pause("player, world, or controller unavailable"); return; }
        if (mc.player.dimension != dimension) { dimension = mc.player.dimension; resetCycle(); transition(State.IDLE, "dimension changed"); return; }
        // Keep the integrated server ticking and count actual client ticks.
        // Wall time can jump ahead when a background window is throttled.
        mc.gameSettings.pauseOnLostFocus = false;
        if (isAutomationBlockedByScreen()) return;
        ticks++;

        switch (state) {
            case IDLE:
                break;
            case PREPARE:
                if(mc.world.provider.doesWaterVaporize()){pause("water cannot be used in this dimension");return;}
                if(InventoryManager.findItem(mc,Items.WATER_BUCKET)<0){pause("a water bucket is required for the repeating cycle");return;}
                if (!planCycle()) { pause("no supported position near the player"); return; }
                transition(State.PLACE_LAVA, "geometry prepared");
                break;
            case PLACE_LAVA:
                if (!readyForAction()) return;
                if (!moveToTower(true)) return;
                if (placeLavaAt(lavaPos)) beginFlowTracking(false);
                else if (state != State.IDLE) retryAction("lava placement was rejected", State.PLACE_LAVA);
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
            case PLACE_WATER:
                if(!moveToTower(false))return;
                int waterSlot=selectItem(Items.WATER_BUCKET);
                if(waterSlot<0){pause("water bucket is unavailable");return;}
                if(placeWaterAtFeet(waterSlot))transition(State.VERIFY_WATER,"water placed at feet");
                else if(timedOut(100))pause("cannot reach the top face for water");
                break;
            case VERIFY_WATER:
                moveToTower(false);
                if(isWaterSource())transition(State.WAIT_WATER,"water source confirmed");
                else if(timedOut(60))pause("server rejected water placement");
                break;
            case WAIT_WATER:
                moveToTower(false);
                observedWaterDepth=measureWaterReach();
                if(observedWaterDepth>=waterReachDepth)
                    transition(State.COLLECT_WATER,"water reached "+observedWaterDepth+" blocks below feet");
                else if(timedOut(160))pause("water did not reach "+waterReachDepth+" blocks below feet");
                break;
            case COLLECT_WATER:
                int bucketSlot=selectItem(Items.BUCKET);
                if(bucketSlot<0){pause("empty bucket missing for water recovery");return;}
                waterBefore=InventoryManager.count(mc,Items.WATER_BUCKET);
                mc.player.inventory.currentItem=bucketSlot;mc.playerController.updateController();
                rotateTo(new Vec3d(waterPos).addVector(.5,.5,.5));
                PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerTryUseItem(EnumHand.MAIN_HAND),name);
                restoreRotation();transition(State.VERIFY_WATER_COLLECT,"water pickup requested");
                break;
            case VERIFY_WATER_COLLECT:
                moveToTower(false);
                if(!isWaterSource() && InventoryManager.count(mc,Items.WATER_BUCKET)>waterBefore){collectRetries=0;transition(State.DRAIN_WATER,"water recovered");}
                else if(timedOut(60)){if(++collectRetries>placementRetryLimit)pause("water pickup rejected");else transition(State.COLLECT_WATER,"retry water pickup");}
                break;
            case DRAIN_WATER:
                moveToTower(false);
                if(timedOut(10) && mc.world.isAirBlock(waterPos) && mc.player.onGround)transition(State.JUMP,"climb two supported blocks");
                else if(timedOut(120))pause("water has not drained from the scaffold position");
                break;
            case JUMP:
                if (!readyForAction()) return;
                if (!mc.player.onGround) {
                    if (timedOut(30)) pause("player could not ground before jump");
                    return;
                }
                if (!validBuildGeometry()) { pause("build geometry became invalid"); return; }
                if (!moveToTower(false)) return;
                mc.player.jump();
                afterAction();
                transition(State.PLACE_LOWER, "jumped");
                break;
            case PLACE_LOWER:
                if (!readyForAction()) return;
                if (playerIntersects(lowerBlockPos)) { if(timedOut(30)) pause("first jump did not clear the block"); return; }
                if (placeSolidBlock(lowerBlockPos)) transition(State.VERIFY_LOWER, "lower placement sent");
                else retryAction("lower block placement was rejected", State.PLACE_LOWER);
                break;
            case VERIFY_LOWER:
                if (isSolid(lowerBlockPos) && mc.player.onGround && Math.abs(mc.player.posY-lowerBlockPos.getY()-1)<.1) { retries = 0; transition(State.JUMP_UPPER, "landed on lower block"); }
                else if (timedOut(operationTimeout())) retryAction("lower block was not confirmed", State.PLACE_LOWER);
                break;
            case JUMP_UPPER:
                if (!mc.player.onGround || !moveToTower(false)) return;
                mc.player.jump(); transition(State.PLACE_UPPER,"second jump"); break;
            case PLACE_UPPER:
                if (!readyForAction()) return;
                if (playerIntersects(upperBlockPos)) { if(timedOut(30)) pause("second jump did not clear the block"); return; }
                if (placeSolidBlock(upperBlockPos)) transition(State.VERIFY_UPPER, "upper placement sent");
                else retryAction("upper block has no reachable support", State.PLACE_UPPER);
                break;
            case VERIFY_UPPER:
                if (isSolid(upperBlockPos) && mc.player.onGround && Math.abs(mc.player.posY-upperBlockPos.getY()-1)<.1) { retries = 0; transition(State.BREAK_LOWER, "landed two blocks higher"); }
                else if (timedOut(operationTimeout())) retryAction("upper block or air gap was not confirmed", State.PLACE_UPPER);
                break;
            case BREAK_LOWER:
                breakLowerBlock();
                break;
            case PLACE_NEXT_LAVA:
                if (!readyForAction()) return;
                lavaPos = nextLavaPos;
                if (placeLavaAt(nextLavaPos)) beginFlowTracking(true);
                else if (state != State.IDLE) retryAction("next lava placement was rejected", State.PLACE_NEXT_LAVA);
                break;
            default:
                pause("unknown state");
        }
    }

    private boolean planCycle() {
        EnumFacing facing = EnumFacing.fromAngle(mc.player.rotationYaw);
        BlockPos playerFeet = new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
        if (!mc.player.onGround || !isSolid(playerFeet.down()) || playerFeet.getY()>252) return false;
        castFacing = facing;
        towerBase = playerFeet.down();
        // The standing block supports a source beside it even on a single-block pillar.
        lavaPos = towerBase.offset(facing).toImmutable();
        lowerBlockPos = playerFeet.toImmutable();
        upperBlockPos = lowerBlockPos.up().toImmutable();
        nextLavaPos = lowerBlockPos.toImmutable();
        return mc.world.isAirBlock(lavaPos);
    }

    private void beginFlowTracking(boolean returnToPrepare) {
        longestFlowPath = 0;
        displayedFlowBlocks=0; displayedCanSpread=true; displayedIncomplete=false;
        liquidTickRate = Blocks.FLOWING_LAVA.tickRate(mc.world);
        int bottom = lavaPos.getY();
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos p = lavaPos.offset(side);
            while(p.getY()>0 && mc.world.isBlockLoaded(p) && !mc.world.getBlockState(p).getMaterial().blocksMovement()) p=p.down();
            bottom = Math.min(bottom,p.getY());
        }
        castFloorY = Math.min(castFloorY, bottom);
        bottom = castFloorY;
        minimumFlowTicks = estimatedFlowTicks(lavaPos.getY(), bottom, liquidTickRate, mc.world.provider.doesWaterVaporize()?7:3);
        // Old configs may still contain the previous 400-tick cap. Allow enough
        // time for a full vertical fall and several randomized lava updates.
        int safeLimit = Math.max(24000, minimumFlowTicks * 4 + Math.max(flowStabilityTicks, liquidTickRate * 4 + 20));
        flow = new FlowTracker(lavaPos, ticks, Math.max(maxFlowTrackingTicks, safeLimit));
        beginScan();
        if(returnToPrepare)completedCycles++;
        collectReturnsToPrepare = returnToPrepare;
        retries = 0;
        afterAction();
        transition(State.WAIT_FLOW, "lava placement sent at " + format(lavaPos));
    }

    private void observeFlow() {
        if (flow == null) { pause("missing lava tracker"); return; }
        if (isLava(lavaPos)) flow.sawLava=true;
        if (!flow.sawLava && ticks-flow.placementTick>60) { pause("server did not confirm lava placement"); return; }
        if (!scanFlow()) return;
        if(!scanIncomplete)displayedFlowBlocks=flowSample.size();
        displayedCanSpread=scanCanSpread;
        displayedIncomplete=scanIncomplete;
        if (!flowSample.containsKey(lavaPos)) {
            if(flow.sawLava){pause("lava source disappeared before collection");return;}
            beginScan();return;
        }
        // A partial scan cannot establish that the outer slopes have finished growing.
        flow.observeCompleteScan(ticks,flowSample,!scanIncomplete,scanCanSpread,settlingTicks(),edgeVerificationTicks);
        for(BlockPos pos:flowSample.keySet())castFloorY=Math.min(castFloorY,pos.getY());
        longestFlowPath = Math.max(longestFlowPath, scanLongestPath);
        minimumFlowTicks=estimatedSurfaceFlowTicks(lavaPos.getY(),castFloorY,longestFlowPath,liquidTickRate,
                mc.world.provider.doesWaterVaporize()?7:3);
        if(flow.status==FlowStatus.TIMED_OUT){pause(scanIncomplete ? "flow scan incomplete: load outer chunks or increase scan radius" : "lava still changing: increase maximum flow tracking");return;}
        if(flow.sourceBlock && !scanIncomplete && flow.status==FlowStatus.STABILIZED) {
            transition(State.COLLECT_LAVA,"complete lava footprint settled; collecting lava");
            return;
        }
        beginScan();
    }

    /** Follow connected water, including sideways flow before the first vertical drop. */
    private int measureWaterReach() {
        if(waterPos==null || !mc.world.isBlockLoaded(waterPos))return 0;
        java.util.ArrayDeque<BlockPos> queue=new java.util.ArrayDeque<BlockPos>();
        java.util.HashSet<BlockPos> seen=new java.util.HashSet<BlockPos>();
        queue.add(waterPos);
        int deepest=0;
        for(int budget=0;budget<384 && !queue.isEmpty();budget++){
            BlockPos pos=queue.removeFirst();
            if(!seen.add(pos) || pos.getY()<waterPos.getY()-waterReachDepth
                    || pos.getY()>waterPos.getY() || Math.abs(pos.getX()-waterPos.getX())>8
                    || Math.abs(pos.getZ()-waterPos.getZ())>8 || !mc.world.isBlockLoaded(pos))continue;
            if(mc.world.getBlockState(pos).getMaterial()!=Material.WATER)continue;
            deepest=Math.max(deepest,waterPos.getY()-pos.getY());
            if(deepest>=waterReachDepth)return deepest;
            queue.add(pos.down());
            for(EnumFacing side:EnumFacing.HORIZONTALS)queue.add(pos.offset(side));
        }
        return deepest;
    }
    private boolean isWaterSource() {
        IBlockState block=mc.world.getBlockState(waterPos);
        return block.getMaterial()==Material.WATER && block.getValue(BlockLiquid.LEVEL)==0;
    }

    public static int estimatedFlowTicks(int sourceY, int floorY, int rate, int spread) {
        return (Math.max(0, sourceY-floorY)+spread+1)*rate;
    }
    public static int estimatedSurfaceFlowTicks(int sourceY,int floorY,int pathLength,int rate,int spread) {
        return Math.max(estimatedFlowTicks(sourceY,floorY,rate,spread),(Math.max(0,pathLength)+spread+1)*rate);
    }
    private void beginScan() {
        flowQueue.clear(); flowVisited.clear(); flowSample.clear(); scanIncomplete=false;
        flowDistance.clear(); scanLongestPath=0; scanCanSpread=false;
        flowDistance.put(lavaPos,0);
        flowQueue.add(lavaPos);
    }
    private boolean scanFlow() {
        for(int budget=0; budget<512 && !flowQueue.isEmpty(); budget++) {
            BlockPos pos=flowQueue.removeFirst();
            if(!flowVisited.add(pos)) continue;
            if(pos.getY()<0 || pos.getY()>255) continue;
            if(!mc.world.isBlockLoaded(pos)) { scanIncomplete=true; continue; }
            IBlockState block=mc.world.getBlockState(pos);
            if(block.getMaterial()!=Material.LAVA) continue;
            if(flowSample.size()>=65536 || Math.abs(pos.getX()-lavaPos.getX())>flowScanRadius || Math.abs(pos.getZ()-lavaPos.getZ())>flowScanRadius) {
                scanIncomplete=true; continue;
            }
            flowSample.put(pos,block.getValue(BlockLiquid.LEVEL));
            if(!scanCanSpread && canSpreadFrom(pos,block.getValue(BlockLiquid.LEVEL)))scanCanSpread=true;
            int distance=flowDistance.get(pos);
            scanLongestPath=Math.max(scanLongestPath,distance);
            enqueueFlow(pos.down(),distance+1);
            for(EnumFacing side:EnumFacing.HORIZONTALS) enqueueFlow(pos.offset(side),distance+1);
        }
        return flowQueue.isEmpty();
    }
    private int settlingTicks() { return Math.max(flowStabilityTicks,liquidTickRate*4+20); }

    // Vanilla 1.12.2 prioritizes downward flow, then the horizontal directions
    // with the shortest path to a drop. Do not count unchosen side air as growth.
    private boolean canSpreadFrom(BlockPos pos,int level) {
        BlockPos below=pos.down();
        if(canReceiveLava(below))return true;
        if(level!=0 && !blocksLiquid(below))return false;
        int outgoing=level>=8?1:level+(mc.world.provider.doesWaterVaporize()?1:2);
        if(outgoing>=8)return false;
        int best=1000;
        boolean pending=false;
        for(EnumFacing side:EnumFacing.HORIZONTALS){
            BlockPos next=pos.offset(side);
            if(blocksLiquid(next))continue;
            IBlockState state=mc.world.getBlockState(next);
            if(state.getMaterial()==Material.LAVA && state.getValue(BlockLiquid.LEVEL)==0)continue;
            int cost=blocksLiquid(next.down())?slopeDistance(next,1,side.getOpposite()):0;
            if(cost<best){best=cost;pending=canReceiveLava(next);}
            else if(cost==best)pending|=canReceiveLava(next);
        }
        return pending;
    }
    private int slopeDistance(BlockPos pos,int distance,EnumFacing excluded) {
        int best=1000;
        for(EnumFacing side:EnumFacing.HORIZONTALS){
            if(side==excluded)continue;
            BlockPos next=pos.offset(side);
            if(blocksLiquid(next))continue;
            IBlockState state=mc.world.getBlockState(next);
            if(state.getMaterial()==Material.LAVA && state.getValue(BlockLiquid.LEVEL)==0)continue;
            if(!blocksLiquid(next.down()))return distance;
            if(distance<(mc.world.provider.doesWaterVaporize()?4:2))
                best=Math.min(best,slopeDistance(next,distance+1,side.getOpposite()));
        }
        return best;
    }
    private boolean blocksLiquid(BlockPos pos) {
        if(pos.getY()<0||pos.getY()>255)return true;
        if(!mc.world.isBlockLoaded(pos))return true; // Prediction must not invalidate an observed scan.
        IBlockState state=mc.world.getBlockState(pos);
        net.minecraft.block.Block b=state.getBlock();
        return state.getMaterial().blocksMovement() || state.getMaterial()==Material.PORTAL
                || state.getMaterial()==Material.STRUCTURE_VOID || b instanceof net.minecraft.block.BlockDoor
                || b==Blocks.STANDING_SIGN || b==Blocks.LADDER || b==Blocks.REEDS;
    }
    private boolean canReceiveLava(BlockPos pos) {
        return !blocksLiquid(pos) && mc.world.getBlockState(pos).getMaterial()!=Material.LAVA;
    }
    private void enqueueFlow(BlockPos pos,int distance) {
        if(!flowDistance.containsKey(pos)){flowDistance.put(pos,distance);flowQueue.add(pos);}
    }
    private boolean moveToTower(boolean edge) {
        double x=towerBase.getX()+.5+(edge?castFacing.getFrontOffsetX()*.62:0);
        double z=towerBase.getZ()+.5+(edge?castFacing.getFrontOffsetZ()*.62:0);
        double dx=x-mc.player.posX, dz=z-mc.player.posZ;
        mc.player.motionX=Math.max(-.09,Math.min(.09,dx*.5));
        mc.player.motionZ=Math.max(-.09,Math.min(.09,dz*.5));
        return dx*dx+dz*dz<.001;
    }

    private void collectLava() {
        if (!isLava(lavaPos)) { retryAction("source disappeared before collection", collectReturnsToPrepare ? State.PLACE_NEXT_LAVA : State.PLACE_LAVA); return; }
        int slot = selectItem(Items.BUCKET);
        if (slot < 0) { pause("empty bucket is unavailable"); return; }
        lavaBucketsBeforeCollect = InventoryManager.count(mc, Items.LAVA_BUCKET);
        emptyBucketsBeforeCollect = InventoryManager.count(mc, Items.BUCKET);
        mc.player.inventory.currentItem = slot;
        Vec3d hit = new Vec3d(lavaPos.getX() + .5D + (collectReturnsToPrepare?castFacing.getFrontOffsetX()*.499:0), lavaPos.getY() + .5D, lavaPos.getZ() + .5D + (collectReturnsToPrepare?castFacing.getFrontOffsetZ()*.499:0));
        rotateTo(hit);
        mc.playerController.updateController();
        PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerTryUseItem(EnumHand.MAIN_HAND),name);
        restoreRotation();
        afterAction();
        transition(State.VERIFY_COLLECT,"bucket collection requested");
    }

    private void verifyCollection() {
        boolean inventoryChanged = InventoryManager.count(mc, Items.LAVA_BUCKET) > lavaBucketsBeforeCollect
                || InventoryManager.count(mc, Items.BUCKET) < emptyBucketsBeforeCollect;
        if (inventoryChanged && !isLava(lavaPos)) {
            collectRetries = 0;
            if(collectReturnsToPrepare) {
                towerBase=upperBlockPos;
                lowerBlockPos=towerBase.up(); upperBlockPos=lowerBlockPos.up(); nextLavaPos=lowerBlockPos;
            }
            waterPos=towerBase.up();
            transition(State.PLACE_WATER, "lava recovered; water at current standing position");
        } else if (timedOut(operationTimeout())) {
            if (++collectRetries > placementRetryLimit) pause("lava collection was not confirmed");
            else transition(State.COLLECT_LAVA, "retry collection");
        }
    }

    private void breakLowerBlock() {
        if (!moveToTower(true)) return;
        if (mc.world.isAirBlock(lowerBlockPos)) { transition(State.PLACE_NEXT_LAVA, "lower block broken"); return; }
        if (!isSolid(lowerBlockPos) || playerIntersects(lowerBlockPos) || !withinReach(lowerBlockPos)) {
            pause("lower block cannot be broken safely"); return;
        }
        if (timedOut(operationTimeout() * 3)) { pause("lower block breaking timed out"); return; }
        if (!readyForAction()) return;
        int best=mc.player.inventory.currentItem; float speed=0;
        for(int slot=0;slot<9;slot++) {
            float value=mc.player.inventory.getStackInSlot(slot).getDestroySpeed(mc.world.getBlockState(lowerBlockPos));
            if(value>speed){speed=value;best=slot;}
        }
        mc.player.inventory.currentItem=best; mc.playerController.updateController();
        if(!miningStarted) {
            miningStarted=true; miningProgress=0; lastMineTick=ticks;
            PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerDigging(net.minecraft.network.play.client.CPacketPlayerDigging.Action.START_DESTROY_BLOCK,lowerBlockPos,castFacing),name);
        }
        if(!miningFinished && ticks>lastMineTick) {
            miningProgress+=mc.world.getBlockState(lowerBlockPos).getPlayerRelativeBlockHardness(mc.player,mc.world,lowerBlockPos);
            lastMineTick=ticks;
            if(miningProgress>=1) {
                PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerDigging(net.minecraft.network.play.client.CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK,lowerBlockPos,castFacing),name);
                miningFinished=true;
            }
        }
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }

    private boolean validBuildGeometry() {
        return lowerBlockPos.getY()<254 && mc.world.isAirBlock(lowerBlockPos) && mc.world.isAirBlock(upperBlockPos)
                && mc.world.isAirBlock(upperBlockPos.up()) && isSolid(towerBase);
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

    private boolean placeWaterAtFeet(int slot) {
        if (!mc.world.isAirBlock(waterPos) || !isSolid(towerBase)) return false;
        Vec3d hit = new Vec3d(towerBase.getX() + .5D, towerBase.getY() + 1D, towerBase.getZ() + .5D);
        Vec3d eye = mc.player.getPositionEyes(1F);
        if (!withinReach(hit)) return false;
        net.minecraft.util.math.RayTraceResult ray = mc.world.rayTraceBlocks(eye,
                hit.addVector(0D, -.001D, 0D), false, true, false);
        if (ray == null || !towerBase.equals(ray.getBlockPos()) || ray.sideHit != EnumFacing.UP) return false;
        mc.player.inventory.currentItem = slot;
        mc.playerController.updateController();
        rotateTo(hit);
        PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerTryUseItem(EnumHand.MAIN_HAND), name);
        restoreRotation();
        mc.player.swingArm(EnumHand.MAIN_HAND);
        return true;
    }

    private boolean placeAgainstSupport(BlockPos target, int slot) {
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos support = target.offset(side);
            // Buckets may legitimately target slabs, stairs, and other collidable
            // non-full blocks. Full cubes are only required for the cast structure.
            if (!isPlacementSupport(support)) continue;
            EnumFacing face = side.getOpposite();
            Vec3d hit = InventoryBlocks.hitVec(support, face);
            if(mc.player.inventory.getStackInSlot(slot).getItem()==Items.LAVA_BUCKET && (face==EnumFacing.UP || face==EnumFacing.DOWN))
                hit=hit.addVector(castFacing.getFrontOffsetX()*.499,0,castFacing.getFrontOffsetZ()*.499);
            if (mc.player.getPositionEyes(1F).squareDistanceTo(hit) > mc.playerController.getBlockReachDistance() * mc.playerController.getBlockReachDistance()) continue;
            net.minecraft.util.math.RayTraceResult ray=mc.world.rayTraceBlocks(mc.player.getPositionEyes(1F),hit.addVector(-face.getFrontOffsetX()*.001,-face.getFrontOffsetY()*.001,-face.getFrontOffsetZ()*.001),false,true,false);
            if(ray==null || !support.equals(ray.getBlockPos())) continue;
            mc.player.inventory.currentItem = slot;
            rotateTo(hit);
            mc.playerController.updateController();
            if(mc.player.getHeldItemMainhand().getItem()==Items.LAVA_BUCKET || mc.player.getHeldItemMainhand().getItem()==Items.WATER_BUCKET)
                PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerTryUseItem(EnumHand.MAIN_HAND),name);
            else PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(support,face,EnumHand.MAIN_HAND,
                    (float)(hit.x-support.getX()),(float)(hit.y-support.getY()),(float)(hit.z-support.getZ())),name);
            restoreRotation(); mc.player.swingArm(EnumHand.MAIN_HAND); return true;
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
    private int operationTimeout() { return Math.max(60, actionDelayTicks * 4 + 10); }
    private boolean timedOut(int limit) { return ticks - stateEnteredTick > limit; }
    private void afterAction() { nextActionTick = ticks + Math.max(0, actionDelayTicks); }

    private void retryAction(String reason, State retryState) {
        if (++retries > placementRetryLimit) { pause(reason + " (retry limit)"); return; }
        flow = null;
        afterAction();
        transition(retryState, reason + " (retry " + retries + ")");
    }

    private void transition(State next, String reason) {
        if(next==State.BREAK_LOWER) { miningStarted=false; miningFinished=false; miningProgress=0; }
        state = next;
        stateEnteredTick = ticks;
        LOGGER.info("{} -> {}: {}", name, next, reason);
    }

    private static String format(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void pause(String reason) {
        stopReason=reason;
        flow = null;
        collectRetries = retries = 0;
        nextActionTick = ticks + 20;
        transition(State.IDLE, "paused: " + reason);
        if(mc.player!=null)mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("LavaCast paused: "+reason+". Toggle to restart."));
    }

    private void resetCycle() {
        ticks = stateEnteredTick = nextActionTick = retries = collectRetries = 0;
        stopReason=""; waterPos=null; observedWaterDepth=0; completedCycles=0; castFloorY=Integer.MAX_VALUE;
        flowQueue.clear(); flowVisited.clear(); flowSample.clear(); flowDistance.clear();
        flow = null; collectReturnsToPrepare = false;
        lavaPos = lowerBlockPos = airGapPos = upperBlockPos = nextLavaPos = null;
    }

    @Override public void onInputUpdate(net.minecraftforge.client.event.InputUpdateEvent event) {
        if(state==State.IDLE || isAutomationBlockedByScreen())return;
        event.getMovementInput().moveForward=0; event.getMovementInput().moveStrafe=0;
        event.getMovementInput().jump=false; event.getMovementInput().sneak=false;
    }
    @Override protected void onDisable() {
        if(miningStarted && !miningFinished && mc.getConnection()!=null && lowerBlockPos!=null)
            PacketManager.send(new net.minecraft.network.play.client.CPacketPlayerDigging(net.minecraft.network.play.client.CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK,lowerBlockPos,castFacing),name);
        miningStarted=miningFinished=false;
        if(mc.playerController!=null)mc.playerController.resetBlockRemoving();
        if (mc.player != null && savedHotbarSlot >= 0 && savedHotbarSlot < 9) mc.player.inventory.currentItem = savedHotbarSlot;
        savedHotbarSlot = -1;
        resetCycle();
    }
    @Override protected void onWorldUnload() { onDisable(); }
    @Override public String hudTag() {
        if(state==State.IDLE)return "[Paused: "+stopReason+"]";
        if(state==State.WAIT_WATER)return "[Water "+observedWaterDepth+"/"+waterReachDepth+" below feet]";
        if(state!=State.WAIT_FLOW || flow==null)return "["+state.name()+"]";
        String reason=displayedIncomplete?"incomplete scan":
                (displayedCanSpread?"checking edges ":"settling ")+
                ((flow.remainingSettlingTicks(ticks,displayedCanSpread,settlingTicks(),edgeVerificationTicks)+19)/20)+"s";
        return "["+displayedFlowBlocks+" tracked / "+reason+"]";
    }

    /** Pure observed-state tracker; also used by the offline diagnostic harness. */
    public static final class FlowTracker {
        public final BlockPos origin;
        public final int placementTick, maxTicks;
        public boolean sourceBlock, sawLava;
        public int lastChangedTick, lastFootprintChangedTick, unchangedFootprintScans;
        public FlowStatus status = FlowStatus.TRACKING;
        public Set<EnumFacing> observedDirections = Collections.emptySet();
        public Map<BlockPos, Integer> neighboringLava = Collections.emptyMap();
        private Map<BlockPos, Integer> previous = Collections.emptyMap();

        public FlowTracker(BlockPos origin, int placementTick, int maxTicks) {
            this.origin = origin.toImmutable(); this.placementTick = placementTick; this.maxTicks = Math.max(1, maxTicks);
            this.lastChangedTick = this.lastFootprintChangedTick = placementTick;
        }

        public void observe(int tick, Map<BlockPos, Integer> current, boolean chunksLoaded) {
            if (status != FlowStatus.TRACKING) return;
            if (!chunksLoaded) { status = FlowStatus.INVALID; return; }
            Map<BlockPos, Integer> copy = new LinkedHashMap<BlockPos, Integer>(current);
            Integer originLevel = copy.get(origin);
            if (originLevel != null) { sawLava = true; sourceBlock = originLevel.intValue() == 0; }
            if (sawLava && originLevel == null) { status = FlowStatus.INVALID; return; }
            if (!copy.keySet().equals(previous.keySet())) {
                lastFootprintChangedTick=tick; unchangedFootprintScans=0;
            } else unchangedFootprintScans++;
            if (!copy.equals(previous)) lastChangedTick = tick;
            previous = copy;
            neighboringLava = Collections.unmodifiableMap(copy);
            EnumSet<EnumFacing> directions = EnumSet.noneOf(EnumFacing.class);
            for (EnumFacing face : EnumFacing.HORIZONTALS) if (copy.containsKey(origin.offset(face))) directions.add(face);
            observedDirections = Collections.unmodifiableSet(directions);
            if (tick - placementTick >= maxTicks) { status = FlowStatus.TIMED_OUT; return; }
        }

        /** Predictions may extend verification once, but cannot veto stable observations forever. */
        public void observeCompleteScan(int tick,Map<BlockPos,Integer> sample,boolean complete,
                                        boolean predictedGrowth,int quietTicks,int edgeTicks) {
            if(status!=FlowStatus.TRACKING)return;
            if(!complete){
                lastChangedTick=lastFootprintChangedTick=tick; unchangedFootprintScans=0;
                if(tick-placementTick>=maxTicks)status=FlowStatus.TIMED_OUT;
                return;
            }
            observe(tick,sample,true);
            if(status!=FlowStatus.TRACKING || !sourceBlock || unchangedFootprintScans<2)return;
            if(remainingSettlingTicks(tick,predictedGrowth,quietTicks,edgeTicks)==0)status=FlowStatus.STABILIZED;
        }
        public int remainingSettlingTicks(int tick,boolean predictedGrowth,int quietTicks,int edgeTicks) {
            int window=Math.max(1,quietTicks);
            int fullQuiet=Math.max(0,window-(tick-lastChangedTick));
            int footprintQuiet=Math.max(0,Math.max(20,edgeTicks)-(tick-lastFootprintChangedTick));
            return predictedGrowth?footprintQuiet:Math.min(fullQuiet,footprintQuiet);
        }
        public void stabilizeIfUnchanged(int tick, int stabilityTicks) {
            if (status == FlowStatus.TRACKING && sourceBlock && tick - lastChangedTick >= Math.max(1, stabilityTicks)) status = FlowStatus.STABILIZED;
        }
    }

    public enum FlowStatus { TRACKING, STABILIZED, TIMED_OUT, INVALID }
}
