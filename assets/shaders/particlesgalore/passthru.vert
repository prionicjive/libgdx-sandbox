#version 330
#ifdef GL_ES
    precision highp float;
#endif

// Incoming ins to be aware of on the Vertex object
in vec4 a_position;
in vec4 a_color;
in vec2 a_texCoord0;

// The combined projection view matrix
// Set by LibGDX's SpriteBatch
uniform mat4 u_projTrans;

// Outgoing ins that will be leverage during the next phase (fragment shader)
out vec2 vTexCoord0;
out vec4 vColor;

void main()
{
    vColor = a_color;
    vTexCoord0 = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
