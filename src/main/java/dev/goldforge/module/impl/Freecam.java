package dev.goldforge.module.impl;

import com.mojang.authlib.GameProfile;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputUpdateEvent;

import java.util.UUID;

/** Detached client camera. The real player remains at the server-reported position. */
public final class Freecam extends Module {
    public int speed=5;
    public int handoffOnDisable=0;
    public int dragPlayerWithCamera=0;
    public int dragUpdateIntervalTicks=1;
    private EntityOtherPlayerMP camera;
    private Entity previousView;
    private int lastDragTick=Integer.MIN_VALUE;

    public Freecam(){
        super("Freecam","Moves the local view camera independently; the player body stays in place.",Category.RENDER,0);
        addIntSetting("Speed",1,10,1,()->speed,v->speed=v);
        addIntSetting("Camera handoff on disable (0 off, 1 test)",0,1,1,()->handoffOnDisable,v->handoffOnDisable=v);
        addIntSetting("Drag player with camera (0 off, 1 test)",0,1,1,()->dragPlayerWithCamera,v->dragPlayerWithCamera=v);
        addIntSetting("Drag update interval (ticks)",1,20,1,()->dragUpdateIntervalTicks,v->dragUpdateIntervalTicks=v);
    }

    @Override protected void onEnable(){
        if(mc.player==null||mc.world==null){
            enabled=false;
            return;
        }
        previousView=mc.getRenderViewEntity();
        lastDragTick=Integer.MIN_VALUE;
        camera=new EntityOtherPlayerMP(mc.world,new GameProfile(UUID.randomUUID(),"BackspaceCamera"));
        camera.copyLocationAndAnglesFrom(mc.player);
        camera.rotationYawHead=mc.player.rotationYawHead;
        camera.noClip=true;
        mc.setRenderViewEntity(camera);
        Phase.recordFreecamTransition(true, mc.player.posX, mc.player.posY, mc.player.posZ,
                camera.posX, camera.posY, camera.posZ);
    }

    @Override public void tick(){
        if(camera==null||mc.world==null||mc.player==null){
            setEnabled(false);
            return;
        }
        camera.noClip=true;
        // Mouse-look is mirrored on every camera setup; the tick copy keeps the
        // camera entity's own rotation usable for movement and ray calculations.
        camera.rotationYaw=mc.player.rotationYaw;
        camera.rotationPitch=mc.player.rotationPitch;
        camera.rotationYawHead=mc.player.rotationYawHead;
        camera.prevRotationYaw=camera.rotationYaw;
        camera.prevRotationPitch=camera.rotationPitch;
        if(mc.getRenderViewEntity()!=camera)mc.setRenderViewEntity(camera);
        camera.lastTickPosX=camera.posX;camera.lastTickPosY=camera.posY;camera.lastTickPosZ=camera.posZ;
        float forward=(mc.gameSettings.keyBindForward.isKeyDown()?1F:0F)-(mc.gameSettings.keyBindBack.isKeyDown()?1F:0F);
        // Vanilla's moveRelative convention is left-positive, right-negative.
        float strafe=(mc.gameSettings.keyBindLeft.isKeyDown()?1F:0F)-(mc.gameSettings.keyBindRight.isKeyDown()?1F:0F);
        float magnitude=(float)Math.sqrt(forward*forward+strafe*strafe);
        if(magnitude>1F){forward/=magnitude;strafe/=magnitude;}
        boolean jumping=mc.gameSettings.keyBindJump.isKeyDown();
        boolean sneaking=mc.gameSettings.keyBindSneak.isKeyDown();
        float yaw=camera.rotationYaw*(float)Math.PI/180F;
        double amount=speed*0.12D;
        double dx=(strafe*Math.cos(yaw)-forward*Math.sin(yaw))*amount;
        double dz=(forward*Math.cos(yaw)+strafe*Math.sin(yaw))*amount;
        double dy=(jumping?amount:0D)-(sneaking?amount:0D);
        if(dx!=0||dy!=0||dz!=0)camera.setPosition(camera.posX+dx,camera.posY+dy,camera.posZ+dz);
        if(Phase.isCapturingDiagnostics()){
            Phase.recordFreecamSample(mc.player.ticksExisted,mc.world.provider.getDimension(),
                    mc.player.posX,mc.player.posY,mc.player.posZ,
                    camera.posX,camera.posY,camera.posZ,forward,strafe,jumping,sneaking,
                    cameraCollisionBlocks(camera.getEntityBoundingBox()));
        }
        if(dragPlayerWithCamera==1&&mc.getConnection()!=null
                &&(lastDragTick==Integer.MIN_VALUE||mc.player.ticksExisted-lastDragTick>=Math.max(1,dragUpdateIntervalTicks))){
            boolean sent=PacketManager.send(new CPacketPlayer.Position(camera.posX,camera.posY,camera.posZ,mc.player.onGround),name);
            Phase.recordFreecamDrag(camera.posX,camera.posY,camera.posZ,mc.player.ticksExisted,sent);
            lastDragTick=mc.player.ticksExisted;
            if(sent){
                mc.player.setPosition(camera.posX,camera.posY,camera.posZ);
                mc.player.motionX=0D;mc.player.motionY=0D;mc.player.motionZ=0D;
            }
        }
    }

    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event){
        if(camera==null||mc.player==null)return;
        if(mc.getRenderViewEntity()!=camera)mc.setRenderViewEntity(camera);
        float yaw=mc.player.rotationYaw;
        float pitch=mc.player.rotationPitch;
        camera.rotationYaw=yaw;camera.prevRotationYaw=yaw;
        camera.rotationPitch=pitch;camera.prevRotationPitch=pitch;
        camera.rotationYawHead=mc.player.rotationYawHead;
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    @Override public void onInputUpdate(InputUpdateEvent event){
        // Keep the real player stationary while the detached camera moves.
        event.getMovementInput().moveForward=0F;
        event.getMovementInput().moveStrafe=0F;
        event.getMovementInput().jump=false;
        event.getMovementInput().sneak=false;
    }

    @Override protected void onDisable(){
        if(mc.player!=null){
            double cameraX=camera==null?mc.player.posX:camera.posX;
            double cameraY=camera==null?mc.player.posY:camera.posY;
            double cameraZ=camera==null?mc.player.posZ:camera.posZ;
            Phase.recordFreecamTransition(false, mc.player.posX, mc.player.posY, mc.player.posZ,
                    cameraX, cameraY, cameraZ);
            boolean requested=handoffOnDisable==1&&dragPlayerWithCamera!=1&&camera!=null&&mc.getConnection()!=null;
            boolean sent=requested&&PacketManager.send(new CPacketPlayer.PositionRotation(cameraX,cameraY,cameraZ,
                    mc.player.rotationYaw,mc.player.rotationPitch,mc.player.onGround),name);
            Phase.recordFreecamHandoff(cameraX,cameraY,cameraZ,requested,sent);
            if(sent)mc.player.setPosition(cameraX,cameraY,cameraZ);
        }else{
            Phase.recordFreecamTransition(false, 0D, 0D, 0D, 0D, 0D, 0D);
        }
        if(camera!=null){
            if(mc.player!=null)mc.setRenderViewEntity(mc.player);
            else if(previousView!=null)mc.setRenderViewEntity(previousView);
        }
        camera=null;previousView=null;
    }

    private String cameraCollisionBlocks(AxisAlignedBB bounds){
        BlockPos min=new BlockPos(MathHelper.floor(bounds.minX),MathHelper.floor(bounds.minY),MathHelper.floor(bounds.minZ));
        BlockPos max=new BlockPos(MathHelper.floor(bounds.maxX),MathHelper.floor(bounds.maxY),MathHelper.floor(bounds.maxZ));
        StringBuilder found=new StringBuilder();
        int count=0;
        for(BlockPos pos:BlockPos.getAllInBox(min,max)){
            IBlockState state=mc.world.getBlockState(pos);
            AxisAlignedBB shape=state.getCollisionBoundingBox(mc.world,pos);
            if(shape==null||!shape.offset(pos).intersects(bounds))continue;
            if(count++>0)found.append('|');
            found.append(state.getBlock().getRegistryName()).append('@').append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
            if(count>=6){found.append("|...");break;}
        }
        return count==0?"none":found.toString();
    }
}
