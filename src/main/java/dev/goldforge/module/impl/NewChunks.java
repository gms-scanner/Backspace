package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.ConfigManager;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Maps chunks with recorded player-presence evidence; red means none has been observed in saved history. */
public final class NewChunks extends Module {
    /** 0: player-history map, 1: recent arrivals, 2: both. */
    public int overlayMode=0,range=256,lifetimeSeconds=300;
    private static final int MAX_TRACKED=512;
    private static final int HISTORY_MAGIC=0x42534831;
    private static final int HISTORY_VERSION=2;
    private static final int MAX_HISTORY=100000;
    private final LinkedHashMap<Long,Long> recent=new LinkedHashMap<Long,Long>();
    private final LinkedHashSet<Long> visited=new LinkedHashSet<Long>();
    private final LinkedHashSet<Long> received=new LinkedHashSet<Long>();
    private String historyKey;
    private long saveTimer;
    private boolean historyDirty;

    public NewChunks(){
        super("NewChunks","Green marks chunks with an observed player; red marks chunks with no recorded player visit.",Category.RENDER,0);
        addIntSetting("Overlay (0 player history, 1 recent, 2 both)",0,2,1,()->overlayMode,v->overlayMode=v);
        addIntSetting("Render range",32,512,16,()->range,v->range=v);
        addIntSetting("Recent lifetime seconds",10,600,10,()->lifetimeSeconds,v->lifetimeSeconds=v);
    }

    public void onChunkLoad(Chunk chunk){
        if(chunk==null||mc.player==null||mc.world==null)return;
        ensureHistoryLoaded();
        recordReceived(chunk.x,chunk.z);
        recent.put(key(chunk.x,chunk.z),System.currentTimeMillis());
        while(recent.size()>MAX_TRACKED){Iterator<Long> iterator=recent.keySet().iterator();iterator.next();iterator.remove();}
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null){saveHistory();historyKey=null;visited.clear();recent.clear();return;}
        ensureHistoryLoaded();
        scanLoadedRenderDistance();
        observePlayer(mc.player);
        for(EntityPlayer player:mc.world.playerEntities)if(player!=null)observePlayer(player);
        if(++saveTimer>=100){saveTimer=0;saveHistory();}
        long cutoff=System.currentTimeMillis()-lifetimeSeconds*1000L;
        Iterator<Map.Entry<Long,Long>> iterator=recent.entrySet().iterator();
        while(iterator.hasNext())if(iterator.next().getValue()<cutoff)iterator.remove();
    }

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||mc.getRenderManager()==null)return;
        RenderManagerCache render=new RenderManagerCache(mc.getRenderManager().viewerPosX,mc.getRenderManager().viewerPosY,mc.getRenderManager().viewerPosZ);
        int radiusChunks=(range+15)/16;
        if(overlayMode==0||overlayMode==2)renderPlayerHistory(render,radiusChunks);
        if((overlayMode==1||overlayMode==2)&&!recent.isEmpty())renderRecent(render,radiusChunks);
    }

    private void renderPlayerHistory(RenderManagerCache camera,int radiusChunks){
        double planeY=64.02D-camera.y;
        GlStateManager.pushMatrix();GL11.glPushAttrib(GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT);
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.disableDepth();GlStateManager.depthMask(false);
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
            for(int dx=-radiusChunks;dx<=radiusChunks;dx++)for(int dz=-radiusChunks;dz<=radiusChunks;dz++){
                if(dx*dx+dz*dz>radiusChunks*radiusChunks)continue;
                int cx=mc.player.chunkCoordX+dx,cz=mc.player.chunkCoordZ+dz;
                boolean observed=visited.contains(key(cx,cz));
                // Red means no player-presence observation exists in this client's saved history.
                float r=observed?.08F:1F,g=observed?1F:.08F,b=observed?.16F:.04F,a=.22F;
                double x=(double)cx*16D-camera.x,z=(double)cz*16D-camera.z;
                vertex(buffer,x,planeY,z,r,g,b,a);vertex(buffer,x+16D,planeY,z,r,g,b,a);
                vertex(buffer,x+16D,planeY,z+16D,r,g,b,a);vertex(buffer,x,planeY,z+16D,r,g,b,a);
            }
            Tessellator.getInstance().draw();

            buffer.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
            for(int dx=-radiusChunks;dx<=radiusChunks;dx++)for(int dz=-radiusChunks;dz<=radiusChunks;dz++){
                if(dx*dx+dz*dz>radiusChunks*radiusChunks)continue;
                int cx=mc.player.chunkCoordX+dx,cz=mc.player.chunkCoordZ+dz;
                boolean observed=visited.contains(key(cx,cz));
                float r=observed?.18F:1F,g=observed?1F:.12F,b=observed?.26F:.08F,a=.78F;
                double x=(double)cx*16D-camera.x,z=(double)cz*16D-camera.z;
                line(buffer,x,planeY,z,x+16D,planeY,z,r,g,b,a);line(buffer,x+16D,planeY,z,x+16D,planeY,z+16D,r,g,b,a);
                line(buffer,x+16D,planeY,z+16D,x,planeY,z+16D,r,g,b,a);line(buffer,x,planeY,z+16D,x,planeY,z,r,g,b,a);
            }
            GL11.glLineWidth(1.5F);Tessellator.getInstance().draw();
        }finally{GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);GL11.glPopAttrib();GlStateManager.popMatrix();}
    }

    private void renderRecent(RenderManagerCache camera,int radiusChunks){
        GlStateManager.pushMatrix();GL11.glPushAttrib(GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT);
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.disableDepth();GlStateManager.depthMask(false);GL11.glLineWidth(2F);
            long now=System.currentTimeMillis(),maxAge=Math.max(1,lifetimeSeconds*1000L);
            for(Map.Entry<Long,Long> entry:recent.entrySet()){
                int cx=(int)(entry.getKey()>>32),cz=(int)(long)entry.getKey();
                if(Math.abs(cx-mc.player.chunkCoordX)>radiusChunks||Math.abs(cz-mc.player.chunkCoordZ)>radiusChunks)continue;
                float alpha=(float)(.95D*(1D-Math.min(1D,(now-entry.getValue())/(double)maxAge)*.55D));
                double x=cx*16D-camera.x,z=cz*16D-camera.z;
                AxisAlignedBB box=new AxisAlignedBB(x,64.05D-camera.y,z,x+16D,64.18D-camera.y,z+16D);
                RenderGlobal.drawSelectionBoundingBox(box,1F,.82F,0F,alpha);
            }
        }finally{GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);GL11.glPopAttrib();GlStateManager.popMatrix();}
    }

    private static void vertex(BufferBuilder buffer,double x,double y,double z,float r,float g,float b,float a){buffer.pos(x,y,z).color(r,g,b,a).endVertex();}
    private static void line(BufferBuilder buffer,double x1,double y,double z1,double x2,double y2,double z2,float r,float g,float b,float a){vertex(buffer,x1,y,z1,r,g,b,a);vertex(buffer,x2,y,z2,r,g,b,a);}
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private void observePlayer(EntityPlayer player){
        if(player==null)return;
        if(visited.add(key(player.chunkCoordX,player.chunkCoordZ))){
            historyDirty=true;
            while(visited.size()>MAX_HISTORY){Iterator<Long> iterator=visited.iterator();iterator.next();iterator.remove();}
        }
    }
    private void scanLoadedRenderDistance(){
        if(!(mc.world.getChunkProvider() instanceof ChunkProviderClient))return;
        ChunkProviderClient provider=(ChunkProviderClient)mc.world.getChunkProvider();
        int radius=Math.max(2,mc.gameSettings.renderDistanceChunks);
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(dx*dx+dz*dz>radius*radius)continue;
            int cx=mc.player.chunkCoordX+dx,cz=mc.player.chunkCoordZ+dz;
            if(provider.getLoadedChunk(cx,cz)!=null)recordReceived(cx,cz);
        }
    }
    private void recordReceived(int x,int z){
        if(received.add(key(x,z))){
            historyDirty=true;
            while(received.size()>MAX_HISTORY){Iterator<Long> iterator=received.iterator();iterator.next();iterator.remove();}
        }
    }
    private void ensureHistoryLoaded(){
        if(mc.world==null)return;
        String current=makeHistoryKey();
        if(current.equals(historyKey))return;
        saveHistory();visited.clear();received.clear();historyKey=current;historyDirty=false;saveTimer=0;
        Path file=historyFile();
        if(file==null||!Files.isRegularFile(file))return;
        try(DataInputStream input=new DataInputStream(Files.newInputStream(file))){
            if(input.readInt()!=HISTORY_MAGIC)return;
            int version=input.readInt();if(version<1||version>HISTORY_VERSION)return;
            int count=input.readInt();if(count<0||count>MAX_HISTORY)return;
            for(int i=0;i<count;i++)visited.add(input.readLong());
            if(version>=2){
                int receivedCount=input.readInt();if(receivedCount<0||receivedCount>MAX_HISTORY)return;
                for(int i=0;i<receivedCount;i++)received.add(input.readLong());
            }
        }catch(IOException ignored){visited.clear();}
    }
    private String makeHistoryKey(){
        String server=ConfigManager.currentServerProfileName();
        if(server==null){String world=mc.world.getWorldInfo().getWorldName();server="single-"+Integer.toHexString(world==null?0:world.hashCode());}
        return server+"-dim"+mc.world.provider.getDimension();
    }
    private Path historyFile(){
        if(historyKey==null)return null;
        return mc.mcDataDir.toPath().resolve("Backspace").resolve("chunk-history").resolve(historyKey+".dat");
    }
    private void saveHistory(){
        if(!historyDirty||historyKey==null)return;
        Path file=historyFile();if(file==null)return;
        Path temporary=file.resolveSibling(file.getFileName().toString()+".tmp");
        try{
            Files.createDirectories(file.getParent());
            try(DataOutputStream output=new DataOutputStream(Files.newOutputStream(temporary))){
                output.writeInt(HISTORY_MAGIC);output.writeInt(HISTORY_VERSION);output.writeInt(visited.size());
                for(Long chunk:visited)output.writeLong(chunk.longValue());
                output.writeInt(received.size());
                for(Long chunk:received)output.writeLong(chunk.longValue());
            }
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            historyDirty=false;
        }catch(IOException ignored){try{Files.deleteIfExists(temporary);}catch(IOException ignoredAgain){}}
    }
    private static final class RenderManagerCache{final double x,y,z;RenderManagerCache(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}
    @Override protected void onEnable(){historyKey=null;if(mc.world!=null)ensureHistoryLoaded();}
    @Override protected void onDisable(){saveHistory();recent.clear();}
    @Override protected void onWorldUnload(){saveHistory();historyKey=null;visited.clear();received.clear();recent.clear();}
}
