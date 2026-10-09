#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec2 corner;
layout(location = 3) out vec3 cloudPos;

void main() {
    vec3 right = vec3(ModelViewMat[0][0], ModelViewMat[1][0], ModelViewMat[2][0]);
    vec3 up = vec3(ModelViewMat[0][1], ModelViewMat[1][1], ModelViewMat[2][1]);
    vec3 pos = Position + ModelOffset + right * UV0.x + up * UV0.y;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    corner = sign(UV0);
    cloudPos = pos + TextureMat[3].xyz;
    vertexDistance = fog_spherical_distance(pos);
    vertexColor = Color * ColorModulator;
}
