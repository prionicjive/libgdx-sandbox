#include "preamble.frag"

// Scratchpad texture to blit into the canonical state texture
// Provided by LibGDX SpriteBatch
uniform sampler2D u_texture;

void main(void)
{
    fragmentColor = texture(u_texture, vTexCoord0);
}
