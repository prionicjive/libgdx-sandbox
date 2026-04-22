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

// Bound texture that position map
// Provided by LibGDX
uniform sampler2D positionMap;

// Provided point size
uniform float pointSize;

// Outgoing ins that will be leverage during the next phase (fragment shader)
out vec2 vTexCoord0;
out vec4 vColor;

void main()
{
    // Set the point size
    gl_PointSize = pointSize;

    // Pass along coords
    vTexCoord0 = a_texCoord0;

    // Initially set color
    vColor = a_color;

    // Look up the world position based on the color data in the position texture.
    //
    // We access the texture a bit differently than other times. We use the position of the incoming vertex to look into the texture.
    //
    // Luckily, we have set this up in our game code so that it is in the particleCount * particleCount dimensions during initialization.
    //
    // The last component is the mipmap level, which should be 0
    vec4 worldPosition = texture(positionMap, a_position.xy);
    worldPosition.w = 1.0;

    // Move worldPosition to screen space.
    gl_Position = u_projTrans * worldPosition;
}
