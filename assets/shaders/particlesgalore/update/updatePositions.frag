#version 330
#ifdef GL_ES
    precision highp float;
#endif

// Incoming ins to be aware of on the Fragment object
// NOTE: These have been interpolated over the verticies
in vec2 vTexCoord0;
in vec4 vColor;

out vec4 fragmentColor;

// Stores info about the positions and velocities
uniform sampler2D positionMap;
uniform sampler2D velocityMap;

// Dimensions of boundaries
uniform float spawnWidth;
uniform float spawnHeight;

// Force and attractor variables
uniform float deltaTime;

void main(void)
{
    vec2 currVelocity = texture(velocityMap, vTexCoord0).xy;
    vec2 currPosition = texture(positionMap, vTexCoord0).xy;

    // Calculate the new position by following this formula...
    vec2 newPosition = currPosition + (currVelocity * deltaTime);

    fragmentColor = vec4(newPosition, 0.0, 1.0);
}
