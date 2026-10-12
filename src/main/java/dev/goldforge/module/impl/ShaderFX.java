package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import org.lwjgl.BufferUtils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** Native player silhouette outlines and animated crystal effects. */
public final class ShaderFX extends Module {
    private static final long MAX_TEXTURE_BYTES=16L*1024L*1024L;
    private static final long MAX_TEXTURE_PIXELS=16L*1024L*1024L;
    public int players=1,crystals=1,bodyTexture=0,style=0,intensity=100,range=96,throughWalls=1;
    /** Stored by ConfigManager; imports are copied into the instance's Backspace/shaders directory. */
    public String texturePath="";

    private static final String VERTEX_SHADER="#version 120\n varying vec2 uv; varying vec4 tint; varying vec3 normalEye; varying vec3 positionEye; void main(){ vec4 eye=gl_ModelViewMatrix*gl_Vertex; gl_Position=gl_ProjectionMatrix*eye; uv=gl_MultiTexCoord0.st; tint=gl_Color; normalEye=normalize(gl_NormalMatrix*gl_Normal); positionEye=eye.xyz; }";
    private static final String FRAGMENT_SHADER="#version 120\n uniform sampler2D uTexture; uniform sampler2D uOverlay; uniform float uTime; uniform float uStrength; uniform int uStyle; uniform int uHasTexture; varying vec2 uv; varying vec4 tint; varying vec3 normalEye; varying vec3 positionEye; void main(){ vec4 surface=texture2D(uTexture,uv)*tint; if(surface.a<0.035) discard; vec3 base=vec3(1.0,0.76,0.12); if(uStyle==1) base=vec3(0.72,0.30,1.0); if(uStyle==2) base=vec3(1.0,0.48,0.18); float pulse=0.78+0.22*sin(uTime*2.1+uv.y*7.0+uv.x*4.0); vec3 rainbow=0.5+0.5*cos(6.28318*(uv.x+uTime*0.08+vec3(0.0,0.33,0.67))); vec3 glow=uStyle==3?rainbow:base; if(uHasTexture!=0){ vec4 image=texture2D(uOverlay,uv); glow=mix(glow,image.rgb,image.a*0.82); } vec3 n=normalize(normalEye); vec3 v=normalize(-positionEye); float rim=pow(1.0-clamp(abs(dot(n,v)),0.0,1.0),1.35); float amount=clamp(uStrength/100.0,0.0,1.0); vec3 color=surface.rgb; color+=glow*rim*(1.35+3.2*amount)*pulse; gl_FragColor=vec4(clamp(color,0.0,1.0),surface.a); }";
    private static final String BLOOM_VERTEX="#version 120\n void main(){ gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; gl_TexCoord[0]=gl_MultiTexCoord0; }";
    private static final String BLOOM_FRAGMENT="#version 120\n uniform sampler2D uTexture; uniform vec2 uTexel; uniform vec2 uDirection; uniform vec3 uColor; uniform float uOpacity; uniform int uComposite; void main(){ vec2 p=gl_TexCoord[0].st; float m=texture2D(uTexture,p).r*0.227027; m+=(texture2D(uTexture,p+uTexel*uDirection*1.384615).r+texture2D(uTexture,p-uTexel*uDirection*1.384615).r)*0.316216; m+=(texture2D(uTexture,p+uTexel*uDirection*3.230769).r+texture2D(uTexture,p-uTexel*uDirection*3.230769).r)*0.070270; if(uComposite!=0) gl_FragColor=vec4(uColor,m*uOpacity); else gl_FragColor=vec4(m,m,m,1.0); }";
    private static final String MASK_VERTEX="#version 120\n void main(){ gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; }";
    private static final String MASK_FRAGMENT="#version 120\n void main(){ gl_FragColor=vec4(1.0); }";
    private static final Field OUTLINE_FRAMEBUFFER=outlineFramebufferField();

    private int shaderProgram;
    private int bloomProgram;
    private int maskProgram;
    private DynamicTexture dynamicTexture;
    private Framebuffer bloomHorizontal,bloomVertical,handMask;
    private ResourceLocation dynamicTextureLocation;
    private String loadedTexturePath="";
    private long loadedTextureModified=-1L;
    private String textureStatus="No texture imported";
    private String shaderStatus="Waiting for render";
    private boolean shaderAttempted;
    private boolean bloomAttempted;
    private boolean maskAttempted;
    private boolean renderingHandMask;
    private final Map<UUID,GlowState> glowingPlayers=new HashMap<UUID,GlowState>();
    private long lastGlowParticleAt;

    private static final class GlowState {
        final EntityPlayer player;
        final boolean wasGlowing;
        GlowState(EntityPlayer player){this.player=player;this.wasGlowing=player.isGlowing();}
    }

    public ShaderFX(){
        super("ShaderFX","Native player outlines and animated effects for end crystals.",Category.RENDER,0);
        addIntSetting("Player outline (0/1)",0,1,1,()->players,v->players=v);
        addIntSetting("End crystal glow (0/1)",0,1,1,()->crystals,v->crystals=v);
        addIntSetting("Imported crystal texture (0/1)",0,1,0,()->bodyTexture,v->bodyTexture=v);
        addIntSetting("Style (0 Gold, 1 Violet, 2 Ember, 3 Prism)",0,3,1,()->style,v->style=v);
        addIntSetting("Intensity",15,100,5,()->intensity,v->intensity=v);
        addIntSetting("Range",16,128,16,()->range,v->range=v);
        addIntSetting("Through walls (0/1)",0,1,1,()->throughWalls,v->throughWalls=v);
    }

    /** Opens a file picker and stores a portable PNG copy under the Minecraft instance directory. */
    public void chooseTexture(){
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Import an overlay texture");
        chooser.setFileFilter(new FileNameExtensionFilter("Images (*.png, *.jpg, *.jpeg)","png","jpg","jpeg"));
        if(!texturePath.isEmpty()){
            File current=new File(texturePath);
            File parent=current.getParentFile();
            if(parent!=null&&parent.isDirectory())chooser.setCurrentDirectory(parent);
        }
        if(chooser.showOpenDialog(null)!=JFileChooser.APPROVE_OPTION)return;
        File selected=chooser.getSelectedFile();
        if(selected==null||!selected.isFile())return;
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
        }catch(Exception failure){textureStatus="Import failed: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
    }

    public String textureLabel(){
        if(!texturePath.isEmpty())return new File(texturePath).getName();
        return "No image";
    }
    public String textureStatus(){return textureStatus;}
    public String shaderStatus(){return shaderStatus;}

    /** Captures the exact first-person hand/item geometry, then blooms it before vanilla draws it normally. */
    public void onSpecificHandPre(RenderSpecificHandEvent event){
        if(renderingHandMask||players==0||mc.player==null||mc.world==null||event==null)return;
        if(!OpenGlHelper.isFramebufferEnabled())return;
        ensureMaskProgram();ensureBloomProgram();
        if(maskProgram==0||bloomProgram==0)return;
        int width=Math.max(1,mc.displayWidth),height=Math.max(1,mc.displayHeight);
        if(handMask!=null&&(handMask.framebufferWidth!=width||handMask.framebufferHeight!=height)){handMask.deleteFramebuffer();handMask=null;}
        if(handMask==null){handMask=new Framebuffer(width,height,true);handMask.setFramebufferFilter(GL11.GL_LINEAR);}

        int previousFramebuffer=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousMatrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        boolean captured=false;
        try{
            handMask.bindFramebuffer(true);
            handMask.framebufferClear();
            GL11.glViewport(0,0,width,height);
            GlStateManager.disableBlend();GlStateManager.enableDepth();GlStateManager.depthMask(true);
            GlStateManager.color(1F,1F,1F,1F);
            GL20.glUseProgram(maskProgram);
            renderingHandMask=true;
            AbstractClientPlayer player=(AbstractClientPlayer)mc.player;
            mc.entityRenderer.itemRenderer.renderItemInFirstPerson(player,event.getPartialTicks(),event.getInterpolatedPitch(),event.getHand(),event.getSwingProgress(),event.getItemStack(),event.getEquipProgress());
            captured=true;
        }catch(Throwable failure){
            shaderStatus="Hand mask unavailable: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
        }finally{
            renderingHandMask=false;
            GL20.glUseProgram(previousProgram);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,previousFramebuffer);
            GL11.glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
            GL13.glActiveTexture(previousTexture);
            GL11.glMatrixMode(previousMatrixMode);
            GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableAlpha();GlStateManager.enableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);
        }
        if(captured)renderBloomSource(handMask);
    }

    @Override protected void onDisable(){restoreGlowingPlayers();releaseTexture();releaseBloom();releaseMask();releaseShader();}
    @Override protected void onWorldUnload(){restoreGlowingPlayers();}

    @Override public void tick(){
        if(players==0||mc.world==null||mc.player==null){restoreGlowingPlayers();return;}
        Map<UUID,EntityPlayer> eligible=new HashMap<UUID,EntityPlayer>();
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof EntityPlayer)||entity==mc.player||entity.isDead||mc.player.getDistance(entity)>range)continue;
            EntityPlayer target=(EntityPlayer)entity;
            UUID id=target.getUniqueID();eligible.put(id,target);
            GlowState state=glowingPlayers.get(id);
            if(state==null){state=new GlowState(target);glowingPlayers.put(id,state);}
            // Minecraft's entity-outline pass consumes this vanilla flag and traces
            // the rendered model silhouette instead of tinting the model surface.
            if(!target.isGlowing())target.setGlowing(true);
        }
        Iterator<Map.Entry<UUID,GlowState>> tracked=glowingPlayers.entrySet().iterator();
        while(tracked.hasNext()){
            Map.Entry<UUID,GlowState> entry=tracked.next();
            if(!eligible.containsKey(entry.getKey())){restoreGlow(entry.getValue());tracked.remove();}
        }
        long now=System.currentTimeMillis();if(now-lastGlowParticleAt<90L)return;lastGlowParticleAt=now;
        int emitted=0;
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof EntityPlayer)||entity==mc.player||entity.isDead||mc.player.getDistance(entity)>range)continue;
            AxisAlignedBB box=entity.getEntityBoundingBox();
            for(int i=0;i<3;i++){
                double x=box.minX+Math.random()*(box.maxX-box.minX);
                double y=box.minY+Math.random()*(box.maxY-box.minY);
                double z=box.minZ+Math.random()*(box.maxZ-box.minZ);
                mc.world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK,x,y,z,0D,.015D,0D);
            }
            if(++emitted>=6)break;
        }
    }

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||mc.getRenderManager()==null)return;
        ensureTexture();ensureShader();
        double vx=mc.getRenderManager().viewerPosX,vy=mc.getRenderManager().viewerPosY,vz=mc.getRenderManager().viewerPosZ;
        int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager.pushMatrix();
        try{
            GL20.glUseProgram(0);
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.enableTexture2D();GlStateManager.disableCull();
            if(throughWalls!=0)GlStateManager.disableDepth();else GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            long now=System.currentTimeMillis();
            if(crystals!=0){
                for(Entity entity:mc.world.loadedEntityList){
                    if(!(entity instanceof EntityEnderCrystal)||mc.player.getDistance(entity)>range)continue;
                    EntityEnderCrystal crystal=(EntityEnderCrystal)entity;
                    AxisAlignedBB box=crystal.getEntityBoundingBox().offset(-vx,-vy,-vz).grow(.08D);
                    drawGlow(box,now,1F);
                    drawCrystalRings(crystal,partialTicks,vx,vy,vz,now);
                    if(bodyTexture!=0&&dynamicTextureLocation!=null)drawTexturedBox(box,now,.28F);
                }
            }
            renderPlayerBloom();
        }finally{
            GL20.glUseProgram(previousProgram);GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableAlpha();GlStateManager.enableTexture2D();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);
            GlStateManager.popMatrix();
        }
    }

    private void drawGlow(AxisAlignedBB box,long now,float alpha){
        float pulse=.75F+.25F*(float)Math.sin(now/260.0D+box.minX*.13D+box.minZ*.11D);
        float[] color=styleColor();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);GL11.glEnable(GL11.GL_LINE_SMOOTH);
        // Keep line width at the portable 1 px value. Wider GL lines are not
        // supported consistently across drivers, so layer expanded traces instead.
        GL11.glLineWidth(1F);
        for(int layer=3;layer>=1;layer--){
            double spread=layer*.035D;
            float layerAlpha=(4-layer)*.075F*pulse*alpha;
            RenderGlobal.drawSelectionBoundingBox(box.grow(spread),color[0],color[1],color[2],layerAlpha);
        }
        RenderGlobal.drawSelectionBoundingBox(box,Math.min(1F,color[0]+.30F),Math.min(1F,color[1]+.30F),Math.min(1F,color[2]+.30F),.98F*pulse*alpha);
        GL11.glPopAttrib();
    }

    /** Adds a broad, color-tinted bloom around the vanilla model-silhouette mask. */
    private void renderPlayerBloom(){
        if(players==0||glowingPlayers.isEmpty())return;
        if(OUTLINE_FRAMEBUFFER==null||!OpenGlHelper.isFramebufferEnabled()){shaderStatus="Bloom unavailable: entity framebuffer missing";return;}
        try{
            Object value=OUTLINE_FRAMEBUFFER.get(mc.renderGlobal);
            if(!(value instanceof Framebuffer)){shaderStatus="Bloom waiting for vanilla outline";return;}
            renderBloomSource((Framebuffer)value);
        }catch(Exception failure){
            shaderStatus="Bloom unavailable: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
        }
    }

    private void renderBloomSource(Framebuffer source){
        if(source==null)return;
        try{
            ensureBloomProgram();
            if(bloomProgram==0)return;
            int width=Math.max(1,mc.displayWidth/2),height=Math.max(1,mc.displayHeight/2);
            ensureBloomTargets(width,height);

            int previousFramebuffer=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int previousMatrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            int previousTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
            try{
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GlStateManager.disableBlend();
                renderBloomPass(source,bloomHorizontal,1F/source.framebufferTextureWidth,1F/source.framebufferTextureHeight,1F,0F,false,width,height);
                renderBloomPass(bloomHorizontal,bloomVertical,1F/bloomHorizontal.framebufferTextureWidth,1F/bloomHorizontal.framebufferTextureHeight,0F,1F,false,width,height);

                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,previousFramebuffer);
                GL11.glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
                GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,1,1,1);
                float[] color=styleColor();
                setBloomUniforms(1F/bloomVertical.framebufferTextureWidth,1F/bloomVertical.framebufferTextureHeight,0F,0F,true,color,Math.min(1F,intensity/100F));
                renderFramebufferTexture(bloomVertical,mc.displayWidth,mc.displayHeight);
            }finally{
                GL20.glUseProgram(previousProgram);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,previousFramebuffer);
                GL11.glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
                GL13.glActiveTexture(previousTexture);
                GL11.glMatrixMode(previousMatrixMode);
                GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableAlpha();GlStateManager.enableTexture2D();
                GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);
            }
            shaderStatus="Gaussian silhouette bloom active";
        }catch(Exception failure){
            shaderStatus="Bloom unavailable: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
        }
    }

    private void renderBloomPass(Framebuffer source,Framebuffer target,float texelX,float texelY,float directionX,float directionY,boolean composite,int width,int height){
        target.bindFramebuffer(true);GlStateManager.disableBlend();GlStateManager.enableTexture2D();
        setBloomUniforms(texelX,texelY,directionX,directionY,composite,styleColor(),1F);
        renderFramebufferTexture(source,width,height);
    }

    private void renderFramebufferTexture(Framebuffer source,int width,int height){
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try{source.framebufferRenderExt(width,height,false);}
        finally{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
        }
    }

    private void setBloomUniforms(float texelX,float texelY,float directionX,float directionY,boolean composite,float[] color,float opacity){
        GL20.glUseProgram(bloomProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(bloomProgram,"uTexture"),0);
        GL20.glUniform2f(GL20.glGetUniformLocation(bloomProgram,"uTexel"),texelX,texelY);
        GL20.glUniform2f(GL20.glGetUniformLocation(bloomProgram,"uDirection"),directionX,directionY);
        GL20.glUniform3f(GL20.glGetUniformLocation(bloomProgram,"uColor"),color[0],color[1],color[2]);
        GL20.glUniform1f(GL20.glGetUniformLocation(bloomProgram,"uOpacity"),opacity);
        GL20.glUniform1i(GL20.glGetUniformLocation(bloomProgram,"uComposite"),composite?1:0);
    }

    private void ensureBloomProgram(){
        if(bloomProgram!=0||bloomAttempted)return;
        bloomAttempted=true;
        try{bloomProgram=compileProgram(BLOOM_VERTEX,BLOOM_FRAGMENT);}
        catch(RuntimeException failure){bloomProgram=0;shaderStatus="Bloom GLSL error: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
    }

    private void ensureMaskProgram(){
        if(maskProgram!=0||maskAttempted)return;
        maskAttempted=true;
        try{maskProgram=compileProgram(MASK_VERTEX,MASK_FRAGMENT);}
        catch(RuntimeException failure){maskProgram=0;shaderStatus="Hand mask GLSL error: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
    }

    private void ensureBloomTargets(int width,int height){
        if(bloomHorizontal!=null&&(bloomHorizontal.framebufferWidth!=width||bloomHorizontal.framebufferHeight!=height))releaseBloomTargets();
        if(bloomHorizontal==null){bloomHorizontal=new Framebuffer(width,height,false);bloomHorizontal.setFramebufferFilter(GL11.GL_LINEAR);}
        if(bloomVertical==null){bloomVertical=new Framebuffer(width,height,false);bloomVertical.setFramebufferFilter(GL11.GL_LINEAR);}
    }

    private void releaseBloomTargets(){
        if(bloomHorizontal!=null){bloomHorizontal.deleteFramebuffer();bloomHorizontal=null;}
        if(bloomVertical!=null){bloomVertical.deleteFramebuffer();bloomVertical=null;}
    }

    private void releaseBloom(){
        releaseBloomTargets();
        if(bloomProgram!=0){GL20.glDeleteProgram(bloomProgram);bloomProgram=0;}
        bloomAttempted=false;
    }

    private void releaseMask(){
        if(handMask!=null){handMask.deleteFramebuffer();handMask=null;}
        if(maskProgram!=0){GL20.glDeleteProgram(maskProgram);maskProgram=0;}
        maskAttempted=false;renderingHandMask=false;
    }

    private static Field outlineFramebufferField(){
        try{return ReflectionHelper.findField(RenderGlobal.class,"entityOutlineFramebuffer","field_175015_z");}
        catch(Exception ignored){return null;}
    }

    private void drawTexturedBox(AxisAlignedBB box,long now,float alpha){
        bindOverlayTexture();useShader((float)(now%100000L)/1000F,1F);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
        double x0=box.minX,y0=box.minY,z0=box.minZ,x1=box.maxX,y1=box.maxY,z1=box.maxZ;
        face(b,x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,alpha);face(b,x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1,alpha);
        face(b,x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1,alpha);face(b,x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,alpha);
        face(b,x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,alpha);face(b,x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0,alpha);
        Tessellator.getInstance().draw();GL20.glUseProgram(0);
    }

    private void drawCrystalRings(EntityEnderCrystal crystal,float partialTicks,double vx,double vy,double vz,long now){
        double x=crystal.lastTickPosX+(crystal.posX-crystal.lastTickPosX)*partialTicks-vx;
        double y=crystal.lastTickPosY+(crystal.posY-crystal.lastTickPosY)*partialTicks-vy+.45D;
        double z=crystal.lastTickPosZ+(crystal.posZ-crystal.lastTickPosZ)*partialTicks-vz;
        float[] c=styleColor();GL11.glLineWidth(2F);GL11.glEnable(GL11.GL_LINE_SMOOTH);
        for(int ring=0;ring<2;ring++){
            double radius=.72D+ring*.23D,rotation=now/900.0D+ring*Math.PI*.5D;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for(int i=0;i<48;i++){double angle=Math.PI*2D*i/48D+rotation;GL11.glColor4f(c[0],c[1],c[2],.78F-ring*.18F);GL11.glVertex3d(x+Math.cos(angle)*radius,y+ring*.22D,z+Math.sin(angle)*radius);}
            GL11.glEnd();
        }
        GL11.glDisable(GL11.GL_LINE_SMOOTH);GlStateManager.color(1F,1F,1F,1F);
    }

    private static void face(BufferBuilder b,double x0,double y0,double z0,double x1,double y1,double z1,double x2,double y2,double z2,double x3,double y3,double z3,float alpha){
        int a=(int)(255F*Math.max(0F,Math.min(1F,alpha)));
        b.pos(x0,y0,z0).tex(0,1).color(255,255,255,a).endVertex();b.pos(x1,y1,z1).tex(1,1).color(255,255,255,a).endVertex();
        b.pos(x2,y2,z2).tex(1,0).color(255,255,255,a).endVertex();b.pos(x3,y3,z3).tex(0,0).color(255,255,255,a).endVertex();
    }
    private void useShader(float time,float alpha){
        if(shaderProgram==0)return;
        GL20.glUseProgram(shaderProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(shaderProgram,"uTexture"),0);
        GL20.glUniform1i(GL20.glGetUniformLocation(shaderProgram,"uOverlay"),1);
        GL20.glUniform1f(GL20.glGetUniformLocation(shaderProgram,"uTime"),time);
        GL20.glUniform1f(GL20.glGetUniformLocation(shaderProgram,"uStrength"),Math.min(100F,Math.max(0F,intensity*alpha)));
        GL20.glUniform1i(GL20.glGetUniformLocation(shaderProgram,"uStyle"),style);
        GL20.glUniform1i(GL20.glGetUniformLocation(shaderProgram,"uHasTexture"),bodyTexture!=0&&dynamicTextureLocation!=null?1:0);
    }

    private void restoreGlowingPlayers(){
        for(GlowState state:glowingPlayers.values())restoreGlow(state);
        glowingPlayers.clear();
    }
    private static void restoreGlow(GlowState state){
        if(state!=null&&state.player!=null&&state.player.isGlowing()!=state.wasGlowing)state.player.setGlowing(state.wasGlowing);
    }

    private float[] styleColor(){
        switch(style){case 1:return new float[]{.72F,.30F,1F};case 2:return new float[]{1F,.48F,.18F};case 3:return new float[]{.45F,.78F,1F};default:return new float[]{1F,.76F,.12F};}
    }

    private void ensureShader(){
        if(shaderProgram!=0||shaderAttempted)return;
        shaderAttempted=true;
        try{shaderProgram=compileProgram(VERTEX_SHADER,FRAGMENT_SHADER);shaderStatus="GLSL 1.20 ready";}
        catch(RuntimeException failure){shaderProgram=0;shaderStatus="GLSL error: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
    }
    private int compileProgram(String vertexSource,String fragmentSource){
        int vertex=compile(GL20.GL_VERTEX_SHADER,vertexSource),fragment=compile(GL20.GL_FRAGMENT_SHADER,fragmentSource),program=GL20.glCreateProgram();
        GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==GL11.GL_FALSE){String log=GL20.glGetProgramInfoLog(program,2048);GL20.glDeleteProgram(program);GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);throw new RuntimeException(log);}
        GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);return program;
    }
    private int compile(int type,String source){
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE){String log=GL20.glGetShaderInfoLog(shader,2048);GL20.glDeleteShader(shader);throw new RuntimeException(log);}
        return shader;
    }

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
    private void bindOverlayTexture(){
        ensureTexture();if(dynamicTextureLocation!=null)mc.getTextureManager().bindTexture(dynamicTextureLocation);else GlStateManager.bindTexture(0);
    }
    private void releaseTexture(){
        if(dynamicTextureLocation!=null)mc.getTextureManager().deleteTexture(dynamicTextureLocation);
        dynamicTexture=null;dynamicTextureLocation=null;loadedTexturePath="";loadedTextureModified=-1L;
    }
    private void releaseShader(){if(shaderProgram!=0){GL20.glDeleteProgram(shaderProgram);shaderProgram=0;}shaderAttempted=false;shaderStatus="Waiting for render";}
}
