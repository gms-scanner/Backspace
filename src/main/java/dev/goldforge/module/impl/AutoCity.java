package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.GoldForge;
import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mines an exposed obsidian or ender-chest city block beside the nearest enemy. */
public final class AutoCity extends Module {
    public int targetRange=5,friendFilter=1,autoTool=1,autoRemine=1,rebreakWindow=40,multiMine=0;
    private BlockPos mining,lastBroken;
    private EnumFacing miningFace;
    private EntityPlayer victim;
    private UUID lastVictim;
    private int ticks,lastBrokenTick,originalSlot=-1,selectedTool=-1;
    private long miningRevision,visualStarted;
    private final List<BlockPos> previews=new ArrayList<BlockPos>();

    public AutoCity(){
        super("AutoCity","Mines a reachable obsidian or ender-chest block beside the nearest player.",Category.COMBAT,0);
        addIntSetting("Target range",2,6,1,()->targetRange,v->targetRange=v);
        addIntSetting("Ignore friends (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
        addIntSetting("Select best tool (0/1)",0,1,1,()->autoTool,v->autoTool=v);
        addIntSetting("Auto re-mine (0/1)",0,1,1,()->autoRemine,v->autoRemine=v);
        addIntSetting("Re-mine window (ticks)",1,100,1,()->rebreakWindow,v->rebreakWindow=v);
        addIntSetting("MultiMine queue (0/1)",0,1,1,()->multiMine,v->multiMine=v);
    }

    @Override public void tick(){
        ticks++;
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null){reset();return;}
        Module autoMine=GoldForge.MODULES.get("AutoMine");
        if(autoMine!=null&&autoMine.enabled&&!autoMine.isSuspended()){reset();return;}
        EntityPlayer target=nearestTarget();
        if(target==null){reset();return;}
        BlockPos feet=new BlockPos(target.posX,target.posY,target.posZ);
        if(mining!=null){
            if(victim==target&&!validCityBlock(mining)){
                lastBroken=mining;lastVictim=target.getUniqueID();lastBrokenTick=ticks;
            }
            EnumFacing face=validCityBlock(mining)?reachableFace(mining):null;
            if(victim!=target||face==null||!nearTarget(mining,feet))reset();
            else{
                long revision=RecentSurroundBlocks.revision(mining,rebreakWindow);
                if(autoRemine!=0&&revision!=0L&&revision!=miningRevision){
                    mc.playerController.resetBlockRemoving();
                    miningRevision=revision;visualStarted=System.currentTimeMillis();
                }
                miningFace=face;
            }
        }
        BlockPos best=null;EnumFacing bestFace=null;double bestScore=Double.MAX_VALUE;
        previews.clear();
        for(EnumFacing side:EnumFacing.HORIZONTALS){
            BlockPos candidate=feet.offset(side);
            if(!validCityBlock(candidate))continue;
            EnumFacing face=reachableFace(candidate);
            if(face==null)continue;
            if(multiMine!=0&&!candidate.equals(mining))previews.add(candidate.toImmutable());
            double score=mc.player.getDistanceSq(candidate.getX()+.5D,candidate.getY()+.5D,candidate.getZ()+.5D);
            if(autoRemine!=0&&RecentSurroundBlocks.revision(candidate,rebreakWindow)!=0L)score-=100D;
            if(autoRemine!=0&&candidate.equals(lastBroken)&&target.getUniqueID().equals(lastVictim)
                    && ticks-lastBrokenTick<=rebreakWindow)score-=200D;
            if(score<bestScore){bestScore=score;best=candidate;bestFace=face;}
        }
        if(mining==null){
            if(multiMine==0&&autoRemine!=0&&lastBroken!=null&&target.getUniqueID().equals(lastVictim)
                    && ticks-lastBrokenTick<=rebreakWindow&&!validCityBlock(lastBroken))return;
            if(best==null)return;
            mining=best.toImmutable();miningFace=bestFace;victim=target;
            miningRevision=RecentSurroundBlocks.revision(mining,rebreakWindow);
            visualStarted=System.currentTimeMillis();
        }
        previews.remove(mining);
        if(autoTool!=0){
            int tool=InventoryManager.bestHotbarTool(mc,mc.world.getBlockState(mining));
            if(tool>=0){
                if(originalSlot<0)originalSlot=mc.player.inventory.currentItem;
                mc.player.inventory.currentItem=tool;selectedTool=tool;
            }
        }
        mc.playerController.onPlayerDamageBlock(mining,miningFace);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
    }

    private boolean validCityBlock(BlockPos pos){
        if(!mc.world.isBlockLoaded(pos))return false;
        Block block=mc.world.getBlockState(pos).getBlock();
        return block==Blocks.OBSIDIAN||block==Blocks.ENDER_CHEST;
    }

    private boolean nearTarget(BlockPos pos,BlockPos feet){
        return pos.getY()==feet.getY()&&Math.abs(pos.getX()-feet.getX())<=1&&Math.abs(pos.getZ()-feet.getZ())<=1;
    }

    private EnumFacing reachableFace(BlockPos pos){
        Vec3d eyes=mc.player.getPositionEyes(1F);
        double reach=Math.min(targetRange,mc.playerController.getBlockReachDistance())-.1D;
        for(EnumFacing face:EnumFacing.values()){
            Vec3d hit=InventoryBlocks.hitVec(pos,face).addVector(face.getFrontOffsetX()*.01D,face.getFrontOffsetY()*.01D,face.getFrontOffsetZ()*.01D);
            if(eyes.squareDistanceTo(hit)>reach*reach)continue;
            RayTraceResult trace=mc.world.rayTraceBlocks(eyes,hit,false,true,false);
            if(trace!=null&&trace.typeOfHit==RayTraceResult.Type.BLOCK&&pos.equals(trace.getBlockPos()))return trace.sideHit;
        }
        return null;
    }

    private EntityPlayer nearestTarget(){
        EntityPlayer best=null;double bestDistance=Double.MAX_VALUE;double range=Math.min(targetRange,mc.playerController.getBlockReachDistance());
        for(EntityPlayer candidate:mc.world.playerEntities){
            if(candidate==mc.player||candidate.isDead||candidate.getHealth()<=0F||candidate.isSpectator())continue;
            if(friendFilter!=0&&FriendManager.isFriend(candidate.getName()))continue;
            double distance=mc.player.getDistanceSq(candidate);if(distance>range*range||distance>=bestDistance)continue;
            best=candidate;bestDistance=distance;
        }
        return best;
    }

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null)return;
        if(mining!=null&&validCityBlock(mining))MiningVisuals.draw(mc,mining,
                MiningVisuals.progress(mc,mining),visualStarted,false);
        if(multiMine!=0)for(BlockPos pos:previews)
            if(validCityBlock(pos))MiningVisuals.draw(mc,pos,0F,visualStarted,false);
    }

    private void reset(){
        if(mining!=null&&mc.playerController!=null)mc.playerController.resetBlockRemoving();
        if(mc.player!=null&&originalSlot>=0&&mc.player.inventory.currentItem==selectedTool)
            mc.player.inventory.currentItem=originalSlot;
        mining=null;miningFace=null;victim=null;previews.clear();
        originalSlot=selectedTool=-1;
    }
    @Override protected void onDisable(){reset();lastBroken=null;}
    @Override protected void onWorldUnload(){reset();lastBroken=null;RecentSurroundBlocks.clear();}
}
