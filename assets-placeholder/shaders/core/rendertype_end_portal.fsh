#version 330
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
};
out vec4 fragColor;
void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 sky = texture(Sampler0, uv * 2.0).rgb;
    vec3 portal = texture(Sampler1, uv * 3.0 + vec2(GameTime * 0.01)).rgb;
    float vignette = smoothstep(0.85, 0.25, length(uv * 2.0 - 1.0));
    fragColor = vec4((sky * 0.45 + portal * 0.65) * vignette, 1.0);
}
