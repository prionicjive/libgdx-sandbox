#version 330
#ifdef GL_ES
    precision highp float;
#endif

// Incoming ins to be aware of on the Fragment object
// NOTE: These have been interpolated over the verticies
in vec2 vTexCoord0;
in vec4 vColor;

out vec4 fragmentColor;

// Bound texture that contains the temporary texture to read from
// Provided by LibGDX
uniform sampler2D u_texture;

void main(void)
{
    // Look up the current coordinate in the scratchpad texture, and pass it back out to be stored in either the
    // velocity or position render target
    fragmentColor = texture(u_texture, vTexCoord0);
}
