package dev.goldforge.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

/** Small antialiased atlas for the ClickGUI, backed by the bundled Inter font. */
public final class UiFont {
    private static final long MAX_CUSTOM_FONT_BYTES = 16L * 1024L * 1024L;
    private static final int ATLAS_SIZE = 1024;
    private static final int CELL = 64;
    private static final int COLUMNS = ATLAS_SIZE / CELL;
    private static final float OVERSAMPLE = 2.5F;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Integer, Glyph> glyphs = new HashMap<Integer, Glyph>();
    private final float glyphHeight;
    private ResourceLocation texture;
    private final float scale;

    public UiFont() throws Exception { this(null); }

    public UiFont(File customFile) throws Exception { this(customFile,13.0F); }

    public UiFont(File customFile,float pointSize) throws Exception {
        Font font = null;
        if(customFile!=null&&customFile.isFile()&&customFile.length()<=MAX_CUSTOM_FONT_BYTES) {
            try(InputStream custom=new FileInputStream(customFile)) { font=Font.createFont(Font.TRUETYPE_FONT,custom); }
            catch(Exception ignored) { font=null; }
        }
        InputStream stream = UiFont.class.getResourceAsStream("/assets/goldforge/fonts/Inter-Variable.ttf");
        if (font==null&&stream != null) {
            try { font = Font.createFont(Font.TRUETYPE_FONT, stream); }
            catch (Exception ignored) { font = null; }
            finally { stream.close(); }
        } else if(stream!=null) {
            stream.close();
        }
        // Java 8 may reject variable fonts. Keep the UI legible with a native
        // sans-serif atlas rather than silently falling back to Minecraft's bitmap font.
        if (font == null) font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        scale=OVERSAMPLE;
        font = font.deriveFont(Math.max(7.0F,Math.min(16.0F,pointSize))*scale);
        BufferedImage image = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics metrics = g.getFontMetrics();
        glyphHeight=Math.min(CELL-2,metrics.getHeight())/scale;
        int[] codepoints = codepoints();
        for (int i = 0; i < codepoints.length; i++) {
            int cp = codepoints[i], cellX = (i % COLUMNS) * CELL, cellY = (i / COLUMNS) * CELL;
            String value = new String(Character.toChars(cp));
            int width = Math.min(CELL - 2, Math.max(1, metrics.stringWidth(value) + 3));
            g.drawString(value, cellX + 1, cellY + metrics.getAscent() + 2);
            glyphs.put(cp, new Glyph(cellX, cellY, width, metrics.stringWidth(value)/scale, metrics.charWidth(cp)/scale));
        }
        g.dispose();
        texture = mc.getTextureManager().getDynamicTextureLocation("goldforge_inter_ui", new DynamicTexture(image));
        mc.getTextureManager().bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
    }

    public void draw(String value, float x, float y, int argb) {
        if (value == null || value.isEmpty()) return;
        float a = ((argb >>> 24) & 255) / 255.0F, r = ((argb >>> 16) & 255) / 255.0F;
        float green = ((argb >>> 8) & 255) / 255.0F, b = (argb & 255) / 255.0F;
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, green, b, a);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        float start = x;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i); i += Character.charCount(cp);
            Glyph glyph = glyphs.get(cp);
            if (glyph == null) glyph = glyphs.get((int)'?');
            float u0 = glyph.x / (float)ATLAS_SIZE, v0 = (glyph.y+1) / (float)ATLAS_SIZE;
            float u1 = (glyph.x + glyph.width) / (float)ATLAS_SIZE, v1 = v0+(glyphHeight*scale)/(float)ATLAS_SIZE;
            float w = glyph.displayWidth;
            buffer.pos(start, y + glyphHeight, 0).tex(u0, v1).endVertex();
            buffer.pos(start + w, y + glyphHeight, 0).tex(u1, v1).endVertex();
            buffer.pos(start + w, y, 0).tex(u1, v0).endVertex();
            buffer.pos(start, y, 0).tex(u0, v0).endVertex();
            start += Math.max(2, glyph.advance);
        }
        Tessellator.getInstance().draw();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    public int width(String value) {
        return (int)Math.ceil(widthPrecise(value));
    }

    /** Fractional advance used by chat so color runs line up with the drawn glyphs. */
    public float widthPrecise(String value) {
        float width = 0;
        if (value != null) for (int i=0;i<value.length();) {
            int cp=value.codePointAt(i); i+=Character.charCount(cp);
            Glyph glyph=glyphs.get(cp);
            if(glyph==null) glyph=glyphs.get((int)'?');
            width+=Math.max(2F,glyph.advance);
        }
        return width;
    }

    public int lineHeight(){return Math.max(1,Math.round(glyphHeight));}

    public String trimToWidth(String value, int maxWidth) {
        if (value == null || width(value) <= maxWidth) return value;
        // Keep the truncation marker inside the atlas's guaranteed ASCII range.
        String suffix="...";
        int end=value.length();
        while(end>0 && width(value.substring(0,end)+suffix)>maxWidth) {
            end=value.offsetByCodePoints(end,-1);
        }
        return value.substring(0,end)+suffix;
    }

    private int[] codepoints() {
        int[] values = new int[95 + 96 + 3]; int at = 0;
        for (int cp = 32; cp <= 126; cp++) values[at++] = cp;
        for (int cp = 160; cp <= 255; cp++) values[at++] = cp;
        values[at++] = 183; values[at++] = 8211; values[at] = 8230;
        return values;
    }

    public void dispose() {
        if(texture!=null){mc.getTextureManager().deleteTexture(texture);texture=null;}
    }

    private static final class Glyph {
        final int x,y,width;
        final float displayWidth,advance;
        Glyph(int x,int y,int width,float displayWidth,float advance){this.x=x;this.y=y;this.width=width;this.displayWidth=displayWidth;this.advance=advance;}
    }
}
