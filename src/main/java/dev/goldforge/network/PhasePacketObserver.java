package dev.goldforge.network;

import dev.goldforge.module.impl.Phase;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import dev.goldforge.module.impl.Velocity;
import dev.goldforge.module.impl.PacketFlyLab;
import dev.goldforge.module.impl.NoFall;
import dev.goldforge.module.impl.GroundSpoof;
import dev.goldforge.module.impl.CraftCarry;

/** Read-only observer for server position corrections, used by Phase diagnostics. */
public final class PhasePacketObserver {
    private static final String HANDLER_NAME="backspace_phase_position_trace";
    private static volatile Channel observedChannel;

    private PhasePacketObserver(){}

    public static void ensureInstalled(){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.getConnection()==null)return;
        NetworkManager manager=mc.getConnection().getNetworkManager();
        if(manager==null)return;
        final Channel channel=manager.channel();
        if(channel==null||!channel.isOpen()||channel==observedChannel)return;
        Runnable install=new Runnable(){@Override public void run(){
            ChannelPipeline pipeline=channel.pipeline();
            if(pipeline.get(HANDLER_NAME)!=null){observedChannel=channel;return;}
            if(pipeline.get("packet_handler")==null)return;
            pipeline.addBefore("packet_handler",HANDLER_NAME,new ChannelDuplexHandler(){
                @Override public void channelRead(ChannelHandlerContext context,Object message)throws Exception{
                    dev.goldforge.module.impl.LagWatch.recordPacket(true,message,System.nanoTime());
                    int velocityEntityId = -1;
                    if(message instanceof SPacketPlayerPosLook){
                        SPacketPlayerPosLook packet=(SPacketPlayerPosLook)message;
                        final Phase.CorrectionRecord record=Phase.recordServerCorrection(packet.getX(),packet.getY(),packet.getZ(),packet.getYaw(),packet.getPitch(),packet.getFlags());
                        final PacketFlyLab.Correction packetFlyRecord=PacketFlyLab.recordCorrection(packet);
                        final Minecraft client=Minecraft.getMinecraft();
                        Runnable capture=new Runnable(){@Override public void run(){
                            if(record!=null)Phase.captureCorrectionClientPreApply(record);
                            if(packetFlyRecord!=null)PacketFlyLab.captureCorrectionPreApply(packetFlyRecord);
                            dev.goldforge.module.impl.Speed.onServerCorrection();
                        }};
                        // Queue correction resets before packet_handler queues application
                        // of the S08 packet, preserving the existing client-thread ordering.
                        if(client.isCallingFromMinecraftThread())capture.run();
                        else client.addScheduledTask(capture);
                    }
                    if(message instanceof SPacketEntityVelocity){
                        // Only retain the packet's primitive id on Netty. Player/module
                        // state is read later on the client thread, after vanilla handles it.
                        velocityEntityId=((SPacketEntityVelocity)message).getEntityID();
                    }
                    super.channelRead(context,message);
                    if(velocityEntityId!=-1){
                        final int entityId=velocityEntityId;
                        final Minecraft client=Minecraft.getMinecraft();
                        Runnable apply=new Runnable(){@Override public void run(){Velocity.apply(Velocity.capture(entityId));}};
                        if(client.isCallingFromMinecraftThread())apply.run();
                        else client.addScheduledTask(apply);
                    }
                }
                @Override public void write(ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise)throws Exception{
                    dev.goldforge.module.impl.LagWatch.recordPacket(false,message,System.nanoTime());
                    if(message instanceof net.minecraft.network.play.client.CPacketCloseWindow&&CraftCarry.shouldKeepOpen((net.minecraft.network.play.client.CPacketCloseWindow)message)){promise.setSuccess();return;}
                    if(message instanceof CPacketPlayer){
                        message=NoFall.rewrite((CPacketPlayer)message);
                        message=GroundSpoof.rewrite((CPacketPlayer)message);
                        Phase.recordOutgoingMovement((CPacketPlayer)message);
                        PacketFlyLab.recordOutbound((CPacketPlayer)message);
                    }
                    super.write(context,message,promise);
                }
            });
            observedChannel=channel;
        }};
        if(channel.eventLoop().inEventLoop())install.run();
        else channel.eventLoop().execute(install);
    }
}
