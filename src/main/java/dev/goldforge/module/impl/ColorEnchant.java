package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

/** Replaces the shared 1.12.2 glint texture at runtime and restores it on disable. */
public final class ColorEnchant extends Module {
    public int red=255,green=205,blue=45;
    private static final ResourceLocation GLINT=new ResourceLocation("textures/misc/enchanted_item_glint.png");
    private ITextureObject original;
    private DynamicTexture recolored;
    private int lastColor=-1;
    public ColorEnchant(){
        super("ColorEnchant","Recolors the enchanted-item glint texture without changing item data.",Category.RENDER,0);
        addIntSetting("Red",0,255,5,()->red,v->red=v);addIntSetting("Green",0,255,5,()->green,v->green=v);addIntSetting("Blue",0,255,5,()->blue,v->blue=v);
    }
    @Override protected void onEnable(){
        if(mc.getTextureManager()==null)return;
        original=mc.getTextureManager().getTexture(GLINT);
        install();
    }
    @Override public void tick(){if(mc.getTextureManager()!=null&&packedColor()!=lastColor)install();}
    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
    private int packedColor(){return (red&255)<<16|(green&255)<<8|(blue&255);}
    private void install(){
        if(original==null){original=mc.getTextureManager().getTexture(GLINT);if(original==null)return;}
        int color=packedColor();if(color==lastColor&&mc.getTextureManager().getTexture(GLINT)==recolored)return;
        int r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        int max=Math.max(1,Math.max(r,Math.max(g,b)));
        int[] pixels=new int[64*64];
        // Two soft diagonal bands retain the vanilla repeating glint motion/UV layout.
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            int phase=Math.floorMod(x+y*2,32);int distance=Math.min(phase,32-phase);
            int alpha=distance<3?(distance==0?210:distance==1?145:65):0;
            int rr=Math.min(255,r*255/max),gg=Math.min(255,g*255/max),bb=Math.min(255,b*255/max);
            pixels[y*64+x]=(alpha<<24)|(rr<<16)|(gg<<8)|bb;
        }
        if(recolored==null)recolored=new DynamicTexture(64,64);
        System.arraycopy(pixels,0,recolored.getTextureData(),0,pixels.length);recolored.updateDynamicTexture();
        mc.getTextureManager().loadTexture(GLINT,recolored);lastColor=color;
    }
    private void restore(){
        if(mc.getTextureManager()!=null&&original!=null&&mc.getTextureManager().getTexture(GLINT)==recolored)
            mc.getTextureManager().loadTexture(GLINT,original);
        original=null;recolored=null;lastColor=-1;
    }
}
