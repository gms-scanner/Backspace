#version 120
uniform sampler2D uTexture;
uniform vec2 uStep;
varying vec2 uv;
void main() {
    float a = texture2D(uTexture, uv).a * 0.227027;
    a += (texture2D(uTexture, uv + uStep * 1.384615).a + texture2D(uTexture, uv - uStep * 1.384615).a) * 0.316216;
    a += (texture2D(uTexture, uv + uStep * 3.230769).a + texture2D(uTexture, uv - uStep * 3.230769).a) * 0.070270;
    gl_FragColor = vec4(a);
}
