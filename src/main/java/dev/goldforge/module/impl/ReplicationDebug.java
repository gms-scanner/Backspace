package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.play.client.CPacketConfirmTeleport;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketVehicleMove;
import net.minecraft.network.play.server.SPacketMoveVehicle;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import java.util.Locale;
import java.util.Set;

/** Observations only. Outgoing packets and missing corrections do not prove server acceptance. */
public final class ReplicationDebug extends Module {
    private static volatile boolean sampling;
    private static long playerPackets,vehiclePackets,teleportConfirms,playerPositionPackets,vehicleCorrections,rubberbands;
    private static Vec3d requested,serverPacketPosition,vehiclePacketPosition;
    private static String[] lines=new String[0];
    public int x=8,y=90,showHud=1;
    private int refreshTicks;

    public ReplicationDebug(){
        super("ReplicationDebug","Shows requested positions, server position packets, and correction counts.",Category.HUD,0);
        addIntSetting("X",0,500,2,()->x,v->x=v);
        addIntSetting("Y",0,500,2,()->y,v->y=v);
        addIntSetting("Show HUD (0/1)",0,1,1,()->showHud,v->showHud=v);
    }
    @Override protected void onEnable(){sampling=true;clear();refreshTicks=0;}
    @Override protected void onDisable(){sampling=false;lines=new String[0];}
    @Override protected void onWorldUnload(){clear();}
    private static void clear(){
        playerPackets=vehiclePackets=teleportConfirms=playerPositionPackets=vehicleCorrections=rubberbands=0;
        requested=serverPacketPosition=vehiclePacketPosition=null;
    }
    public static void outgoing(Object packet){
        if(!sampling)return;
        if(packet instanceof CPacketPlayer){
            CPacketPlayer movement=(CPacketPlayer)packet;
            playerPackets++;
            if(packet instanceof CPacketPlayer.Position||packet instanceof CPacketPlayer.PositionRotation)
                requested=new Vec3d(movement.getX(0D),movement.getY(0D),movement.getZ(0D));
        }else if(packet instanceof CPacketVehicleMove)vehiclePackets++;
        else if(packet instanceof CPacketConfirmTeleport)teleportConfirms++;
    }
    /** Called before vanilla applies a position packet, on the Minecraft client thread. */
    public static void playerPosition(SPacketPlayerPosLook packet,double clientX,double clientY,double clientZ){
        if(!sampling)return;
        Set<SPacketPlayerPosLook.EnumFlags> flags=packet.getFlags();
        double x=packet.getX()+(flags.contains(SPacketPlayerPosLook.EnumFlags.X)?clientX:0D);
        double y=packet.getY()+(flags.contains(SPacketPlayerPosLook.EnumFlags.Y)?clientY:0D);
        double z=packet.getZ()+(flags.contains(SPacketPlayerPosLook.EnumFlags.Z)?clientZ:0D);
        Vec3d position=new Vec3d(x,y,z);
        serverPacketPosition=position;playerPositionPackets++;
        if(position.squareDistanceTo(new Vec3d(clientX,clientY,clientZ))>.09D)rubberbands++;
    }
    public static void vehiclePosition(SPacketMoveVehicle packet){
        if(!sampling)return;
        vehiclePacketPosition=new Vec3d(packet.getX(),packet.getY(),packet.getZ());
        vehicleCorrections++;
    }
    @Override public void tick(){
        if(mc.player==null)return;
        if(++refreshTicks<5)return;
        refreshTicks=0;
        lines=new String[]{
            "Replication: observer required for confirmation",
            "Local "+vec(mc.player.posX,mc.player.posY,mc.player.posZ),
            "Requested "+vec(requested),
            "Server player packet "+vec(serverPacketPosition),
            "Server vehicle packet "+vec(vehiclePacketPosition),
            "TX player "+playerPackets+" vehicle "+vehiclePackets+" teleport confirm "+teleportConfirms,
            "RX player "+playerPositionPackets+" vehicle "+vehicleCorrections+" displacement "+rubberbands
        };
    }
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(showHud==0||event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||mc.currentScreen!=null)return;
        int drawY=y;
        for(String line:lines){
            int width=mc.fontRenderer.getStringWidth(line);
            Gui.drawRect(x-4,drawY-2,x+width+5,drawY+mc.fontRenderer.FONT_HEIGHT+2,0xB80A0A0A);
            Gui.drawRect(x-4,drawY-2,x-2,drawY+mc.fontRenderer.FONT_HEIGHT+2,0xFFFFD700);
            mc.fontRenderer.drawStringWithShadow(line,x,drawY,0xFFF4F4F4);
            drawY+=mc.fontRenderer.FONT_HEIGHT+4;
        }
    }
    private static String vec(Vec3d v){return v==null?"—":vec(v.x,v.y,v.z);}
    private static String vec(double x,double y,double z){
        return String.format(Locale.ROOT,"%.2f %.2f %.2f",x,y,z);
    }
}
