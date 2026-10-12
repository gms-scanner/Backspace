package dev.goldforge.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Isolated GLSL 1.20 canvas used only by the title screen. */
final class MenuBackdropShader {
    private static final String VERTEX = "#version 120\n"
            + "void main(){ gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; gl_TexCoord[0]=gl_MultiTexCoord0; }";
    private static final String FRAGMENT = "#version 120\n"
            + "uniform vec2 uResolution; uniform float uTime;\n"
            + "float curve(vec2 p,float y,float wave,float phase,float sharp){"
            + " return exp(-abs(p.y-y-wave*sin(p.x*3.2+phase))*sharp); }\n"
            + "void main(){ vec2 uv=gl_TexCoord[0].st; float t=uTime;"
            + " vec2 aspect=vec2(uResolution.x/max(1.0,uResolution.y),1.0);"
            + " vec2 glowCenter=vec2(0.69+0.014*sin(t*0.12),0.51+0.021*cos(t*0.10));"
            + " float haze=exp(-length((uv-glowCenter)*aspect*vec2(0.88,0.68))*2.35);"
            + " float lavender=curve(uv,0.59,0.115,t*0.13,29.0);"
            + " float violet=curve(uv,0.71,0.14,-t*0.10+1.9,36.0);"
            + " float ring=exp(-abs(length((uv-glowCenter)*aspect*vec2(0.92,1.0))-0.30)*30.0);"
            + " float innerRing=exp(-abs(length((uv-glowCenter)*aspect*vec2(0.92,1.0))-0.30)*82.0);"
            + " float rightMask=smoothstep(0.27,0.57,uv.x);"
            + " vec3 col=vec3(0.0035,0.0040,0.0070);"
            + " col+=haze*vec3(0.080,0.046,0.160);"
            + " col+=lavender*vec3(0.24,0.18,0.40);"
            + " col+=violet*vec3(0.18,0.055,0.29);"
            + " col+=rightMask*ring*vec3(0.15,0.10,0.28);"
            + " col+=rightMask*innerRing*vec3(0.46,0.42,0.70);"
            + " float darken=1.0-smoothstep(0.20,0.67,uv.x);"
            + " col=mix(col,vec3(0.0015,0.0020,0.0035),darken*0.94);"
            + " float vignette=1.0-smoothstep(0.38,1.12,length((uv-0.5)*vec2(1.0,0.76)));"
            + " col*=0.57+0.43*vignette; gl_FragColor=vec4(col,1.0); }";
    private static int program;
    private static int timeUniform = -1;
    private static int resolutionUniform = -1;
    private static boolean attempted;

    private MenuBackdropShader() {}

    static void draw(int x, int y, int width, int height, float elapsedSeconds) {
        if (width <= 0 || height <= 0) return;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (ensureProgram()) {
                Minecraft mc = Minecraft.getMinecraft();
                GL20.glUseProgram(program);
                GL20.glUniform1f(timeUniform, elapsedSeconds);
                GL20.glUniform2f(resolutionUniform, Math.max(1, mc.displayWidth * width / Math.max(1, mc.currentScreen.width)),
                        Math.max(1, mc.displayHeight * height / Math.max(1, mc.currentScreen.height)));
                GL11.glColor4f(1F, 1F, 1F, 1F);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0F, 0F); GL11.glVertex2f(x, y + height);
                GL11.glTexCoord2f(1F, 0F); GL11.glVertex2f(x + width, y + height);
                GL11.glTexCoord2f(1F, 1F); GL11.glVertex2f(x + width, y);
                GL11.glTexCoord2f(0F, 1F); GL11.glVertex2f(x, y);
                GL11.glEnd();
            } else {
                GL20.glUseProgram(0);
                fallback(x, y, width, height, elapsedSeconds);
            }
        } finally {
            GL20.glUseProgram(oldProgram);
            GL11.glPopAttrib();
        }
    }

    private static void fallback(int x, int y, int width, int height, float time) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.018F, 0.023F, 0.038F, 1F); GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y + height);
        GL11.glColor4f(0.07F, 0.025F, 0.105F, 1F); GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        float cx = x + width * (0.54F + 0.08F * (float)Math.sin(time * 0.22));
        float cy = y + height * (0.50F + 0.07F * (float)Math.cos(time * 0.19));
        for (int i = 5; i >= 1; i--) {
            float radius = Math.max(width, height) * (0.07F + i * 0.035F);
            float alpha = 0.018F + (6 - i) * 0.012F;
            GL11.glColor4f(0.45F, 0.20F, 0.72F, alpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(cx, cy);
            for (int n = 0; n <= 48; n++) {
                double angle = n * Math.PI * 2.0 / 48.0;
                GL11.glVertex2f(cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius * 0.52F);
            }
            GL11.glEnd();
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static boolean ensureProgram() {
        if (program != 0) return true;
        if (attempted) return false;
        attempted = true;
        if (!org.lwjgl.opengl.GLContext.getCapabilities().OpenGL20) return false;
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (vertex == 0 || fragment == 0) {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
            return false;
        }
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex);
        GL20.glAttachShader(linked, fragment);
        GL20.glLinkProgram(linked);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GL20.glDeleteProgram(linked);
            return false;
        }
        program = linked;
        timeUniform = GL20.glGetUniformLocation(program, "uTime");
        resolutionUniform = GL20.glGetUniformLocation(program, "uResolution");
        return timeUniform >= 0 && resolutionUniform >= 0;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
