package dev.goldforge.gui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Antialiased signed-distance rounded surfaces for the 1.12 fixed-function GUI. */
final class SmoothRectRenderer {
    private static final String VERTEX = "#version 120\n"
            + "void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;gl_TexCoord[0]=gl_MultiTexCoord0;}";
    private static final String FRAGMENT = "#version 120\n"
            + "uniform vec4 uColor;uniform vec2 uSize;uniform float uRadius;\n"
            + "void main(){vec2 p=(gl_TexCoord[0].st-.5)*uSize;vec2 b=.5*uSize-vec2(uRadius);"
            + "float d=length(max(abs(p)-b,0.0))-uRadius;float edge=1.0-smoothstep(-.65,.65,d);"
            + "gl_FragColor=vec4(uColor.rgb,uColor.a*edge);}";
    private static int program, colorUniform, sizeUniform, radiusUniform;
    private static int backdropProgram, backdropResolutionUniform, backdropAccentUniform;
    private static boolean attempted;

    private SmoothRectRenderer() {}

    static boolean draw(float x, float y, float width, float height, float radius, int color) {
        if (width <= 0F || height <= 0F || !ensureProgram()) return false;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glUseProgram(program);
            float safeRadius = Math.max(0F, Math.min(radius, Math.min(width, height) * .5F));
            GL20.glUniform4f(colorUniform, ((color >>> 16) & 255) / 255F, ((color >>> 8) & 255) / 255F,
                    (color & 255) / 255F, ((color >>> 24) & 255) / 255F);
            GL20.glUniform2f(sizeUniform, width, height);
            GL20.glUniform1f(radiusUniform, safeRadius);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0F, 0F); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(1F, 0F); GL11.glVertex2f(x + width, y);
            GL11.glTexCoord2f(1F, 1F); GL11.glVertex2f(x + width, y + height);
            GL11.glTexCoord2f(0F, 1F); GL11.glVertex2f(x, y + height);
            GL11.glEnd();
            return true;
        } finally {
            GL20.glUseProgram(oldProgram);
            GL11.glPopAttrib();
        }
    }

    /**
     * Full-screen, opaque backdrop. Keeping it in the same shader family as the
     * cards gives the library a clean canvas even over bright worlds and avoids
     * the hard translucent rectangle that made the old GUI look like an overlay.
     */
    static boolean drawBackdrop(float width, float height, int accent) {
        if (width <= 0F || height <= 0F || !ensureBackdropProgram()) return false;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL20.glUseProgram(backdropProgram);
            GL20.glUniform2f(backdropResolutionUniform, width, height);
            GL20.glUniform3f(backdropAccentUniform, ((accent >>> 16) & 255) / 255F,
                    ((accent >>> 8) & 255) / 255F, (accent & 255) / 255F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0F, 0F); GL11.glVertex2f(0F, 0F);
            GL11.glTexCoord2f(1F, 0F); GL11.glVertex2f(width, 0F);
            GL11.glTexCoord2f(1F, 1F); GL11.glVertex2f(width, height);
            GL11.glTexCoord2f(0F, 1F); GL11.glVertex2f(0F, height);
            GL11.glEnd();
            return true;
        } finally {
            GL20.glUseProgram(oldProgram);
            GL11.glPopAttrib();
        }
    }

    private static boolean ensureProgram() {
        if (program != 0) return true;
        if (attempted || !org.lwjgl.opengl.GLContext.getCapabilities().OpenGL20) return false;
        attempted = true;
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (vertex == 0 || fragment == 0) return false;
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment); GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) { GL20.glDeleteProgram(program); program = 0; return false; }
        colorUniform = GL20.glGetUniformLocation(program, "uColor");
        sizeUniform = GL20.glGetUniformLocation(program, "uSize");
        radiusUniform = GL20.glGetUniformLocation(program, "uRadius");
        return colorUniform >= 0 && sizeUniform >= 0 && radiusUniform >= 0;
    }

    private static boolean ensureBackdropProgram() {
        if (backdropProgram != 0) return true;
        if (!org.lwjgl.opengl.GLContext.getCapabilities().OpenGL20) return false;
        String fragment = "#version 120\n"
                + "uniform vec2 uResolution;uniform vec3 uAccent;\n"
                + "float halo(vec2 p,vec2 c,float s){vec2 d=(p-c)/s;return exp(-dot(d,d)*3.1);}\n"
                + "void main(){vec2 uv=gl_TexCoord[0].st;vec2 q=(uv-.5)*vec2(uResolution.x/max(uResolution.y,1.0),1.0)+.5;vec3 base=vec3(.006,.007,.010);"
                + "float top=halo(q,vec2(.10,.04),.32);float right=halo(q,vec2(.97,.18),.42);"
                + "float low=halo(q,vec2(.72,.92),.58);float haze=halo(q,vec2(.42,.42),.78);"
                + "vec3 c=base+uAccent*(top*.105+right*.055+low*.025)+vec3(.012,.006,.025)*(haze*.24);"
                + "float vignette=1.0-.42*dot(uv-.5,uv-.5);gl_FragColor=vec4(c*vignette,1.0);}";
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, fragment);
        if (vertex == 0 || fragmentShader == 0) return false;
        backdropProgram = GL20.glCreateProgram();
        GL20.glAttachShader(backdropProgram, vertex); GL20.glAttachShader(backdropProgram, fragmentShader); GL20.glLinkProgram(backdropProgram);
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragmentShader);
        if (GL20.glGetProgrami(backdropProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GL20.glDeleteProgram(backdropProgram); backdropProgram = 0; return false;
        }
        backdropResolutionUniform = GL20.glGetUniformLocation(backdropProgram, "uResolution");
        backdropAccentUniform = GL20.glGetUniformLocation(backdropProgram, "uAccent");
        return backdropResolutionUniform >= 0 && backdropAccentUniform >= 0;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) { GL20.glDeleteShader(shader); return 0; }
        return shader;
    }
}
