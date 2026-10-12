package dev.goldforge.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Cached, antialiased rounded surfaces for legacy Forge GUIs.  A DynamicTexture
 * avoids the unreliable custom GLSL and POSITION_COLOR paths used by shader mods.
 */
final class UiSurface {
    private static final int OVERSAMPLE = 3;
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final Map<Key, ResourceLocation> SURFACES = new HashMap<Key, ResourceLocation>();
    private static int nextId;

    private UiSurface() {}

    static boolean draw(float x, float y, float width, float height, float radius, int argb) {
        if (width <= 0F || height <= 0F) return true;
        int drawWidth = Math.max(1, Math.round(width));
        int drawHeight = Math.max(1, Math.round(height));
        int drawRadius = Math.max(0, Math.min(Math.round(radius), Math.min(drawWidth, drawHeight) / 2));
        if (drawRadius <= 1) return false;
        try {
            Key key = new Key(drawWidth, drawHeight, drawRadius, argb);
            ResourceLocation texture = SURFACES.get(key);
            if (texture == null) {
                texture = create(key);
                SURFACES.put(key, texture);
            }
            MC.getTextureManager().bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(1F, 1F, 1F, 1F);
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(x, y + height, 0).tex(0, 1).endVertex();
            buffer.pos(x + width, y + height, 0).tex(1, 1).endVertex();
            buffer.pos(x + width, y, 0).tex(1, 0).endVertex();
            buffer.pos(x, y, 0).tex(0, 0).endVertex();
            Tessellator.getInstance().draw();
            GlStateManager.disableBlend();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
    }

    static void dispose() {
        for (ResourceLocation texture : SURFACES.values()) MC.getTextureManager().deleteTexture(texture);
        SURFACES.clear();
    }

    private static ResourceLocation create(Key key) {
        int pixelWidth = key.width * OVERSAMPLE;
        int pixelHeight = key.height * OVERSAMPLE;
        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setColor(new Color(key.argb, true));
        int diameter = key.radius * OVERSAMPLE * 2;
        graphics.fillRoundRect(0, 0, pixelWidth, pixelHeight, diameter, diameter);
        graphics.dispose();
        return MC.getTextureManager().getDynamicTextureLocation("backspace_surface_" + nextId++, new DynamicTexture(image));
    }

    private static final class Key {
        final int width, height, radius, argb;
        Key(int width, int height, int radius, int argb) {
            this.width = width; this.height = height; this.radius = radius; this.argb = argb;
        }
        @Override public boolean equals(Object object) {
            if (!(object instanceof Key)) return false;
            Key other = (Key) object;
            return width == other.width && height == other.height && radius == other.radius && argb == other.argb;
        }
        @Override public int hashCode() {
            int result = width; result = 31 * result + height; result = 31 * result + radius; return 31 * result + argb;
        }
    }
}
