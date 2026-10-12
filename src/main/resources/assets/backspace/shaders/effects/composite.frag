#version 120
uniform sampler2D uMask;
uniform sampler2D uBloom;
uniform sampler2D uOverlay;
uniform vec2 uResolution;
uniform vec3 uColor;
uniform float uTime;
uniform float uIntensity;
uniform float uFill;
uniform float uOutline;
uniform int uStyle;
uniform int uHasOverlay;
varying vec2 uv;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}
float nebula(vec2 p) {
    float n = 0.0, weight = 0.55;
    for (int i = 0; i < 4; i++) {
        n += noise(p) * weight;
        p = mat2(1.6, 1.2, -1.2, 1.6) * p + 1.7;
        weight *= 0.5;
    }
    return n;
}
vec3 effectColor(vec2 p) {
    if (uStyle == 3) {
        return 0.55 + 0.45 * cos(6.283185 * (p.x * 0.35 + p.y * 0.2 - uTime * 0.12 + vec3(0.0, 0.3333, 0.6667)));
    }
    if (uStyle == 4) {
        float cloud = nebula(p * 3.5 + vec2(uTime * 0.08, -uTime * 0.035));
        vec3 color = mix(vec3(0.12, 0.08, 0.40), vec3(0.65, 0.20, 0.95), smoothstep(0.2, 0.8, cloud));
        color = mix(color, vec3(0.20, 0.70, 1.0), smoothstep(0.57, 0.87, cloud));
        vec2 grid = p * 100.0;
        vec2 cell = floor(grid);
        float star = 1.0 - smoothstep(0.0, 0.08, length(fract(grid) - 0.5));
        star *= step(0.985, hash(cell)) * (0.65 + 0.35 * sin(uTime * 1.5 + hash(cell) * 20.0));
        return color + star * vec3(1.0, 0.92, 1.0);
    }
    if (uStyle == 5) {
        float wave = 0.5 + 0.5 * sin(p.y * 7.0 + sin(p.x * 3.0 + uTime * 0.4) * 2.0 - uTime);
        return mix(vec3(0.06, 0.95, 0.65), vec3(0.45, 0.30, 1.0), wave);
    }
    return uColor * (0.88 + 0.12 * sin(uTime * 1.5 + p.y * 3.0));
}
void main() {
    float mask = texture2D(uMask, uv).a;
    vec2 px = vec2(uOutline) / uResolution;
    float lo = mask, hi = mask;
    for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
        float sampleMask = texture2D(uMask, uv + vec2(float(x), float(y)) * px).a;
        lo = min(lo, sampleMask); hi = max(hi, sampleMask);
    }
    float edge = max(mask - lo, hi - mask);
    float halo = texture2D(uBloom, uv).a * (1.0 - mask);
    float alpha = clamp((edge * 0.85 + halo * 2.2 + mask * uFill) * uIntensity, 0.0, 0.94);
    if (alpha < 0.002) discard;
    vec2 p = uv * vec2(uResolution.x / uResolution.y, 1.0);
    vec3 color = effectColor(p);
    if (uHasOverlay != 0) {
        vec4 overlay = texture2D(uOverlay, uv);
        color = mix(color, overlay.rgb, overlay.a * 0.75 * mask);
    }
    color += vec3(edge * 0.22);
    gl_FragColor = vec4(color, alpha);
}
