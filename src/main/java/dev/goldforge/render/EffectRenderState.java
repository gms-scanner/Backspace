package dev.goldforge.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Restores both OpenGL state and Minecraft's cached state after rendering models into an FBO. */
final class EffectRenderState implements AutoCloseable {
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int framebuffer = GL11.glGetInteger(0x8CA6); // FRAMEBUFFER_BINDING, also EXT
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
    private final float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int shade = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
    private final int cull = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
    private final int materialFace = GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_FACE), materialMode = GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_PARAMETER);
    private final float offsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), offsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
    private final float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
    private final int[] caps = {GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_ALPHA_TEST, GL11.GL_CULL_FACE,
            GL11.GL_LIGHTING, GL11.GL_FOG, GL11.GL_NORMALIZE, 0x803A, GL11.GL_COLOR_MATERIAL, GL11.GL_POLYGON_OFFSET_FILL};
    private final boolean[] enabled = new boolean[caps.length];
    private final boolean[] lights = new boolean[8], textures = new boolean[4];
    private final int[] bindings = new int[4];
    private final FloatBuffer color = BufferUtils.createFloatBuffer(4);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(4);
    private final java.nio.ByteBuffer colorMask = BufferUtils.createByteBuffer(4);

    EffectRenderState() {
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        for (int i = 0; i < caps.length; i++) enabled[i] = GL11.glIsEnabled(caps[i]);
        for (int i = 0; i < lights.length; i++) lights[i] = GL11.glIsEnabled(GL11.GL_LIGHT0 + i);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        for (int i = 0; i < 4; i++) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
            bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textures[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix();
        }
        GlStateManager.setActiveTexture(activeTexture);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        GL11.glMatrixMode(matrixMode);
    }

    int framebuffer() { return framebuffer; }
    void bindDestination() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
    }

    @Override public void close() {
        GL20.glUseProgram(program);
        bindDestination();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
        for (int i = 0; i < 4; i++) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
            GlStateManager.bindTexture(bindings[i]);
            if (textures[i]) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        }
        GlStateManager.setActiveTexture(activeTexture);
        GL11.glMatrixMode(matrixMode);
        if (enabled[0]) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (enabled[1]) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (enabled[2]) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        if (enabled[3]) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (enabled[4]) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (enabled[5]) GlStateManager.enableFog(); else GlStateManager.disableFog();
        if (enabled[6]) GlStateManager.enableNormalize(); else GlStateManager.disableNormalize();
        if (enabled[7]) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
        if (enabled[8]) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
        if (enabled[9]) GlStateManager.enablePolygonOffset(); else GlStateManager.disablePolygonOffset();
        for (int i = 0; i < lights.length; i++) {
            if (lights[i]) GlStateManager.enableLight(i); else GlStateManager.disableLight(i);
        }
        GlStateManager.depthFunc(depthFunc); GlStateManager.depthMask(depthMask);
        GlStateManager.alphaFunc(alphaFunc, alphaRef);
        GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GlStateManager.shadeModel(shade);
        GlStateManager.cullFace(cull == GL11.GL_FRONT ? GlStateManager.CullFace.FRONT :
                cull == GL11.GL_FRONT_AND_BACK ? GlStateManager.CullFace.FRONT_AND_BACK : GlStateManager.CullFace.BACK);
        GlStateManager.doPolygonOffset(offsetFactor, offsetUnits);
        GlStateManager.colorMaterial(materialFace, materialMode);
        GlStateManager.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
        GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GL11.glPopAttrib();
    }
}
