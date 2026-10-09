#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <billow:gas_noise.glsl>

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 corner;
layout(location = 3) in vec3 cloudPos;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    return color;
}

void main() {
    float r2 = dot(corner, corner);
    if (r2 >= 1.0) {
        discard;
    }
    float time = TextureMat[3].w;

    vec3 p = cloudPos * vec3(1.0 / 30.0, 1.0 / 20.0, 1.0 / 30.0);
    float warp = billowFbm3(p * 0.45 + vec3(0.0, time * 0.004, 0.0), 2);
    float gas = billowFbm3(p + vec3(warp * 2.6, warp * 1.2, time * 0.006), 4);
    float wisps = billowFbm3(p * 2.6 + vec3(warp * 1.5), 2);

    float soft = 1.0 - r2;
    soft *= soft;
    float body = smoothstep(0.24, 0.62, gas) * (0.8 + 0.45 * wisps);
    float alpha = vertexColor.a * soft * body;

    float shade = mix(1.05, 0.88, smoothstep(0.4, 0.8, gas));
    vec4 color = vec4(min(vertexColor.rgb * shade, vec3(1.0)), alpha);

    #ifndef OIT_DEPTH_BOUNDS
    color.a *= 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    #endif
    if (color.a < 0.004) {
        discard;
    }
    color.rgb += vec3(billowDither(gl_FragCoord.xy));

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
