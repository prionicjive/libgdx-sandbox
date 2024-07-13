#version 330
#ifdef GL_ES
    precision highp float;
#endif

// Incoming ins to be aware of on the Fragment object
// NOTE: These have been interpolated over the verticies
in vec2 vTexCoord0;
in vec4 vColor;

out vec4 fragmentColor;

// Bound texture that contains random values
// Provided by LibGDX
uniform sampler2D u_texture;

// Stores info about the positions and velocities
uniform sampler2D positionMap;
uniform sampler2D velocityMap;

uniform float spawnWidth;
uniform float spawnHeight;

void main(void)
{
    // Get the random value from the random texture (It will be between 0 and 1 for each component)
    vec4 rand =  texture2D(u_texture, vTexCoord0);

    // Return the position, which will be a random position somewhere in the screen
    fragmentColor = vec4(10.0 + rand.x * spawnWidth, 10.0 + rand.y * spawnHeight, 0.0, 1.0);
}
