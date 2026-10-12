package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/** Briefly resets sprint state around a player hit, then resumes sprinting. */
public final class WTap extends Module {
    public int resetTicks=1,friendFilter=1,playersOnly=1;
    private int resumeTicks;
    private boolean resumeSprint;

    public WTap(){
        super("WTap","Resets sprint for a short hit window, then resumes it if sprint is still held.",Category.COMBAT,0);
        addIntSetting("Sprint reset ticks",1,4,1,()->resetTicks,v->resetTicks=v);
        addIntSetting("Ignore friend targets (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
        addIntSetting("Players only (0/1)",0,1,1,()->playersOnly,v->playersOnly=v);
    }

    public void onAttack(AttackEntityEvent event){
        if(mc.player==null||event.getEntityPlayer()!=mc.player||!mc.player.isSprinting())return;
        Entity target=event.getTarget();
        if(playersOnly!=0&&!(target instanceof EntityPlayer))return;
        if(friendFilter!=0&&target instanceof EntityPlayer&&FriendManager.isFriend(target.getName()))return;
        resumeSprint=true;
        resumeTicks=Math.max(1,resetTicks);
        PacketManager.send(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.STOP_SPRINTING),name);
    }

    @Override public void tick(){
        if(resumeTicks<=0)return;
        if(--resumeTicks>0)return;
        if(resumeSprint&&mc.player!=null&&mc.player.isSprinting())
            PacketManager.send(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.START_SPRINTING),name);
        resumeSprint=false;
    }

    @Override protected void onDisable(){resumeTicks=0;resumeSprint=false;}
    @Override protected void onWorldUnload(){resumeTicks=0;resumeSprint=false;}
}
