#ifndef BILLOW_GAS_NOISE_GLSL
#define BILLOW_GAS_NOISE_GLSL

uvec3 billowPcg3(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return v;
}

float billowHash3(vec3 p) {
    uvec3 h = billowPcg3(uvec3(ivec3(floor(p + 0.5))));
    return float(h.x >> 8u) * (1.0 / 16777216.0);
}

float billowNoise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(billowHash3(i), billowHash3(i + vec3(1.0, 0.0, 0.0)), f.x),
            mix(billowHash3(i + vec3(0.0, 1.0, 0.0)), billowHash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(billowHash3(i + vec3(0.0, 0.0, 1.0)), billowHash3(i + vec3(1.0, 0.0, 1.0)), f.x),
            mix(billowHash3(i + vec3(0.0, 1.0, 1.0)), billowHash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

float billowFbm3(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float total = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amp * billowNoise3(p);
        total += amp;
        p = p * 2.03 + vec3(1.7, 9.2, 3.4);
        amp *= 0.5;
    }
    return sum / total;
}

float billowDither(vec2 fragCoord) {
    return (billowHash3(vec3(fragCoord, 7.0)) - 0.5) / 255.0;
}

#endif
