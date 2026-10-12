package dev.goldforge.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * A transparent GLSL 1.20 glow for rounded client surfaces. Unlike a backdrop
 * shader, this fragment program emits only the frame's edge and exterior halo.
 */
final class FrameGlowShader {
    private static final String VERTEX = "#version 120\n"
            + "void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;gl_TexCoord[0]=gl_MultiTexCoord0;}";
    private static final String FRAGMENT = "#version 120\n"
            + "uniform vec2 uResolution;uniform vec4 uFrame;uniform float uRadius;uniform vec3 uColor;uniform float uTime;"
            + "float roundedBox(vec2 p,vec2 b,float r){vec2 q=abs(p)-b+vec2(r);return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}"
            + "void main(){vec2 p=gl_TexCoord[0].st*uResolution;vec2 c=(uFrame.xy+uFrame.zw*.5)*uResolution;"
            + "vec2 h=uFrame.zw*uResolution*.5;float d=roundedBox(p-c,h,uRadius);float pulse=.84+.16*sin(uTime*1.7);"
            + "float halo=exp(-max(d,0.0)/22.0)*.34*pulse;float edge=exp(-abs(d)/1.45)*.70;"
            + "float a=max(halo,edge);gl_FragColor=vec4(uColor,a);}";
    private static int program;
    private static int resolutionUniform = -1, frameUniform = -1, radiusUniform = -1, colorUniform = -1, timeUniform = -1;
    private static boolean attempted;

    private FrameGlowShader() {}

    static void draw(float screenX, float screenY, float screenW, float screenH, float frameX, float frameY,
                     float frameW, float frameH, float radius, int argb, float time) {
        if (screenW <= 0F || screenH <= 0F || !ensureProgram()) return;
        Minecraft mc = Minecraft.getMinecraft();
        float scale = mc.displayWidth / Math.max(1F, screenW);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glUseProgram(program);
            GL20.glUniform2f(resolutionUniform, mc.displayWidth, mc.displayHeight);
            GL20.glUniform4f(frameUniform, frameX / screenW, frameY / screenH, frameW / screenW, frameH / screenH);
            GL20.glUniform1f(radiusUniform, radius * scale);
            GL20.glUniform3f(colorUniform, ((argb >>> 16) & 255) / 255F, ((argb >>> 8) & 255) / 255F, (argb & 255) / 255F);
            GL20.glUniform1f(timeUniform, time);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0F, 0F); GL11.glVertex2f(screenX, screenY + screenH);
            GL11.glTexCoord2f(1F, 0F); GL11.glVertex2f(screenX + screenW, screenY + screenH);
            GL11.glTexCoord2f(1F, 1F); GL11.glVertex2f(screenX + screenW, screenY);
            GL11.glTexCoord2f(0F, 1F); GL11.glVertex2f(screenX, screenY);
            GL11.glEnd();
        } finally {
            GL20.glUseProgram(previousProgram);
            GL11.glPopAttrib();
        }
    }

    private static boolean ensureProgram() {
        if (program != 0) return true;
        if (attempted || !org.lwjgl.opengl.GLContext.getCapabilities().OpenGL20) return false;
        attempted = true;
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (vertex == 0 || fragment == 0) { if (vertex != 0) GL20.glDeleteShader(vertex); if (fragment != 0) GL20.glDeleteShader(fragment); return false; }
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment); GL20.glLinkProgram(linked);
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) { GL20.glDeleteProgram(linked); return false; }
        program = linked;
        resolutionUniform = GL20.glGetUniformLocation(program, "uResolution"); frameUniform = GL20.glGetUniformLocation(program, "uFrame");
        radiusUniform = GL20.glGetUniformLocation(program, "uRadius"); colorUniform = GL20.glGetUniformLocation(program, "uColor");
        timeUniform = GL20.glGetUniformLocation(program, "uTime");
        return resolutionUniform >= 0 && frameUniform >= 0 && radiusUniform >= 0 && colorUniform >= 0 && timeUniform >= 0;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) { GL20.glDeleteShader(shader); return 0; }
        return shader;
    }
}
