package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraft.network.play.server.SPacketTimeUpdate;

/** Reports observable ping and FPS separately; it never presents an estimated server TPS. */
public final class LagWatch extends Module {
    public int warningPing=250,cooldownSeconds=10,showHud=1,showFps=1;
    private int ticks;
    private long lastWarning;
    private int ping=-1,fps;
    private boolean connected;
    private static volatile long lastInboundNanos,lastOutboundNanos,timeUpdateIntervalNanos;
    private static volatile long previousTimeUpdateNanos;
    private static volatile boolean packetSampling;
    public LagWatch(){
        super("LagWatch","Shows measured ping/FPS and warns on sustained high latency.",Category.HUD,0);
        addIntSetting("Ping warning (ms)",50,1000,25,()->warningPing,v->warningPing=v);
        addIntSetting("Cooldown (seconds)",1,60,1,()->cooldownSeconds,v->cooldownSeconds=v);
        addIntSetting("HUD indicator (0/1)",0,1,1,()->showHud,v->showHud=v);
        addIntSetting("Show FPS (0/1)",0,1,1,()->showFps,v->showFps=v);
    }
    @Override protected void onEnable(){packetSampling=true;}
    @Override protected void onDisable(){ticks=0;ping=-1;connected=false;packetSampling=false;}
    /** Called by the existing Netty observer; stores only monotonic timestamps. */
    public static void recordPacket(boolean inbound,Object packet,long now){
        if(!packetSampling)return;
        if(inbound){lastInboundNanos=now;if(packet instanceof SPacketTimeUpdate){long previous=previousTimeUpdateNanos;if(previous>0L)timeUpdateIntervalNanos=Math.max(0L,now-previous);previousTimeUpdateNanos=now;}}
        else lastOutboundNanos=now;
    }
    @Override public void tick(){
        if(++ticks<20)return;ticks=0;connected=mc.getConnection()!=null&&mc.player!=null;
        ping=-1;if(connected){NetworkPlayerInfo info=mc.getConnection().getPlayerInfo(mc.player.getUniqueID());if(info!=null)ping=Math.max(0,info.getResponseTime());}
        fps=mc.getDebugFPS();
        if(ping>=warningPing&&System.currentTimeMillis()-lastWarning>=Math.max(1,cooldownSeconds)*1000L){
            lastWarning=System.currentTimeMillis();if(mc.player!=null)mc.player.sendMessage(new TextComponentString("[LagWatch] High latency: "+ping+" ms"));
        }
    }
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||showHud==0||mc.currentScreen!=null)return;
        long now=System.nanoTime();long rx=lastInboundNanos==0L?-1L:Math.max(0L,(now-lastInboundNanos)/1000000L);
        long tx=lastOutboundNanos==0L?-1L:Math.max(0L,(now-lastOutboundNanos)/1000000L);
        long update=timeUpdateIntervalNanos/1000000L;
        String network=connected?(ping<0?"Ping —":"Ping "+ping+" ms"):"Disconnected";
        String value=network+(showFps!=0?"  |  FPS "+fps:"")+(rx>=0?"  |  RX "+rx+" ms":"")+(tx>=0?"  |  TX "+tx+" ms":"")+(update>0?"  |  Time update "+update+" ms":"");int w=mc.fontRenderer.getStringWidth(value);
        Gui.drawRect(8,8,16+w,22,0xB80A0A0A);Gui.drawRect(8,8,10,22,ping>=warningPing?0xFFFF5555:0xFFFFD700);
        mc.fontRenderer.drawStringWithShadow(value,13,10,ping>=warningPing?0xFFFF7777:0xFFF4F4F4);
    }
}
