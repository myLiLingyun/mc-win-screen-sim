#version 330
layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
};
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 texCoord;
out vec4 vertexColor;
void main() {
    texCoord = UV0;
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
