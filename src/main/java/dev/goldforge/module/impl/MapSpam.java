package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import java.util.*;

/** Places real inventory maps into visible, supported item frames. */
public final class MapSpam extends Module {
    public int searchRadius=12,delayTicks=4,useBaritone=1,mapId=-1;
    private int ticks,nextAction,originalSlot=-1,started;
    private BlockPos target,goal;
    private EnumFacing face;
    private Object ownedProcess;
    private final Map<BlockPos,Integer> cooldowns=new HashMap<BlockPos,Integer>();
    public MapSpam(){
        super("MapSpam","Places item frames and filled maps on reachable walls, with optional Baritone travel.",Category.WORLD,0);
        addIntSetting("Search radius",4,24,1,()->searchRadius,v->searchRadius=v);
        addIntSetting("Action delay",2,40,1,()->delayTicks,v->delayTicks=v);
        addIntSetting("Baritone (0/1)",0,1,1,()->useBaritone,v->useBaritone=v);
        addIntSetting("Map id (-1 any)",-1,32767,1,()->mapId,v->mapId=v);
    }
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        ticks++;
        if(isAutomationBlockedByScreen()){cancel();return;}
        if(ticks<nextAction)return;
        if(find(Items.FILLED_MAP)<0){cancel();return;}
        if(target==null){if(ticks%10!=0)return;choose();if(target==null)return;started=ticks;}
        if(ticks-started>200||!valid(target,face)){abandon();return;}
        EntityItemFrame frame=frameAt(target);
        if(frame!=null&&!frame.getDisplayedItem().isEmpty()){abandon();return;}
        Vec3d hit=new Vec3d(target.offset(face.getOpposite())).addVector(.5,.5,.5).add(new Vec3d(face.getDirectionVec()).scale(.5));
        Vec3d aim=frame==null?hit:frame.getPositionVector().addVector(0,.25,0);
        RayTraceResult ray=mc.world.rayTraceBlocks(mc.player.getPositionEyes(1),hit,false,true,false);
        boolean visible=ray!=null&&ray.typeOfHit==RayTraceResult.Type.BLOCK&&ray.getBlockPos().equals(target.offset(face.getOpposite()));
        if(mc.player.getPositionEyes(1).squareDistanceTo(aim)>16||!visible){
            if(useBaritone==0||goal==null){abandon();return;}
            if(ownedProcess==null&&!path(goal)){abandon();return;}
            return;
        }
        cancel();
        if(!select(frame==null?Items.ITEM_FRAME:Items.FILLED_MAP)){abandon();return;}
        Vec3d d=aim.subtract(mc.player.getPositionEyes(1));
        float yaw=(float)Math.toDegrees(Math.atan2(d.z,d.x))-90;
        float pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)));
        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw,pitch,mc.player.onGround));
        mc.playerController.updateController();
        if(frame==null)mc.playerController.processRightClickBlock(mc.player,mc.world,target.offset(face.getOpposite()),face,hit,EnumHand.MAIN_HAND);
        else mc.playerController.interactWithEntity(mc.player,frame,EnumHand.MAIN_HAND);
        mc.player.swingArm(EnumHand.MAIN_HAND);
        nextAction=ticks+Math.max(2,delayTicks);
    }
    private void choose(){
        cooldowns.entrySet().removeIf(e->e.getValue()<ticks);
        BlockPos origin=mc.player.getPosition();double best=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.getAllInBoxMutable(origin.add(-searchRadius,-3,-searchRadius),origin.add(searchRadius,3,searchRadius))){
            if(cooldowns.containsKey(p)||!mc.world.isBlockLoaded(p)||!mc.world.isAirBlock(p))continue;
            double score=p.distanceSq(origin);if(score>=best)continue;
            for(EnumFacing f:EnumFacing.HORIZONTALS){
                if(!valid(p,f))continue;
                EntityItemFrame frame=frameAt(p);
                if(frame!=null&&!frame.getDisplayedItem().isEmpty())continue;
                if(frame==null&&find(Items.ITEM_FRAME)<0)continue;
                BlockPos stand=null;
                for(int distance=1;distance<=3;distance++){
                    BlockPos s=p.offset(f,distance).down();
                    if(mc.world.isBlockLoaded(s)&&mc.world.getBlockState(s.down()).isFullCube()&&mc.world.isAirBlock(s)&&mc.world.isAirBlock(s.up())){stand=s;break;}
                }
                if(stand==null)continue;
                best=score;target=p.toImmutable();face=f;goal=stand;break;
            }
        }
    }
    private boolean valid(BlockPos p,EnumFacing f){
        if(f==null||!mc.world.isBlockLoaded(p)||!mc.world.isAirBlock(p))return false;
        BlockPos support=p.offset(f.getOpposite());
        return mc.world.getBlockState(support).isSideSolid(mc.world,support,f);
    }
    private EntityItemFrame frameAt(BlockPos p){
        for(EntityItemFrame frame:mc.world.getEntitiesWithinAABB(EntityItemFrame.class,new AxisAlignedBB(p).grow(.2)))
            if(frame.getHangingPosition().equals(p))return frame;
        return null;
    }
    private int find(Item item){
        for(int i=0;i<36;i++)if(!mc.player.inventory.getStackInSlot(i).isEmpty()&&mc.player.inventory.getStackInSlot(i).getItem()==item
            &&(item!=Items.FILLED_MAP||mapId<0||mc.player.inventory.getStackInSlot(i).getMetadata()==mapId))return i;
        return -1;
    }
    private boolean select(Item item){
        int slot=find(item);if(slot<0)return false;
        if(originalSlot<0)originalSlot=mc.player.inventory.currentItem;
        if(slot>=9){if(!InventoryManager.moveToHotbar(mc,slot,originalSlot))return false;slot=originalSlot;}
        mc.player.inventory.currentItem=slot;return true;
    }
    private boolean path(BlockPos pos){
        try{
            Object provider=Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
            Object b=Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
            Object process=Class.forName("baritone.api.IBaritone").getMethod("getCustomGoalProcess").invoke(b);
            if((Boolean)Class.forName("baritone.api.process.IBaritoneProcess").getMethod("isActive").invoke(process))return false;
            Object g=Class.forName("baritone.api.pathing.goals.GoalBlock").getConstructor(int.class,int.class,int.class).newInstance(pos.getX(),pos.getY(),pos.getZ());
            Class.forName("baritone.api.process.ICustomGoalProcess").getMethod("setGoalAndPath",Class.forName("baritone.api.pathing.goals.Goal")).invoke(process,g);
            ownedProcess=process;return true;
        }catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
    private void cancel(){
        if(ownedProcess==null)return;
        try{Class.forName("baritone.api.process.IBaritoneProcess").getMethod("onLostControl").invoke(ownedProcess);}catch(ReflectiveOperationException|LinkageError ignored){}
        ownedProcess=null;
    }
    private void abandon(){cancel();if(target!=null)cooldowns.put(target,ticks+200);target=goal=null;}
    @Override protected void onDisable(){cancel();if(originalSlot>=0&&mc.player!=null)mc.player.inventory.currentItem=originalSlot;originalSlot=-1;target=goal=null;cooldowns.clear();ticks=nextAction=0;}
}
