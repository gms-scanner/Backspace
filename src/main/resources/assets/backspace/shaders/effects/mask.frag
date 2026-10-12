#version 120
uniform sampler2D uTexture;
uniform sampler2D uDepth;
uniform vec2 uResolution;
uniform int uTextured;
uniform int uOccluded;
varying vec2 uv;
void main() {
    float coverage = uTextured != 0 ? texture2D(uTexture, uv).a : 1.0;
    if (coverage < 0.1) discard;
    if (uOccluded != 0 && gl_FragCoord.z > texture2D(uDepth, gl_FragCoord.xy / uResolution).r + 0.000002) discard;
    gl_FragColor = vec4(coverage);
}
