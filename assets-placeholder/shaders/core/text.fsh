#version 330
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 texel = texture(Sampler0, texCoord);
    float alpha = texel.a * vertexColor.a;
    if (alpha < 0.1) {
        discard;
    }
    fragColor = vec4(texel.rgb * vertexColor.rgb, alpha);
}
