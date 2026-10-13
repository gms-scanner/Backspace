package dev.goldforge.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Actual model mask -> two Gaussian blur passes -> animated GLSL silhouette composite. */
public final class SilhouetteShader {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final long started = System.nanoTime();
    private Framebuffer mask, horizontal, blurred;
    private int maskProgram, blurProgram, compositeProgram, sceneDepth;
    private boolean attempted, capturing, depthUnavailable, successfulCompositeLogged;
    private volatile boolean reload;
    private String status = "Waiting for render";

    public SilhouetteShader() {
        if (mc.getResourceManager() instanceof IReloadableResourceManager)
            ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(manager -> reload = true);
    }

    public boolean isCapturing() { return capturing; }
    public String status() { return status; }

    public static final class Appearance {
        public int style, intensity, fill, radius, outline, speed;
        public float red, green, blue;
        public ResourceLocation overlay;
    }

    /** beforeComposite lets the hand render normally after capture but before its colored overlay. */
    public void draw(Runnable geometry, boolean textured, boolean respectDepth, Appearance appearance, Runnable beforeComposite) {
        if (capturing || !supportsPipeline()) return;
        Framebuffer mainFramebuffer = mc.getFramebuffer();
        int width = mainFramebuffer.framebufferWidth;
        int height = mainFramebuffer.framebufferHeight;
        if (width <= 0 || height <= 0) {
            status = "Waiting for the main framebuffer";
            return;
        }

        // Compile/link before capturing render state. A missing GLSL capability should
        // never enter EffectRenderState or leave a half-restored fixed-function state.
        if (reload) {
            discardResources();
            reload = false;
        }
        if (!ensurePrograms()) return;

        try (EffectRenderState state = new EffectRenderState()) {
            ensureTargets(width, height, respectDepth);
            // Copy depth from Minecraft's actual main framebuffer. displayWidth can differ
            // from it with framebuffer wrappers, HiDPI displays, and shader loaders.
            boolean useDepth = respectDepth && copyMainDepth(mainFramebuffer, width, height);
            try (EffectRenderState capture = new EffectRenderState()) {
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.depthMask(true);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                mask.framebufferClear();
                mask.bindFramebuffer(true);
                bind(3, useDepth ? sceneDepth : 0);
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
                GlStateManager.disableBlend(); GlStateManager.enableDepth();
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
                GlStateManager.disableFog(); GlStateManager.color(1F, 1F, 1F, 1F);
                GL20.glUseProgram(maskProgram);
                uniform(maskProgram, "uTexture", 0); uniform(maskProgram, "uDepth", 3);
                uniform(maskProgram, "uTextured", textured ? 1 : 0);
                uniform(maskProgram, "uOccluded", useDepth ? 1 : 0);
                GL20.glUniform2f(location(maskProgram, "uResolution"), width, height);
                capturing = true;
                try { geometry.run(); } finally { capturing = false; }
            }
            GL20.glUseProgram(0);
            state.bindDestination();
            if (beforeComposite != null) beforeComposite.run();

            GlStateManager.disableDepth(); GlStateManager.depthMask(false);
            GlStateManager.disableAlpha(); GlStateManager.disableLighting(); GlStateManager.disableFog();
            GlStateManager.disableCull(); GlStateManager.disableBlend();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.colorMask(true, true, true, true);
            float radius = Math.max(1, Math.min(24, appearance.radius));
            blur(mask, horizontal, radius / (3.23F * mask.framebufferWidth), 0F);
            blur(horizontal, blurred, 0F, radius / (3.23F * horizontal.framebufferHeight));

            state.bindDestination();
            GL20.glUseProgram(compositeProgram);
            bind(0, mask.framebufferTexture); bind(1, blurred.framebufferTexture);
            if (appearance.overlay != null) {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE2);
                mc.getTextureManager().bindTexture(appearance.overlay);
            }
            uniform(compositeProgram, "uMask", 0); uniform(compositeProgram, "uBloom", 1);
            uniform(compositeProgram, "uOverlay", 2);
            uniform(compositeProgram, "uHasOverlay", appearance.overlay == null ? 0 : 1);
            uniform(compositeProgram, "uStyle", Math.max(0, Math.min(6, appearance.style)));
            GL20.glUniform2f(location(compositeProgram, "uResolution"), width, height);
            GL20.glUniform3f(location(compositeProgram, "uColor"), appearance.red, appearance.green, appearance.blue);
            scalar("uTime", (float) (((System.nanoTime() - started) / 1.0e9) % 16384D) * Math.max(0, Math.min(200, appearance.speed)) / 100F);
            scalar("uIntensity", Math.max(0, Math.min(150, appearance.intensity)) / 100F);
            scalar("uFill", Math.max(0, Math.min(80, appearance.fill)) / 100F);
            scalar("uOutline", Math.max(1, Math.min(4, appearance.outline)));
            GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            quad();
            status = useDepth || !respectDepth ? "GLSL model silhouettes + Gaussian bloom"
                    : "GLSL model silhouettes + Gaussian bloom (depth fallback)";
            if (!successfulCompositeLogged) {
                LogManager.getLogger("Backspace/ShaderFX").info(
                        "ShaderFX composite active: {}x{}, depth occlusion: {}", width, height, useDepth);
                successfulCompositeLogged = true;
            }
        } catch (RuntimeException failure) {
            status = "Shader unavailable: " + failure.getMessage();
            LogManager.getLogger("Backspace/ShaderFX").error(status, failure);
            // Avoid retrying a broken GPU path on every frame; toggling or resource reload retries it.
            discardResources(); attempted = true;
        } finally { capturing = false; }
    }

    private boolean supportsPipeline() {
        if (!OpenGlHelper.isFramebufferEnabled() || !OpenGlHelper.shadersSupported) {
            status = "Requires framebuffer and GLSL support";
            return false;
        }
        try {
            if (!GLContext.getCapabilities().OpenGL20) {
                status = "Requires OpenGL 2.0 shader support";
                return false;
            }
            return true;
        } catch (RuntimeException unavailable) {
            status = "OpenGL context unavailable";
            return false;
        }
    }

    private void blur(Framebuffer source, Framebuffer target, float x, float y) {
        target.bindFramebuffer(true);
        GL20.glUseProgram(blurProgram);
        bind(0, source.framebufferTexture);
        uniform(blurProgram, "uTexture", 0);
        GL20.glUniform2f(location(blurProgram, "uStep"), x, y);
        quad();
    }

    private static void quad() {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, -1);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }

    private static void bind(int unit, int texture) {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager.bindTexture(texture);
    }

    private void ensureTargets(int width, int height, boolean needDepth) {
        if (mask != null && (mask.framebufferWidth != width || mask.framebufferHeight != height)) releaseTargets();
        if (mask == null) {
            mask = target(width, height, true);
            horizontal = target(Math.max(1, width / 2), Math.max(1, height / 2), false);
            blurred = target(Math.max(1, width / 2), Math.max(1, height / 2), false);
        }
        if (needDepth) ensureDepthTexture(width, height);
    }

    /**
     * Depth textures are optional. A surprising number of older GL stacks can render FBOs
     * and GLSL programs but reject a sampled DEPTH_COMPONENT24 texture. Disable occlusion
     * once in that case and continue with the normal through-wall silhouette instead.
     */
    private boolean ensureDepthTexture(int width, int height) {
        if (sceneDepth != 0) return true;
        if (depthUnavailable) return false;
        int texture = 0;
        try {
            texture = GlStateManager.generateTexture();
            if (texture == 0) throw new IllegalStateException("could not allocate a depth texture");
            bind(3, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
            checkDepthError("allocating depth texture");
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            clampTexture();
            checkDepthError("configuring depth texture");
            sceneDepth = texture;
            return true;
        } catch (RuntimeException failure) {
            if (texture != 0) GlStateManager.deleteTexture(texture);
            disableDepth("Depth texture unavailable; using through-wall shader fallback", failure);
            return false;
        }
    }

    private boolean copyMainDepth(Framebuffer mainFramebuffer, int width, int height) {
        if (!ensureDepthTexture(width, height)) return false;
        try {
            bind(3, sceneDepth);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, mainFramebuffer.framebufferObject);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            checkDepthError("copying main framebuffer depth");
            return true;
        } catch (RuntimeException failure) {
            disableDepth("Depth copy unavailable; using through-wall shader fallback", failure);
            return false;
        }
    }

    private static void checkDepthError(String operation) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR)
            throw new IllegalStateException(operation + " failed (OpenGL 0x" + Integer.toHexString(error) + ')');
    }

    private void disableDepth(String message, RuntimeException failure) {
        if (!depthUnavailable) LogManager.getLogger("Backspace/ShaderFX").warn(message, failure);
        depthUnavailable = true;
        if (sceneDepth != 0) GlStateManager.deleteTexture(sceneDepth);
        sceneDepth = 0;
    }

    private static Framebuffer target(int width, int height, boolean depth) {
        Framebuffer result = new Framebuffer(width, height, depth);
        result.setFramebufferColor(0F, 0F, 0F, 0F);
        result.setFramebufferFilter(GL11.GL_LINEAR);
        bind(0, result.framebufferTexture); clampTexture();
        return result;
    }

    private static void clampTexture() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private boolean ensurePrograms() {
        if (maskProgram != 0 && blurProgram != 0 && compositeProgram != 0) return true;
        if (attempted) return false;
        attempted = true;
        int nextMask = 0, nextBlur = 0, nextComposite = 0;
        try {
            nextMask = program("mask.vert", "mask.frag");
            nextBlur = program("screen.vert", "blur.frag");
            nextComposite = program("screen.vert", "composite.frag");
            maskProgram = nextMask;
            blurProgram = nextBlur;
            compositeProgram = nextComposite;
            return true;
        } catch (RuntimeException failure) {
            if (nextMask != 0) GL20.glDeleteProgram(nextMask);
            if (nextBlur != 0) GL20.glDeleteProgram(nextBlur);
            if (nextComposite != 0) GL20.glDeleteProgram(nextComposite);
            status = "GLSL program unavailable: " + failure.getMessage();
            LogManager.getLogger("Backspace/ShaderFX").error(status, failure);
            return false;
        }
    }

    private int program(String vertexFile, String fragmentFile) {
        int vertex = 0, fragment = 0, result = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, source(vertexFile));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, source(fragmentFile));
            result = GL20.glCreateProgram();
            GL20.glAttachShader(result, vertex); GL20.glAttachShader(result, fragment); GL20.glLinkProgram(result);
            if (GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(result, 4096));
            return result;
        } catch (RuntimeException failure) {
            if (result != 0) GL20.glDeleteProgram(result);
            throw failure;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private String source(String file) {
        ResourceLocation resource = new ResourceLocation("backspace", "shaders/effects/" + file);
        try (InputStream input = mc.getResourceManager().getResource(resource).getInputStream();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096]; int count;
            while ((count = input.read(chunk)) != -1) bytes.write(chunk, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot read " + resource, failure); }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String message = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader); throw new IllegalStateException(message);
        }
        return shader;
    }

    private static int location(int program, String name) { return GL20.glGetUniformLocation(program, name); }
    private static void uniform(int program, String name, int value) { GL20.glUniform1i(location(program, name), value); }
    private void scalar(String name, float value) { GL20.glUniform1f(location(compositeProgram, name), value); }

    private void releaseTargets() {
        int previous = GL11.glGetInteger(0x8CA6);
        if ((mask != null && previous == mask.framebufferObject)
                || (horizontal != null && previous == horizontal.framebufferObject)
                || (blurred != null && previous == blurred.framebufferObject)) previous = mc.getFramebuffer().framebufferObject;
        if (mask != null) mask.deleteFramebuffer();
        if (horizontal != null) horizontal.deleteFramebuffer();
        if (blurred != null) blurred.deleteFramebuffer();
        if (sceneDepth != 0) GlStateManager.deleteTexture(sceneDepth);
        mask = horizontal = blurred = null;
        sceneDepth = 0;
        depthUnavailable = false;
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
    }

    private void releasePrograms() {
        if (maskProgram != 0) GL20.glDeleteProgram(maskProgram);
        if (blurProgram != 0) GL20.glDeleteProgram(blurProgram);
        if (compositeProgram != 0) GL20.glDeleteProgram(compositeProgram);
        maskProgram = blurProgram = compositeProgram = 0;
    }

    /** Deletes renderer resources without constructing a state capture first. */
    private void discardResources() {
        releaseTargets();
        releasePrograms();
        attempted = false;
        capturing = false;
        successfulCompositeLogged = false;
    }

    public void release() {
        if (mask != null || horizontal != null || blurred != null || sceneDepth != 0
                || maskProgram != 0 || blurProgram != 0 || compositeProgram != 0) {
            try (EffectRenderState cleanup = new EffectRenderState()) {
                discardResources();
            } catch (RuntimeException ignored) {
                // Releasing must not keep the module alive if a driver refuses state queries.
                discardResources();
            }
        } else {
            depthUnavailable = false;
            attempted = false;
            capturing = false;
            successfulCompositeLogged = false;
        }
    }
}
