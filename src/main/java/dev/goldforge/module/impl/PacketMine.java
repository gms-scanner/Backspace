package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

/** Single-target packet mining with bounded timing and authoritative block-change observation. */
public final class PacketMine extends Module {
    public int pauseManualAttack=1,stopDelay=0,confirmTimeout=20,retryLimit=2;
    private BlockPos target;
    private EnumFacing face;
    private int ticks,elapsed,required,retries,waitingSince;
    private boolean stopSent,confirmed;
    private String state="Idle";

    public PacketMine(){
        super("PacketMine","Sends bounded start/stop mining actions and waits for block updates.",Category.WORLD,0);
        addIntSetting("Pause during manual attack (0/1)",0,1,1,()->pauseManualAttack,v->pauseManualAttack=v);
        addIntSetting("Extra stop delay (ticks)",0,20,1,()->stopDelay,v->stopDelay=v);
        addIntSetting("Confirm timeout (ticks)",4,80,1,()->confirmTimeout,v->confirmTimeout=v);
        addIntSetting("Retry attempts",0,5,1,()->retryLimit,v->retryLimit=v);
    }
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.getConnection()==null||isAutomationBlockedByScreen()){
            abort();return;
        }
        ticks++;
        if(pauseManualAttack!=0&&mc.gameSettings.keyBindAttack.isKeyDown()){abort();return;}
        dev.goldforge.module.Module autoMine=dev.goldforge.GoldForge.MODULES.get("AutoMine");
        dev.goldforge.module.Module autoCity=dev.goldforge.GoldForge.MODULES.get("AutoCity");
        if(autoMine!=null&&autoMine.enabled||autoCity!=null&&autoCity.enabled){abort();return;}
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK){abort();return;}
        BlockPos pos=hit.getBlockPos();
        double reach=mc.playerController.getBlockReachDistance();
        if(pos==null||!mc.world.isBlockLoaded(pos)||mc.player.getDistanceSq(
                pos.getX()+.5D,pos.getY()+.5D,pos.getZ()+.5D)>reach*reach){abort();return;}
        IBlockState block=mc.world.getBlockState(pos);
        if(block.getMaterial().isReplaceable()||block.getBlockHardness(mc.world,pos)<0F){abort();return;}
        if(target==null||!target.equals(pos)){
            abort();target=pos.toImmutable();face=hit.sideHit;elapsed=0;retries=0;confirmed=false;
            float progress=block.getPlayerRelativeBlockHardness(mc.player,mc.world,target);
            if(progress<=0F){state="Unbreakable";abort();return;}
            required=Math.max(1,(int)Math.ceil(1D/progress))+Math.max(0,stopDelay);
            if(PacketManager.send(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK,target,face),name))
                state="Mining";
            else {abort();return;}
        }
        if(!stopSent){
            if(++elapsed<required)return;
            if(PacketManager.send(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK,target,face),name)){
                stopSent=true;waitingSince=ticks;state="Awaiting block update";
            }
            return;
        }
        if(confirmed){state="Server block update";target=null;stopSent=false;return;}
        if(mc.world.getBlockState(target).getMaterial().isReplaceable()){
            state="Local air; awaiting server update";
            if(ticks-waitingSince>=Math.max(4,confirmTimeout)){target=null;stopSent=false;state="Local air; unconfirmed";}
            return;
        }
        if(ticks-waitingSince<Math.max(4,confirmTimeout))return;
        if(retries++>=Math.max(0,retryLimit)){state="Unconfirmed";abort();return;}
        PacketManager.send(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK,target,face),name);
        elapsed=0;stopSent=false;state="Retrying";
    }
    /** Called from the packet observer on the client thread. */
    public void onServerBlockChange(BlockPos pos,IBlockState block){
        if(target!=null&&target.equals(pos)&&block.getMaterial().isReplaceable())confirmed=true;
    }
    private void abort(){
        if(target!=null&&!stopSent&&mc.getConnection()!=null)
            PacketManager.send(new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK,target,face),name);
        target=null;face=null;elapsed=required=0;stopSent=false;
    }
    @Override protected void onDisable(){abort();state="Idle";}
    @Override protected void onWorldUnload(){target=null;face=null;stopSent=false;state="Idle";}
    @Override public String hudTag(){return "["+state+"]";}
}
