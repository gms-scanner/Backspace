package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleExplosion;
import net.minecraft.client.particle.ParticleExplosionHuge;
import net.minecraft.client.particle.ParticleExplosionLarge;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;

/** Client-only render filtering for sign text and explosion effects. */
public final class NoRender extends Module {
    public int hideSignText=1, hideExplosionEffects=1;
    private SignRenderer signWrapper;
    private TileEntitySpecialRenderer<TileEntitySign> originalSignRenderer;
    private static final Field PARTICLE_LAYERS=field("fxLayers","field_78876_b");
    private static final Field PARTICLE_QUEUE=field("queue","field_187241_h");
    private static final ITextComponent EMPTY_LINE=new TextComponentString("");

    public NoRender() {
        super("NoRender","Hides sign text and explosion particles/sounds on your client.",Category.RENDER,0);
        addIntSetting("Hide sign text (0/1)",0,1,1,()->hideSignText,v->hideSignText=v);
        addIntSetting("Hide explosions (0/1)",0,1,1,()->hideExplosionEffects,v->hideExplosionEffects=v);
    }

    @Override protected void onEnable(){installSignFilter();}
    @Override protected void onDisable(){restoreSignRenderer();}
    @Override public void tick(){
        if(hideSignText!=0)installSignFilter();else restoreSignRenderer();
        if(hideExplosionEffects!=0)removeExplosionParticles();
    }

    public void onSound(PlaySoundEvent event){
        if(hideExplosionEffects!=0&&event.getName()!=null&&event.getName().toLowerCase().contains("explode"))event.setResultSound(null);
    }

    private void installSignFilter(){
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        if(dispatcher==null||dispatcher.renderers==null)return;
        TileEntitySpecialRenderer<?> current=dispatcher.renderers.get(TileEntitySign.class);
        if(current instanceof SignRenderer){signWrapper=(SignRenderer)current;return;}
        if(current==null)return;
        @SuppressWarnings("unchecked") TileEntitySpecialRenderer<TileEntitySign> delegate=(TileEntitySpecialRenderer<TileEntitySign>)current;
        originalSignRenderer=delegate;
        signWrapper=new SignRenderer(delegate);
        signWrapper.setRendererDispatcher(dispatcher);
        dispatcher.renderers.put(TileEntitySign.class,signWrapper);
    }

    private void restoreSignRenderer(){
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        if(dispatcher!=null&&dispatcher.renderers!=null&&signWrapper!=null&&dispatcher.renderers.get(TileEntitySign.class)==signWrapper&&originalSignRenderer!=null)
            dispatcher.renderers.put(TileEntitySign.class,originalSignRenderer);
        signWrapper=null; originalSignRenderer=null;
    }

    private void removeExplosionParticles(){
        if(mc.effectRenderer==null)return;
        try{
            if(PARTICLE_LAYERS!=null){
                Object layers=PARTICLE_LAYERS.get(mc.effectRenderer);
                for(int row=0;row<Array.getLength(layers);row++){
                    Object layer=Array.get(layers,row);
                    if(layer==null)continue;
                    for(int column=0;column<Array.getLength(layer);column++)removeFrom(Array.get(layer,column));
                }
            }
            if(PARTICLE_QUEUE!=null)removeFrom(PARTICLE_QUEUE.get(mc.effectRenderer));
        }catch(ReflectiveOperationException ignored){}
    }

    private void removeFrom(Object collection){
        if(!(collection instanceof Collection))return;
        Iterator<?> iterator=((Collection<?>)collection).iterator();
        while(iterator.hasNext()){
            Object value=iterator.next();
            if(value instanceof Particle&&(value instanceof ParticleExplosion||value instanceof ParticleExplosionLarge||value instanceof ParticleExplosionHuge))iterator.remove();
        }
    }

    private static Field field(String name,String srgName){
        try{return ReflectionHelper.findField(net.minecraft.client.particle.ParticleManager.class,name,srgName);}
        catch(Exception ignored){return null;}
    }

    private static final class SignRenderer extends TileEntitySpecialRenderer<TileEntitySign>{
        private final TileEntitySpecialRenderer<TileEntitySign> delegate;
        SignRenderer(TileEntitySpecialRenderer<TileEntitySign> delegate){this.delegate=delegate;}
        @Override public void render(TileEntitySign sign,double x,double y,double z,float partialTicks,int destroyStage,float alpha){
            ITextComponent[] text=sign.signText.clone();
            try{
                for(int i=0;i<sign.signText.length;i++)sign.signText[i]=EMPTY_LINE;
                delegate.render(sign,x,y,z,partialTicks,destroyStage,alpha);
            }finally{System.arraycopy(text,0,sign.signText,0,text.length);}
        }
    }
}
