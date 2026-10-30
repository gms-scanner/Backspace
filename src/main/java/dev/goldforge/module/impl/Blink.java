package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.play.client.CPacketPlayer;
import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded movement queue; disabling the module flushes queued packets in order. */
public final class Blink extends Module {
    private static final Deque<Entry> QUEUE=new ArrayDeque<Entry>();
    private static volatile boolean active;
    private static int limit=120;
    public int maxPackets=120,maxSeconds=5,flushOnDisable=1;
    public Blink(){
        super("Blink","Queues movement packets and flushes them in order on disable.",Category.MOVEMENT,0);
        addIntSetting("Maximum queued packets",20,400,10,()->maxPackets,v->maxPackets=v);
        addIntSetting("Maximum queue seconds",1,20,1,()->maxSeconds,v->maxSeconds=v);
        addIntSetting("Flush on disable (0/1)",0,1,1,()->flushOnDisable,v->flushOnDisable=v);
    }
    @Override protected void onEnable(){synchronized(QUEUE){QUEUE.clear();limit=maxPackets;active=true;}}
    @Override protected void onDisable(){active=false;if(flushOnDisable!=0)flush();else clear();}
    @Override protected void onWorldUnload(){active=false;clear();}
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.getConnection()==null){clear();return;}
        synchronized(QUEUE){
            limit=Math.max(20,Math.min(400,maxPackets));
            if(!QUEUE.isEmpty()&&System.currentTimeMillis()-QUEUE.peekFirst().time
                    >=Math.max(1,maxSeconds)*1000L)flushLocked();
        }
    }
    @Override public String hudTag(){synchronized(QUEUE){return "["+QUEUE.size()+" queued]";}}
    public static boolean intercept(Object packet,ChannelHandlerContext context,ChannelPromise promise){
        if(!active||!(packet instanceof CPacketPlayer))return false;
        synchronized(QUEUE){
            if(!active)return false;
            // Avoid an unbounded queue; flush old movement before accepting more.
            if(QUEUE.size()>=limit)flushLocked();
            QUEUE.addLast(new Entry((CPacketPlayer)packet,context,System.currentTimeMillis()));
        }
        promise.setSuccess();
        return true;
    }
    public static void flush(){synchronized(QUEUE){flushLocked();}}
    private static void flushLocked(){
        while(!QUEUE.isEmpty()){
            Entry entry=QUEUE.removeFirst();
            if(entry.context.channel().isOpen())
                entry.context.executor().execute(()->{
                    Phase.recordOutgoingMovement(entry.packet);
                    PacketFlyLab.recordOutbound(entry.packet);
                    ReplicationDebug.outgoing(entry.packet);
                    entry.context.writeAndFlush(entry.packet);
                });
        }
    }
    private static void clear(){synchronized(QUEUE){QUEUE.clear();}}
    private static final class Entry{
        final CPacketPlayer packet;
        final ChannelHandlerContext context;
        final long time;
        Entry(CPacketPlayer packet,ChannelHandlerContext context,long time){
            this.packet=packet;this.context=context;this.time=time;
        }
    }
}
