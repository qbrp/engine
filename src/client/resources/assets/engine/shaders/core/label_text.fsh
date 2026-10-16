#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 glyph = texture(Sampler0, texCoord0);
    if (glyph.a < 0.1) {
        discard;
    }
    fragColor = glyph * vertexColor * ColorModulator;
}
