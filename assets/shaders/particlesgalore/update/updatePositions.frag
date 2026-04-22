#version 330

in vec2 vTexCoord0;

out vec4 fragmentColor;

uniform sampler2D positionMap;
uniform sampler2D velocityMap;

uniform float deltaTime;

void main(void)
{
    vec2 currVelocity = texture(velocityMap, vTexCoord0).xy;
    vec2 currPosition = texture(positionMap, vTexCoord0).xy;

    vec2 newPosition = currPosition + (currVelocity * deltaTime);

    fragmentColor = vec4(newPosition, 0.0, 1.0);
}
