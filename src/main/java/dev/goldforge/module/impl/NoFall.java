package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;

/** Rewrites outgoing movement flags while descending; the local physics state is left unchanged. */
public final class NoFall extends Module {
    /** 0: NCP threshold profile, 1: early packet profile. */
    public int mode=0;
    public int thresholdTenths=31;
    private static volatile boolean spoofGround;
    private static volatile int activeMode;

    public NoFall(){
        super("NoFall","Adjusts the onGround flag on outgoing movement packets while falling.",Category.MOVEMENT,0);
        addIntSetting("Mode (0 NCP, 1 Packet)",0,1,1,()->mode,v->mode=v);
        addIntSetting("NCP fall threshold (tenths)",10,80,1,()->thresholdTenths,v->thresholdTenths=v);
    }

    @Override public void tick(){
        EntityPlayerSP player=mc.player;
        activeMode=mode;
        if(player==null||mc.world==null||player.isDead||player.onGround||player.isRiding()||player.isElytraFlying()||
                player.isInWater()||player.isInLava()||player.isOnLadder()||player.capabilities.isFlying||player.noClip){
            spoofGround=false;return;
        }
        double threshold=mode==0?Math.max(1,Math.min(8,thresholdTenths))/10.0D:.1D;
        spoofGround=player.motionY<0D&&player.fallDistance>=threshold;
    }

    /** Called from the network pipeline before the original packet is encoded. */
    public static CPacketPlayer rewrite(CPacketPlayer packet){
        if(packet==null||!spoofGround||packet.isOnGround())return packet;
        if(packet instanceof CPacketPlayer.PositionRotation)
            return new CPacketPlayer.PositionRotation(packet.getX(0D),packet.getY(0D),packet.getZ(0D),packet.getYaw(0F),packet.getPitch(0F),true);
        if(packet instanceof CPacketPlayer.Position)
            return new CPacketPlayer.Position(packet.getX(0D),packet.getY(0D),packet.getZ(0D),true);
        if(packet instanceof CPacketPlayer.Rotation)
            return new CPacketPlayer.Rotation(packet.getYaw(0F),packet.getPitch(0F),true);
        return new CPacketPlayer(true);
    }

    @Override protected void onDisable(){spoofGround=false;}
    @Override protected void onWorldUnload(){spoofGround=false;}
    @Override public String hudTag(){return activeMode==0?"[NCP]":"[Packet]";}
}
