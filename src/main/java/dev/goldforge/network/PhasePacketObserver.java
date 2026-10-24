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
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketBlockBreakAnim;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import dev.goldforge.module.impl.Velocity;
import dev.goldforge.module.impl.PacketFlyLab;
import dev.goldforge.module.impl.NoFall;
import dev.goldforge.module.impl.GroundSpoof;
import dev.goldforge.module.impl.CraftCarry;
import dev.goldforge.module.impl.Scaffold;
import dev.goldforge.module.impl.BreakESP;
import dev.goldforge.module.impl.PopChams;
import dev.goldforge.module.impl.LogoutSpots;

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
                    if(message instanceof SPacketBlockBreakAnim){
                        final SPacketBlockBreakAnim packet=(SPacketBlockBreakAnim)message;
                        final Minecraft client=Minecraft.getMinecraft();
                        Runnable capture=()->{
                            dev.goldforge.module.Module module=dev.goldforge.GoldForge.MODULES.get("BreakESP");
                            if(module instanceof BreakESP)((BreakESP)module).onBreakPacket(packet.getBreakerId(),packet.getPosition(),packet.getProgress());
                        };
                        if(client.isCallingFromMinecraftThread())capture.run();else client.addScheduledTask(capture);
                    }
                    if(message instanceof SPacketEntityStatus){
                        final SPacketEntityStatus packet=(SPacketEntityStatus)message;
                        if(packet.getOpCode()==35){
                            final Minecraft client=Minecraft.getMinecraft();
                            Runnable capture=()->{
                                if(client.world==null)return;
                                net.minecraft.entity.Entity entity=packet.getEntity(client.world);
                                dev.goldforge.module.Module module=dev.goldforge.GoldForge.MODULES.get("PopChams");
                                if(entity!=null&&module instanceof PopChams)((PopChams)module).onTotemPop(entity.getEntityId());
                            };
                            if(client.isCallingFromMinecraftThread())capture.run();else client.addScheduledTask(capture);
                        }
                    }
                    if(message instanceof SPacketPlayerListItem){
                        SPacketPlayerListItem packet=(SPacketPlayerListItem)message;
                        if(packet.getAction()==SPacketPlayerListItem.Action.REMOVE_PLAYER||packet.getAction()==SPacketPlayerListItem.Action.ADD_PLAYER){
                            final boolean removed=packet.getAction()==SPacketPlayerListItem.Action.REMOVE_PLAYER;
                            final java.util.List<java.util.UUID> ids=new java.util.ArrayList<java.util.UUID>();
                            for(SPacketPlayerListItem.AddPlayerData entry:packet.getEntries())ids.add(entry.getProfile().getId());
                            final Minecraft client=Minecraft.getMinecraft();
                            Runnable capture=()->{
                                dev.goldforge.module.Module module=dev.goldforge.GoldForge.MODULES.get("LogoutSpots");
                                if(module instanceof LogoutSpots)for(java.util.UUID id:ids)((LogoutSpots)module).onPlayerListChange(id,removed);
                            };
                            if(client.isCallingFromMinecraftThread())capture.run();else client.addScheduledTask(capture);
                        }
                    }
                    int velocityEntityId = -1;
                    SPacketExplosion explosion = null;
                    SPacketBlockChange blockChange = message instanceof SPacketBlockChange
                            ? (SPacketBlockChange) message : null;
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
                    if(message instanceof SPacketExplosion){
                        explosion=(SPacketExplosion)message;
                    }
                    if(blockChange!=null){
                        final BlockPos pos=blockChange.getBlockPosition().toImmutable();
                        final IBlockState state=blockChange.getBlockState();
                        final Minecraft client=Minecraft.getMinecraft();
                        Runnable apply=new Runnable(){@Override public void run(){
                            dev.goldforge.module.Module module=dev.goldforge.GoldForge.MODULES.get("Scaffold");
                            if(module instanceof Scaffold)((Scaffold)module).onServerBlockChange(pos,state);
                        }};
                        // Capture the authoritative reply before packet_handler may defer it.
                        // Scaffold also checks the world state before using the new support.
                        if(client.isCallingFromMinecraftThread())apply.run();
                        else client.addScheduledTask(apply);
                    }
                    super.channelRead(context,message);
                    if(velocityEntityId!=-1){
                        final int entityId=velocityEntityId;
                        final Minecraft client=Minecraft.getMinecraft();
                        Runnable apply=new Runnable(){@Override public void run(){Velocity.apply(Velocity.capture(entityId));}};
                        if(client.isCallingFromMinecraftThread())apply.run();
                        else client.addScheduledTask(apply);
                    }
                    if(explosion!=null){
                        final float motionX=explosion.getMotionX(),motionY=explosion.getMotionY(),motionZ=explosion.getMotionZ();
                        final Minecraft client=Minecraft.getMinecraft();
                        Runnable apply=new Runnable(){@Override public void run(){Velocity.apply(Velocity.captureExplosion(motionX,motionY,motionZ));}};
                        if(client.isCallingFromMinecraftThread())apply.run();
                        else client.addScheduledTask(apply);
                    }
                }
                @Override public void write(ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise)throws Exception{
                    dev.goldforge.module.impl.LagWatch.recordPacket(false,message,System.nanoTime());
                    if(message instanceof net.minecraft.network.play.client.CPacketKeepAlive){
                        dev.goldforge.module.Module spoof=dev.goldforge.GoldForge.MODULES.get("PingSpoof");
                        if(spoof instanceof dev.goldforge.module.impl.PingSpoof&&spoof.enabled&&!spoof.isSuspended()){
                            int delay=Math.max(0,Math.min(5000,((dev.goldforge.module.impl.PingSpoof)spoof).delayMs));
                            if(delay>0){
                                final Object packet=message;
                                context.executor().schedule(()->context.writeAndFlush(packet,promise),delay,java.util.concurrent.TimeUnit.MILLISECONDS);
                                return;
                            }
                        }
                    }
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
