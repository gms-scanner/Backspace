package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.block.material.Material;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;

/** Detects a fall with no support below and attempts server-owned Elytra recovery. */
public final class AntiVoid extends Module {
    /** 0 Warn, 1 Elytra, 2 Elytra then disconnect near void. */
    public int mode=1,triggerY=20,disconnectY=3,minimumFall=4,scanRadius=1;
    private boolean warned,elytraRequested,disconnected;
    private int retry,attempts;
    private String state="Safe";
    public AntiVoid(){
        super("AntiVoid","Warns below unsupported terrain and optionally starts Elytra flight.",Category.MOVEMENT,0);
        addIntSetting("Mode (0 Warn, 1 Elytra, 2 Elytra+Disconnect)",0,2,1,()->mode,v->mode=v);
        addIntSetting("Trigger below Y",5,80,1,()->triggerY,v->triggerY=v);
        addIntSetting("Disconnect below Y",0,20,1,()->disconnectY,v->disconnectY=v);
        addIntSetting("Minimum fall distance",2,20,1,()->minimumFall,v->minimumFall=v);
        addIntSetting("Support scan radius",0,2,1,()->scanRadius,v->scanRadius=v);
    }
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.getConnection()==null){clear();return;}
        if(mc.player.onGround||mc.player.isElytraFlying()||mc.player.isRiding()){
            clear();return;
        }
        if(retry>0)retry--;
        else elytraRequested=false;
        if(mc.player.motionY>=0D||mc.player.posY>triggerY||mc.player.fallDistance<minimumFall
                ||hasSupportBelow()){state="Safe";return;}
        state="Void risk";
        if(!warned){mc.player.sendMessage(new TextComponentString("[AntiVoid] No support detected below."));warned=true;}
        if(mode>=1&&!mc.player.isElytraFlying()&&!elytraRequested&&retry==0&&attempts<3
                &&mc.player.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem()==Items.ELYTRA){
            if(PacketManager.send(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.START_FALL_FLYING),name)){
                elytraRequested=true;retry=20;attempts++;state="Elytra requested";
            }
        }
        if(mode==2&&!disconnected&&mc.player.posY<=disconnectY){
            disconnected=true;state="Disconnected before void";
            mc.getConnection().getNetworkManager().closeChannel(
                    new TextComponentString("AntiVoid: no support below"));
        }
    }
    private boolean hasSupportBelow(){
        int x=MathHelper.floor(mc.player.posX),z=MathHelper.floor(mc.player.posZ);
        int radius=Math.max(0,Math.min(2,scanRadius));
        int top=MathHelper.floor(mc.player.getEntityBoundingBox().minY)-1;
        for(int y=top;y>=0;y--)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            BlockPos pos=new BlockPos(x+dx,y,z+dz);
            if(!mc.world.isBlockLoaded(pos))continue;
            Material material=mc.world.getBlockState(pos).getMaterial();
            if(material.blocksMovement())return true;
        }
        return false;
    }
    private void clear(){warned=elytraRequested=disconnected=false;retry=attempts=0;state="Safe";}
    @Override protected void onDisable(){clear();}
    @Override protected void onWorldUnload(){clear();}
    @Override public String hudTag(){return "["+state+"]";}
}
