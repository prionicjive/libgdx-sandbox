#include "preamble.frag"

// Bound texture that contains random values, provided by LibGDX SpriteBatch
uniform sampler2D u_texture;

uniform float spawnWidth;
uniform float spawnHeight;

void main(void)
{
    vec4 rand = texture(u_texture, vTexCoord0);

    fragmentColor = vec4(10.0 + rand.x * spawnWidth, 10.0 + rand.y * spawnHeight, 0.0, 1.0);
}
