package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** Billboard name labels rendered from interpolated entity positions. */
public final class Nametags extends Module {
    public int scale=10,red=255,green=255,blue=255,background=1,shadow=1,verticalOffset=2,health=1,distance=0,players=1,otherLiving=0,range=128;
    public Nametags(){
        super("Nametags","Renders configurable player labels with optional health and distance.",Category.RENDER,0);
        addIntSetting("Scale (tenths)",5,20,1,()->scale,v->scale=v);addIntSetting("Red",0,255,5,()->red,v->red=v);
        addIntSetting("Green",0,255,5,()->green,v->green=v);addIntSetting("Blue",0,255,5,()->blue,v->blue=v);
        addIntSetting("Background (0/1)",0,1,1,()->background,v->background=v);addIntSetting("Shadow (0/1)",0,1,1,()->shadow,v->shadow=v);
        addIntSetting("Vertical offset",0,6,1,()->verticalOffset,v->verticalOffset=v);addIntSetting("Health (0/1)",0,1,1,()->health,v->health=v);
        addIntSetting("Distance (0/1)",0,1,1,()->distance,v->distance=v);addIntSetting("Players (0/1)",0,1,1,()->players,v->players=v);
        addIntSetting("Other living (0/1)",0,1,1,()->otherLiving,v->otherLiving=v);addIntSetting("Range",16,256,16,()->range,v->range=v);
    }
    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||mc.getRenderManager()==null||(players==0&&otherLiving==0))return;
        double viewX=mc.getRenderManager().viewerPosX,viewY=mc.getRenderManager().viewerPosY,viewZ=mc.getRenderManager().viewerPosZ;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.depthMask(false);
            for(Entity entity:mc.world.loadedEntityList){
                if(entity==mc.player||entity.isDead||!(entity instanceof EntityLivingBase))continue;
                boolean isPlayer=entity instanceof EntityPlayer;if((isPlayer&&players==0)||(!isPlayer&&otherLiving==0))continue;
                double d=mc.player.getDistance(entity);if(d>range)continue;
                EntityLivingBase living=(EntityLivingBase)entity;
                double x=entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partialTicks-viewX;
                double y=entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partialTicks-viewY+entity.height+verticalOffset*.05D;
                double z=entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partialTicks-viewZ;
                String label=entity.getDisplayName().getFormattedText();
                if(health!=0)label+=" §c"+String.format(java.util.Locale.ROOT,"%.1f",living.getHealth());
                if(distance!=0)label+=" §7"+Math.round(d)+"m";
                renderLabel(label,x,y,z,isPlayer);
            }
        }finally{
            GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableLighting();GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);
            GlStateManager.popMatrix();GL11.glPopAttrib();
        }
    }
    /** Cancel vanilla player labels so the custom versions never double up. */
    public void cancelVanilla(RenderLivingEvent.Specials.Pre event){if(players!=0&&event.getEntity() instanceof EntityPlayer&&event.getEntity()!=mc.player)event.setCanceled(true);}
    private void renderLabel(String text,double x,double y,double z,boolean player){
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(x,y,z);GlStateManager.rotate(-mc.getRenderManager().playerViewY,0F,1F,0F);GlStateManager.rotate(mc.getRenderManager().playerViewX,1F,0F,0F);
            float factor=Math.max(.5F,Math.min(2F,scale/10F));GlStateManager.scale(-.025F*factor,-.025F*factor,.025F*factor);
            int width=mc.fontRenderer.getStringWidth(text),left=-width/2;
            if(background!=0){GlStateManager.disableTexture2D();GL11.glColor4f(.035F,.045F,.055F,.78F);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(left-3,-2);GL11.glVertex2f(left-3,mc.fontRenderer.FONT_HEIGHT+2);GL11.glVertex2f(-left+3,mc.fontRenderer.FONT_HEIGHT+2);GL11.glVertex2f(-left+3,-2);GL11.glEnd();GlStateManager.enableTexture2D();}
            int color=(red&255)<<16|(green&255)<<8|(blue&255);
            if(shadow!=0)mc.fontRenderer.drawStringWithShadow(text,left,0,color);else mc.fontRenderer.drawString(text,left,0,color);
        }finally{GlStateManager.color(1F,1F,1F,1F);GlStateManager.popMatrix();}
    }
}
