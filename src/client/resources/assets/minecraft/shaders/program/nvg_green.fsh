#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float noise = hash(vec3(texCoord, sin(Time * hash(vec3(texCoord, Time)))));
    float brightness = 1.2 - noise;
    fragColor = vec4(0.0, color.g * brightness, 0.0, 1.0);
}