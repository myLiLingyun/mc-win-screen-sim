#version 330
layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
};
in vec3 Position;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
