package dev.goldforge.module.impl;

import dev.goldforge.gui.GuiPreferences;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.render.SafeHoleScanner;
import dev.goldforge.render.SilhouetteShader;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Animated GLSL silhouettes and bloom for real models and single-block safe holes. */
public final class ShaderFX extends Module {
    private static final long MAX_TEXTURE_BYTES=16L*1024L*1024L;
    private static final long MAX_TEXTURE_PIXELS=16L*1024L*1024L;
    // Preserve previous field names for existing saved configurations.
    public int players=1,crystals=1,bodyTexture=0,style=3,intensity=85,range=64,throughWalls=1;
    public int hands=1,holes=1,glowRadius=10,outlineWidth=1,fill=16,animationSpeed=100;
    public int handStyle=-1,playerStyle=-1,crystalStyle=-1,holeStyle=6;
    public int holeRange=12,holeVertical=4,holeScanDelay=5,holeHeight=10,holeFill=28,bedrockOnly=0;
    public int entityLimit=32;
    public String texturePath="";
    private DynamicTexture dynamicTexture;
    private ResourceLocation dynamicTextureLocation;
    private String loadedTexturePath="",textureStatus="No texture imported";
    private long loadedTextureModified=-1L;
    private SilhouetteShader effects;
    private List<BlockPos> safeHoles=Collections.emptyList();
    private World scanWorld;
    private BlockPos scanCenter;
    private int scanTicks;
    private boolean drawingHand;

    public ShaderFX(){
        super("ShaderFX","Animated rainbow, galaxy and theme glows around hands, models and safe holes.",Category.RENDER,0);
        addIntSetting("Hands and held items",0,1,1,()->hands,v->hands=v);
        addIntSetting("Players",0,1,1,()->players,v->players=v);
        addIntSetting("End crystals",0,1,1,()->crystals,v->crystals=v);
        addIntSetting("Safe holes",0,1,1,()->holes,v->holes=v);
        addIntSetting("Style",0,6,1,()->style,v->style=v);
        addIntSetting("Hand style (-1 follows style)",-1,6,1,()->handStyle,v->handStyle=v);
        addIntSetting("Player style (-1 follows style)",-1,6,1,()->playerStyle,v->playerStyle=v);
        addIntSetting("Crystal style (-1 follows style)",-1,6,1,()->crystalStyle,v->crystalStyle=v);
        addIntSetting("Hole style (-1 follows style)",-1,6,1,()->holeStyle,v->holeStyle=v);
        addIntSetting("Glow intensity (%)",0,150,5,()->intensity,v->intensity=v);
        addIntSetting("Glow radius (pixels)",2,24,1,()->glowRadius,v->glowRadius=v);
        addIntSetting("Outline width (pixels)",1,4,1,()->outlineWidth,v->outlineWidth=v);
        addIntSetting("Model fill opacity (%)",0,80,2,()->fill,v->fill=v);
        addIntSetting("Animation speed (%)",0,200,10,()->animationSpeed,v->animationSpeed=v);
        addIntSetting("Entity range",16,128,8,()->range,v->range=v);
        addIntSetting("Models per category",4,64,4,()->entityLimit,v->entityLimit=v);
        addIntSetting("Through walls",0,1,1,()->throughWalls,v->throughWalls=v);
        addIntSetting("Hole scan range",2,24,1,()->holeRange,v->holeRange=v);
        addIntSetting("Hole vertical range",1,8,1,()->holeVertical,v->holeVertical=v);
        addIntSetting("Hole refresh (ticks)",1,20,1,()->holeScanDelay,v->holeScanDelay=v);
        addIntSetting("Hole side height (%)",0,100,5,()->holeHeight,v->holeHeight=v);
        addIntSetting("Hole fill opacity (%)",0,80,2,()->holeFill,v->holeFill=v);
        addIntSetting("Only all-bedrock holes",0,1,1,()->bedrockOnly,v->bedrockOnly=v);
        addIntSetting("Imported crystal overlay",0,1,1,()->bodyTexture,v->bodyTexture=v);
    }

    /** Opens the in-game overlay editor instead of a native Swing dialog. */
    public void chooseTexture(){
        if(mc.currentScreen!=null)mc.displayGuiScreen(new dev.goldforge.gui.ShaderOverlayGui(mc.currentScreen,this));
    }

    /** Validates and copies an image to the instance before exposing it to the GL renderer. */
    public boolean importTexturePath(String rawPath){
        if(rawPath==null||rawPath.trim().isEmpty()){
            texturePath="";releaseTexture();textureStatus="No image imported";dev.goldforge.ConfigManager.save();return true;
        }
        File selected=new File(rawPath.trim());
        try{
            BufferedImage image=readSafeImage(selected);
            Path directory=mc.mcDataDir.toPath().resolve("Backspace").resolve("shaders");
            Files.createDirectories(directory);
            Path destination=directory.resolve("overlay.png"),temporary=directory.resolve("overlay.png.tmp");
            if(!ImageIO.write(image,"png",temporary.toFile()))throw new IOException("Could not encode PNG");
            try{Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);}
            texturePath=destination.toAbsolutePath().toString();
            releaseTexture();textureStatus="Texture imported; upload pending";
            dev.goldforge.ConfigManager.save();
            return true;
        }catch(Exception failure){
            textureStatus="Import failed: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
            return false;
        }
    }

    public String textureLabel(){
        if(!texturePath.isEmpty())return new File(texturePath).getName();
        return "No image";
    }
    public String textureStatus(){return textureStatus;}
    public String shaderStatus(){return styleName(style)+" | "+(effects==null?"Waiting for render":effects.status());}
    @Override public String hudTag(){return "["+styleName(style)+"]";}
    private static String styleName(int value){
        String[] names={"Gold","Violet","Ember","Rainbow","Galaxy","Aurora","Theme"};
        return names[Math.max(0,Math.min(names.length-1,value))];
    }


    private SilhouetteShader effects(){
        if(effects==null)effects=new SilhouetteShader();
        return effects;
    }
    public boolean isCapturing(){return effects!=null&&effects.isCapturing();}

    @Override public void tick(){
        if(mc.world==null||mc.player==null||holes==0){clearHoles();return;}
        BlockPos center=new BlockPos(mc.player);
        if(scanWorld!=mc.world||!center.equals(scanCenter)||--scanTicks<=0){
            scanWorld=mc.world;scanCenter=center;
            scanTicks=Math.max(1,Math.min(20,holeScanDelay));
            safeHoles=SafeHoleScanner.scan(mc.world,center,holeRange,holeVertical,bedrockOnly!=0);
        }
    }

    @Override public void render(final float partialTicks){
        if(mc.world==null||mc.player==null||isCapturing())return;
        final RenderManager manager=mc.getRenderManager();
        final Frustum frustum=new Frustum();
        frustum.setPosition(manager.viewerPosX,manager.viewerPosY,manager.viewerPosZ);
        if(players!=0)renderModels(false,partialTicks,frustum);
        if(crystals!=0)renderModels(true,partialTicks,frustum);
        if(holes!=0&&!safeHoles.isEmpty()){
            final List<BlockPos> visible=new ArrayList<BlockPos>();
            for(BlockPos hole:safeHoles){
                if(SafeHoleScanner.isHole(mc.world,hole,bedrockOnly!=0)
                        &&frustum.isBoundingBoxInFrustum(new AxisAlignedBB(hole).grow(.2D)))visible.add(hole);
            }
            if(!visible.isEmpty())effects().draw(()->{
                GlStateManager.disableTexture2D();GlStateManager.disableCull();
                for(BlockPos hole:visible)drawHole(hole,manager);
            },false,throughWalls==0,appearance(holeStyle,holeFill,false),null);
        }
    }

    private void renderModels(boolean crystal,final float partialTicks,Frustum frustum){
        final List<Entity> models=new ArrayList<Entity>();
        double distance=Math.max(16,Math.min(128,range));
        for(Entity entity:mc.world.loadedEntityList){
            if(entity==mc.player||entity.isDead||entity.isInvisible()
                    ||mc.player.getDistanceSq(entity)>distance*distance)continue;
            if(crystal?!(entity instanceof EntityEnderCrystal):!(entity instanceof EntityPlayer))continue;
            if(entity instanceof EntityPlayer&&((EntityPlayer)entity).isSpectator())continue;
            if(!frustum.isBoundingBoxInFrustum(entity.getEntityBoundingBox().grow(.5D)))continue;
            models.add(entity);
        }
        models.sort(Comparator.comparingDouble(e->mc.player.getDistanceSq(e)));
        if(models.isEmpty())return;
        final int count=Math.min(Math.max(4,Math.min(64,entityLimit)),models.size());
        if(crystal&&bodyTexture!=0)ensureTexture();
        effects().draw(()->{
            RenderManager manager=mc.getRenderManager();
            boolean shadow=manager.isRenderShadow(),debug=manager.isDebugBoundingBox();
            manager.setRenderShadow(false);manager.setDebugBoundingBox(false);
            try{
                for(int i=0;i<count;i++)manager.renderEntityStatic(models.get(i),partialTicks,false);
            }finally{manager.setRenderShadow(shadow);manager.setDebugBoundingBox(debug);}
        },true,throughWalls==0,appearance(crystal?crystalStyle:playerStyle,fill,crystal&&bodyTexture!=0),null);
    }

    /** Render the normal hand between mask capture and composition, avoiding a second vanilla draw. */
    public void onSpecificHandPre(final RenderSpecificHandEvent event){
        if(drawingHand||isCapturing()||hands==0||mc.player==null||mc.world==null||event.isCanceled()
                ||mc.gameSettings.thirdPersonView!=0||mc.gameSettings.hideGUI||mc.player.isSpectator())return;
        final Runnable geometry=()->mc.entityRenderer.itemRenderer.renderItemInFirstPerson(mc.player,
                event.getPartialTicks(),event.getInterpolatedPitch(),event.getHand(),
                event.getSwingProgress(),event.getItemStack(),event.getEquipProgress());
        drawingHand=true;
        try{
            effects().draw(geometry,true,false,appearance(handStyle,fill,false),()->{
                geometry.run();
                event.setCanceled(true);
            });
        }finally{drawingHand=false;}
    }

    private SilhouetteShader.Appearance appearance(int override,int opacity,boolean imported){
        SilhouetteShader.Appearance a=new SilhouetteShader.Appearance();
        a.style=Math.max(0,Math.min(6,override<0?style:override));
        a.intensity=intensity;a.fill=opacity;a.radius=glowRadius;a.outline=outlineWidth;a.speed=animationSpeed;
        a.red=1F;a.green=.78F;a.blue=.08F;
        if(a.style==1){a.red=.67F;a.green=.26F;a.blue=1F;}
        if(a.style==2){a.red=1F;a.green=.32F;a.blue=.06F;}
        if(a.style==6)switch(GuiPreferences.theme){
            case 1:a.red=1F;a.green=.28F;a.blue=.35F;break;
            case 2:a.red=.3F;a.green=1F;a.blue=.72F;break;
            case 3:a.red=.3F;a.green=.65F;a.blue=1F;break;
            default:break;
        }
        a.overlay=imported?dynamicTextureLocation:null;
        return a;
    }

    private void drawHole(BlockPos pos,RenderManager manager){
        double x=pos.getX()-manager.viewerPosX+.025D,y=pos.getY()-manager.viewerPosY+.015D;
        double z=pos.getZ()-manager.viewerPosZ+.025D,s=.95D,h=Math.max(0,Math.min(100,holeHeight))/100D;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x,y,z);GL11.glVertex3d(x+s,y,z);GL11.glVertex3d(x+s,y,z+s);GL11.glVertex3d(x,y,z+s);
        if(h>0){
            GL11.glVertex3d(x,y,z);GL11.glVertex3d(x,y+h,z);GL11.glVertex3d(x+s,y+h,z);GL11.glVertex3d(x+s,y,z);
            GL11.glVertex3d(x+s,y,z+s);GL11.glVertex3d(x+s,y+h,z+s);GL11.glVertex3d(x,y+h,z+s);GL11.glVertex3d(x,y,z+s);
            GL11.glVertex3d(x,y,z+s);GL11.glVertex3d(x,y+h,z+s);GL11.glVertex3d(x,y+h,z);GL11.glVertex3d(x,y,z);
            GL11.glVertex3d(x+s,y,z);GL11.glVertex3d(x+s,y+h,z);GL11.glVertex3d(x+s,y+h,z+s);GL11.glVertex3d(x+s,y,z+s);
        }
        GL11.glEnd();
    }

    private void clearHoles(){safeHoles=Collections.emptyList();scanCenter=null;scanWorld=null;scanTicks=0;}
    @Override protected void onDisable(){clearHoles();releaseTexture();if(effects!=null)effects.release();}
    @Override protected void onWorldUnload(){onDisable();}
    private void ensureTexture(){
        if(texturePath==null||texturePath.trim().isEmpty()){releaseTexture();textureStatus="No image imported";return;}
        File file=new File(texturePath);long modified=file.isFile()?file.lastModified():-1L;
        if(dynamicTextureLocation!=null&&texturePath.equals(loadedTexturePath)&&modified==loadedTextureModified)return;
        releaseTexture();
        if(!file.isFile()){textureStatus="Texture file missing";return;}
        try{BufferedImage image=readSafeImage(file);dynamicTexture=new DynamicTexture(image);dynamicTextureLocation=mc.getTextureManager().getDynamicTextureLocation("backspace_shader_overlay",dynamicTexture);loadedTexturePath=texturePath;loadedTextureModified=modified;textureStatus=image.getWidth()+" x "+image.getHeight();}
        catch(Exception failure){textureStatus="Texture load failed";releaseTexture();}
    }
    private static BufferedImage readSafeImage(File file)throws IOException{
        if(!file.isFile())throw new IOException("Image file not found");
        if(file.length()>MAX_TEXTURE_BYTES)throw new IOException("Image must be 16 MB or smaller");
        try(ImageInputStream input=ImageIO.createImageInputStream(file)){
            if(input==null)throw new IOException("Unsupported image format");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Unsupported image format");
            ImageReader reader=readers.next();
            try{
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>4096||height>4096||(long)width*height>MAX_TEXTURE_PIXELS)throw new IOException("Image must be at most 4096 x 4096 and 16 megapixels");
                BufferedImage image=reader.read(0);
                if(image==null)throw new IOException("Image could not be decoded");
                return image;
            }finally{reader.dispose();}
        }
    }

    private void releaseTexture(){
        if(dynamicTextureLocation!=null)mc.getTextureManager().deleteTexture(dynamicTextureLocation);
        dynamicTexture=null;dynamicTextureLocation=null;loadedTexturePath="";loadedTextureModified=-1L;
    }

}
