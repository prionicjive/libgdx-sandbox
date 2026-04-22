#version 330

in vec2 vTexCoord0;

out vec4 fragmentColor;

// Bound texture that contains random values, provided by LibGDX SpriteBatch
uniform sampler2D u_texture;

void main(void)
{
    vec4 rand = texture(u_texture, vTexCoord0);
    rand = vec4(-1.0) + (2.0 * rand);

    fragmentColor = vec4(rand.xy * 100.0, 0.0, 1.0);
}
