package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.InputUpdateEvent;

import java.util.*;

/** Finds a nearby reachable log, walks a collision-aware path, and lights it once. */
public final class TreeBurner extends Module {
    private BlockPos target;
    private List<BlockPos> route = Collections.emptyList();
    private int routeIndex;
    private int rescanTicks;
    private int cooldown;
    private boolean movingForward;
    private boolean jumping;
    public int searchRadius=18;
    private final Set<BlockPos> ignited = new HashSet<BlockPos>();

    public TreeBurner() {
        super("TreeBurner", "Pathfinds to nearby logs and ignites them using flint and steel.", Category.WORLD, 0);
        addIntSetting("Search radius", 4, 32, 2, () -> searchRadius, value -> searchRadius=value);
    }
    @Override protected void onEnable() { ignited.clear(); target=null; route=Collections.emptyList(); routeIndex=0; rescanTicks=0; }

    @Override public void tick() {
        if (mc.player==null || mc.world==null) return;
        if (cooldown>0) cooldown--;
        if (target==null && rescanTicks--<=0) {
            Plan plan=findPlan();
            target=plan==null ? null : plan.log;
            route=plan==null ? Collections.<BlockPos>emptyList() : plan.path;
            routeIndex=route.size()>1 ? 1 : 0;
            rescanTicks=20;
        }
        if (target==null) { stopMoving(); return; }
        int fireSlot=findFlintAndSteel();
        if (fireSlot<0) { stopMoving(); return; }

        if (mc.player.getDistanceSqToCenter(target)<=Math.pow(mc.playerController.getBlockReachDistance(),2)) {
            stopMoving();
            if (cooldown==0 && igniteVisibleFace()) {
                cooldown=14;
            } else if(cooldown==0) {
                // Keep the same target and retry after the player's rotation/interaction
                // state has updated; don't mark a failed click as completed.
                cooldown=3;
            }
            return;
        }

        followRoute();
    }

    private boolean igniteVisibleFace() {
        Vec3d eye=mc.player.getPositionEyes(1.0F);
        double best=Double.MAX_VALUE;
        net.minecraft.util.math.RayTraceResult bestHit=null;
        for(EnumFacing face:EnumFacing.values()) {
            Vec3d point=new Vec3d(target.getX()+.5+face.getFrontOffsetX()*.501D,
                    target.getY()+.5+face.getFrontOffsetY()*.501D,
                    target.getZ()+.5+face.getFrontOffsetZ()*.501D);
            double distance=eye.squareDistanceTo(point);
            if(distance>mc.playerController.getBlockReachDistance()*mc.playerController.getBlockReachDistance()||distance>=best) continue;
            net.minecraft.util.math.RayTraceResult hit=mc.world.rayTraceBlocks(eye,point,false,true,false);
            if(hit!=null&&hit.typeOfHit==net.minecraft.util.math.RayTraceResult.Type.BLOCK&&target.equals(hit.getBlockPos())) { best=distance;bestHit=hit; }
        }
        if(bestHit==null) return false;
        Vec3d point=bestHit.hitVec;
        double dx=point.x-mc.player.posX;
        double dy=point.y-(mc.player.posY+mc.player.getEyeHeight());
        double dz=point.z-mc.player.posZ;
        mc.player.rotationYaw=(float)(MathHelper.atan2(dz,dx)*180.0D/Math.PI)-90.0F;
        mc.player.rotationPitch=(float)(-MathHelper.atan2(dy,Math.sqrt(dx*dx+dz*dz))*180.0D/Math.PI);
        int fireSlot=findFlintAndSteel();
        if(fireSlot<0) return false;
        mc.player.inventory.currentItem=fireSlot;
        net.minecraft.util.EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,bestHit.getBlockPos(),bestHit.sideHit,bestHit.hitVec,EnumHand.MAIN_HAND);
        if(result==net.minecraft.util.EnumActionResult.SUCCESS) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
            ignited.add(target);
            target=null; route=Collections.emptyList();
            return true;
        }
        return false;
    }

    private void followRoute() {
        if (routeIndex>=route.size()) { stopMoving(); target=null; rescanTicks=0; return; }
        BlockPos waypoint=route.get(routeIndex);
        double dx=waypoint.getX()+.5-mc.player.posX, dz=waypoint.getZ()+.5-mc.player.posZ;
        if (Math.abs(dx)<.35 && Math.abs(dz)<.35 && Math.abs(waypoint.getY()-mc.player.posY)<1.25) {
            routeIndex++;
            if (routeIndex>=route.size()) { stopMoving(); return; }
            waypoint=route.get(routeIndex); dx=waypoint.getX()+.5-mc.player.posX; dz=waypoint.getZ()+.5-mc.player.posZ;
        }
        mc.player.rotationYaw=(float)(MathHelper.atan2(dz,dx)*180.0D/Math.PI)-90.0F;
        movingForward=true;
        jumping=(waypoint.getY()>MathHelper.floor(mc.player.posY) || mc.player.collidedHorizontally) && mc.player.onGround;
    }

    @Override public void onInputUpdate(InputUpdateEvent event) {
        if(event.getEntityPlayer()!=mc.player || target==null) return;
        if(movingForward) event.getMovementInput().moveForward=1.0F;
        if(jumping) event.getMovementInput().jump=true;
        movingForward=false;
        jumping=false;
    }

    private Plan findPlan() {
        BlockPos origin=new BlockPos(mc.player.posX,mc.player.posY,mc.player.posZ);
        Queue<BlockPos> queue=new ArrayDeque<BlockPos>();
        Map<BlockPos,BlockPos> parent=new HashMap<BlockPos,BlockPos>();
        Map<BlockPos,Integer> distance=new HashMap<BlockPos,Integer>();
        BlockPos start=origin.toImmutable(); queue.add(start); parent.put(start,null); distance.put(start,0);
        EnumFacing[] directions={EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST};
        while(!queue.isEmpty() && queue.size()+parent.size()<2500) {
            BlockPos current=queue.remove(); int d=distance.get(current);
            if(d>=searchRadius*2) continue;
            for(EnumFacing direction:directions) {
                BlockPos horizontal=current.offset(direction);
                BlockPos[] candidates={horizontal.up(),horizontal,horizontal.down()};
                for(BlockPos next:candidates) {
                    if(Math.abs(next.getY()-origin.getY())>5 || Math.abs(next.getX()-origin.getX())>searchRadius || Math.abs(next.getZ()-origin.getZ())>searchRadius || parent.containsKey(next) || !canStand(next)) continue;
                    BlockPos stable=next.toImmutable(); parent.put(stable,current); distance.put(stable,d+1); queue.add(stable);
                }
            }
        }
        BlockPos bestLog=null, bestGoal=null; int bestDistance=Integer.MAX_VALUE;
        for(BlockPos log:BlockPos.getAllInBoxMutable(origin.add(-searchRadius,-4,-searchRadius),origin.add(searchRadius,6,searchRadius))) {
            Block block=mc.world.getBlockState(log).getBlock();
            if ((block!=Blocks.LOG && block!=Blocks.LOG2) || ignited.contains(log)) continue;
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
                if(x==0&&z==0) continue;
                BlockPos goal=new BlockPos(log.getX()+x,log.getY(),log.getZ()+z);
                Integer pathDistance=distance.get(goal);
                if(pathDistance!=null && pathDistance<bestDistance) { bestDistance=pathDistance; bestLog=log.toImmutable(); bestGoal=goal; }
            }
        }
        if(bestLog==null) return null;
        List<BlockPos> path=new ArrayList<BlockPos>();
        for(BlockPos p=bestGoal;p!=null;p=parent.get(p)) path.add(p);
        Collections.reverse(path);
        return new Plan(bestLog,path);
    }

    private boolean canStand(BlockPos feet) {
        if (mc.world.getBlockState(feet).getCollisionBoundingBox(mc.world,feet)!=null) return false;
        if (mc.world.getBlockState(feet.up()).getCollisionBoundingBox(mc.world,feet.up())!=null) return false;
        BlockPos floor=feet.down();
        return mc.world.getBlockState(floor).isSideSolid(mc.world,floor,EnumFacing.UP);
    }

    private int findFlintAndSteel() {
        int slot=InventoryManager.findItem(mc,Items.FLINT_AND_STEEL);if(slot<0)return -1;
        if(slot<9)return slot;
        return InventoryManager.moveToHotbar(mc,slot,0)?0:-1;
    }

    private void stopMoving() {
        movingForward=false; jumping=false;
        if(mc.gameSettings!=null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
        }
    }
    @Override protected void onDisable() { stopMoving(); target=null; route=Collections.emptyList(); }

    private static final class Plan {
        final BlockPos log; final List<BlockPos> path;
        Plan(BlockPos log,List<BlockPos> path){this.log=log;this.path=path;}
    }
}

