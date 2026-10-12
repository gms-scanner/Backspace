package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** Batched player boxes and tracers, with friend colors and strict distance filtering. */
public final class PlayerESP extends Module {
    public int boxes=1,tracers=1,range=128;
    public PlayerESP(){
        super("Player ESP","Shows player boxes and tracers; friends are green and others gold.",Category.RENDER,0);
        addIntSetting("ESP boxes (0/1)",0,1,1,()->boxes,v->boxes=v);
        addIntSetting("Tracers (0/1)",0,1,1,()->tracers,v->tracers=v);
        addIntSetting("Range",16,128,16,()->range,v->range=v);
    }
    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||mc.getRenderManager()==null||(boxes==0&&tracers==0))return;
        double vx=mc.getRenderManager().viewerPosX,vy=mc.getRenderManager().viewerPosY,vz=mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();GL11.glPushAttrib(GL11.GL_LINE_BIT);
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.disableDepth();GlStateManager.depthMask(false);
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
            for(EntityPlayer player:mc.world.playerEntities){
                if(player==mc.player||player.isDead||mc.player.getDistance(player)>range)continue;
                boolean friend=FriendManager.isFriend(player.getName());
                float r=friend?.12F:1F,g=friend?1F:.84F,b=friend?.24F:0F;
                double x=player.lastTickPosX+(player.posX-player.lastTickPosX)*partialTicks;
                double y=player.lastTickPosY+(player.posY-player.lastTickPosY)*partialTicks;
                double z=player.lastTickPosZ+(player.posZ-player.lastTickPosZ)*partialTicks;
                if(tracers!=0)addLine(buffer,0D,0D,0D,x-vx,y+player.height*.55D-vy,z-vz,r,g,b,.92F);
                if(boxes!=0){
                    AxisAlignedBB a=player.getEntityBoundingBox();
                    double minX=a.minX+x-player.posX-vx,maxX=a.maxX+x-player.posX-vx;
                    double minY=a.minY+y-player.posY-vy,maxY=a.maxY+y-player.posY-vy;
                    double minZ=a.minZ+z-player.posZ-vz,maxZ=a.maxZ+z-player.posZ-vz;
                    // Bottom and top rectangles, then four vertical edges.
                    addLine(buffer,minX,minY,minZ,maxX,minY,minZ,r,g,b,.96F);addLine(buffer,maxX,minY,minZ,maxX,minY,maxZ,r,g,b,.96F);
                    addLine(buffer,maxX,minY,maxZ,minX,minY,maxZ,r,g,b,.96F);addLine(buffer,minX,minY,maxZ,minX,minY,minZ,r,g,b,.96F);
                    addLine(buffer,minX,maxY,minZ,maxX,maxY,minZ,r,g,b,.96F);addLine(buffer,maxX,maxY,minZ,maxX,maxY,maxZ,r,g,b,.96F);
                    addLine(buffer,maxX,maxY,maxZ,minX,maxY,maxZ,r,g,b,.96F);addLine(buffer,minX,maxY,maxZ,minX,maxY,minZ,r,g,b,.96F);
                    addLine(buffer,minX,minY,minZ,minX,maxY,minZ,r,g,b,.96F);addLine(buffer,maxX,minY,minZ,maxX,maxY,minZ,r,g,b,.96F);
                    addLine(buffer,maxX,minY,maxZ,maxX,maxY,maxZ,r,g,b,.96F);addLine(buffer,minX,minY,maxZ,minX,maxY,maxZ,r,g,b,.96F);
                }
            }
            GL11.glLineWidth(1.8F);Tessellator.getInstance().draw();
        }finally{GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);GL11.glPopAttrib();GlStateManager.popMatrix();}
    }
    private static void addLine(BufferBuilder buffer,double x1,double y1,double z1,double x2,double y2,double z2,float r,float g,float b,float a){
        buffer.pos(x1,y1,z1).color(r,g,b,a).endVertex();buffer.pos(x2,y2,z2).color(r,g,b,a).endVertex();
    }
}
