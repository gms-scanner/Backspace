package dev.goldforge.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Locale;

/** Bounded local movement recorder with a non-interactive ghost-path playback. */
public final class MovementReplay {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final Logger LOGGER=LogManager.getLogger("Backspace/Replay");
    private static final int CAPACITY=6000;
    private static final Sample[] SAMPLES=new Sample[CAPACITY];
    private static int start,count,playbackIndex;
    private static boolean recording,playing;
    private static long recordingStarted,playbackStarted;
    private static Object recordingWorld;
    private static int recordingDimension;
    private static Sample previous;

    private MovementReplay(){}

    public static synchronized boolean startRecording(){
        if(MC.player==null||MC.world==null)return false;
        clear();recording=true;recordingStarted=System.nanoTime();recordingWorld=MC.world;recordingDimension=MC.player.dimension;return true;
    }
    public static synchronized void stopRecording(){recording=false;recordingWorld=null;previous=null;}
    public static synchronized boolean play(){
        if(count<2)return false;
        recording=false;playbackIndex=0;playbackStarted=System.nanoTime();playing=true;return true;
    }
    public static synchronized void stopPlayback(){playing=false;playbackIndex=0;}
    public static synchronized void clear(){recording=false;playing=false;start=0;count=0;playbackIndex=0;previous=null;recordingWorld=null;}

    /** Called once after movement arbitration at client-tick end. */
    public static synchronized void tick(){
        if(!recording)return;
        EntityPlayerSP player=MC.player;
        if(player==null||player.isDead||MC.world==null||MC.world!=recordingWorld||player.dimension!=recordingDimension){stopRecording();return;}
        double elapsed=(System.nanoTime()-recordingStarted)/1_000_000D;
        double horizontal=Math.sqrt(player.motionX*player.motionX+player.motionZ*player.motionZ);
        boolean correction=false;
        if(previous!=null){double dx=player.posX-previous.x,dy=player.posY-previous.y,dz=player.posZ-previous.z;correction=dx*dx+dy*dy+dz*dz>9D;}
        Sample sample=new Sample(elapsed,player.posX,player.posY,player.posZ,player.rotationYaw,player.rotationPitch,player.motionX,player.motionY,player.motionZ,
                player.movementInput==null?0F:player.movementInput.moveForward,player.movementInput==null?0F:player.movementInput.moveStrafe,player.onGround,correction);
        previous=sample;
        int index=(start+count)%CAPACITY;SAMPLES[index]=sample;
        if(count<CAPACITY)count++;else start=(start+1)%CAPACITY;
    }

    public static synchronized void worldChanged(){stopRecording();stopPlayback();}

    public static synchronized void render(RenderWorldLastEvent event){
        try{renderFrame(event);}
        catch(VirtualMachineError fatal){throw fatal;}
        catch(ThreadDeath fatal){throw fatal;}
        catch(Throwable failure){playing=false;LOGGER.error("Movement replay render failed; playback stopped safely.",failure);}
    }

    private static void renderFrame(RenderWorldLastEvent event){
        if(!playing||count<2||MC.world==null)return;
        double elapsed=(System.nanoTime()-playbackStarted)/1_000_000D;
        Sample first=at(0),last=at(count-1);
        if(elapsed>=last.timeMs-first.timeMs){playing=false;return;}
        while(playbackIndex<count-2&&at(playbackIndex+1).timeMs-first.timeMs<elapsed)playbackIndex++;
        Sample a=at(playbackIndex),b=at(Math.min(count-1,playbackIndex+1));
        double span=Math.max(1D,b.timeMs-a.timeMs),f=Math.max(0D,Math.min(1D,(elapsed-(a.timeMs-first.timeMs))/span));
        double px=a.x+(b.x-a.x)*f,py=a.y+(b.y-a.y)*f,pz=a.z+(b.z-a.z)*f;
        double vx=MC.getRenderManager().viewerPosX,vy=MC.getRenderManager().viewerPosY,vz=MC.getRenderManager().viewerPosZ;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.disableDepth();GlStateManager.depthMask(false);GL11.glLineWidth(2F);
            GL11.glColor4f(1F,.84F,0F,.75F);GL11.glBegin(GL11.GL_LINE_STRIP);
            for(int i=0;i<count;i++){Sample sample=at(i);GL11.glVertex3d(sample.x-vx,sample.y-vy+.04D,sample.z-vz);}
            GL11.glEnd();
            GL11.glColor4f(1F,.84F,0F,1F);
            AxisAlignedBB marker=new AxisAlignedBB(px-.18D-vx,py-vy,pz-.18D-vz,px+.18D-vx,py+.36D-vy,pz+.18D-vz);
            RenderGlobal.drawSelectionBoundingBox(marker,1F,.84F,0F,1F);
        }finally{GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);GL11.glPopAttrib();}
    }

    public static synchronized boolean export(String name){
        if(name==null||!name.matches("[A-Za-z0-9_-]{1,32}")||count==0)return false;
        Path file=Paths.get(MC.mcDataDir.getAbsolutePath(),"Backspace","replays",name+".csv");Path temp=file.resolveSibling(file.getFileName().toString()+".tmp");
        try{
            Files.createDirectories(file.getParent());
            try(BufferedWriter writer=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){
                writer.write("time_ms,x,y,z,yaw,pitch,motion_x,motion_y,motion_z,forward,strafe,on_ground,position_jump_suspected\n");
                for(int i=0;i<count;i++){Sample s=at(i);writer.write(String.format(Locale.ROOT,"%.2f,%.5f,%.5f,%.5f,%.3f,%.3f,%.6f,%.6f,%.6f,%.2f,%.2f,%s,%s%n",s.timeMs,s.x,s.y,s.z,s.yaw,s.pitch,s.motionX,s.motionY,s.motionZ,s.forward,s.strafe,s.onGround,s.correction));}
            }
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException failure){try{Files.deleteIfExists(temp);}catch(IOException ignored){}return false;}
    }

    public static synchronized String status(){
        String mode=recording?"recording":playing?"playing":"idle";
        double duration=count<2?0D:(at(count-1).timeMs-at(0).timeMs)/1000D;
        return String.format(Locale.ROOT,"Replay %s | %d samples | %.1f s%s",mode,count,duration,recording?" | max 5 minutes":"");
    }
    private static Sample at(int chronologicalIndex){return SAMPLES[(start+chronologicalIndex)%CAPACITY];}

    private static final class Sample{
        final double timeMs,x,y,z,motionX,motionY,motionZ;
        final float yaw,pitch,forward,strafe;
        final boolean onGround,correction;
        Sample(double timeMs,double x,double y,double z,float yaw,float pitch,double motionX,double motionY,double motionZ,float forward,float strafe,boolean onGround,boolean correction){
            this.timeMs=timeMs;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;this.motionX=motionX;this.motionY=motionY;this.motionZ=motionZ;this.forward=forward;this.strafe=strafe;this.onGround=onGround;this.correction=correction;
        }
    }
}
