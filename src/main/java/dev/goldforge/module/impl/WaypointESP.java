package dev.goldforge.module.impl;

import dev.goldforge.WaypointManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** Renders saved waypoints in their matching dimension, with a strict distance cap. */
public final class WaypointESP extends Module {
    public int range=256,showLines=1;
    public WaypointESP(){super("Waypoints","Shows saved local waypoints in the current dimension.",Category.RENDER,0);addIntSetting("Range",32,512,32,()->range,v->range=v);addIntSetting("Tracer lines (0/1)",0,1,1,()->showLines,v->showLines=v);}
    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||mc.getRenderManager()==null)return;
        double vx=mc.getRenderManager().viewerPosX,vy=mc.getRenderManager().viewerPosY,vz=mc.getRenderManager().viewerPosZ;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.disableDepth();GlStateManager.depthMask(false);GL11.glLineWidth(2F);
            for(WaypointManager.Waypoint point:WaypointManager.all()){
                if(point.dimension!=mc.player.dimension)continue;
                double dx=point.x+.5D-mc.player.posX,dy=point.y+.5D-mc.player.posY,dz=point.z+.5D-mc.player.posZ;
                if(dx*dx+dy*dy+dz*dz>range*(double)range)continue;
                AxisAlignedBB box=new AxisAlignedBB(point.x-vx,point.y-vy,point.z-vz,point.x+1-vx,point.y+1-vy,point.z+1-vz);
                float r=((point.color>>16)&255)/255F,g=((point.color>>8)&255)/255F,b=(point.color&255)/255F;
                RenderGlobal.drawSelectionBoundingBox(box,r,g,b,.95F);
                if(showLines!=0){GL11.glBegin(GL11.GL_LINES);GL11.glColor4f(r,g,b,.78F);GL11.glVertex3d(mc.player.posX-vx,mc.player.posY+mc.player.getEyeHeight()-vy,mc.player.posZ-vz);GL11.glVertex3d(point.x+.5D-vx,point.y+.5D-vy,point.z+.5D-vz);GL11.glEnd();}
            }
        }finally{GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GL11.glPopAttrib();}
    }
}
