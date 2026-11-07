package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import java.util.EnumSet;

/** Keeps the camera angle while vanilla still handles position and teleport acknowledgement. */
public final class NoRotate extends Module {
    private static volatile boolean active;
    private static volatile long preserved;
    public int preserveYaw=1,preservePitch=1;
    public NoRotate(){
        super("NoRotate","Preserves local view rotation on server position packets.",Category.MOVEMENT,0);
        addIntSetting("Preserve yaw (0/1)",0,1,1,()->preserveYaw,v->preserveYaw=v);
        addIntSetting("Preserve pitch (0/1)",0,1,1,()->preservePitch,v->preservePitch=v);
    }
    @Override protected void onEnable(){active=true;preserved=0;}
    @Override protected void onDisable(){active=false;}
    @Override protected void onWorldUnload(){active=false;}
    public static SPacketPlayerPosLook rewrite(SPacketPlayerPosLook packet){
        if(!active||packet==null)return packet;
        dev.goldforge.module.Module module=dev.goldforge.GoldForge.MODULES.get("NoRotate");
        if(!(module instanceof NoRotate)||!module.enabled)return packet;
        NoRotate setting=(NoRotate)module;
        if(setting.preserveYaw==0&&setting.preservePitch==0)return packet;
        EnumSet<SPacketPlayerPosLook.EnumFlags> flags=EnumSet.noneOf(SPacketPlayerPosLook.EnumFlags.class);
        flags.addAll(packet.getFlags());
        float yaw=packet.getYaw(),pitch=packet.getPitch();
        if(setting.preserveYaw!=0){flags.add(SPacketPlayerPosLook.EnumFlags.Y_ROT);yaw=0F;}
        if(setting.preservePitch!=0){flags.add(SPacketPlayerPosLook.EnumFlags.X_ROT);pitch=0F;}
        preserved++;
        return new SPacketPlayerPosLook(packet.getX(),packet.getY(),packet.getZ(),
                yaw,pitch,flags,packet.getTeleportId());
    }
    @Override public String hudTag(){return "["+preserved+" kept]";}
}
