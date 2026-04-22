#include "preamble.frag"

uniform sampler2D positionMap;
uniform sampler2D velocityMap;

uniform float spawnWidth;
uniform float spawnHeight;

uniform vec3 attractorPos;
uniform float attractorForce;
uniform float attractorMaxDistance;

uniform float dragPercentage;
uniform float deltaTime;

// Parameterized colliders / point-forces. Sized at compile-time; actual count comes from the *_Count
// uniforms. See ParticleSystem.java for the configuration API.
const int MAX_CIRCLE_COLLIDERS = 8;
const int MAX_RECT_COLLIDERS = 4;
const int MAX_POINT_FORCES = 8;

uniform int circleColliderCount;
uniform vec4 circleColliders[MAX_CIRCLE_COLLIDERS];  // xy = center, z = radius, w unused

uniform int rectColliderCount;
uniform vec4 rectColliders[MAX_RECT_COLLIDERS];      // xy = center, zw = half-extents

uniform int pointForceCount;
uniform vec4 pointForces[MAX_POINT_FORCES];          // xy = position, z = signed strength, w = maxDistance

// Per-collider surface response is fixed for now; promote to per-collider uniforms if needed later.
const float COLLIDER_RESILIENCE = 0.8;
const float COLLIDER_FRICTION = 0.7;
const float COLLIDER_MIN_VELOCITY = 0.01;

#include "collisions.glsl"
#include "forces.glsl"

void main(void)
{
    vec2 currVelocity = texture(velocityMap, vTexCoord0).xy;
    vec2 currPosition = texture(positionMap, vTexCoord0).xy;

    vec2 acceleration = vec2(0.0);
    for (int i = 0; i < pointForceCount; i++)
    {
        vec4 pf = pointForces[i];
        acceleration += localAttractor(currPosition, pf.xy, pf.z, pf.w);
    }
    acceleration += localAttractor(currPosition, attractorPos.xy, attractorForce, attractorMaxDistance);

    vec2 newVelocity = currVelocity + (acceleration * deltaTime);
    newVelocity -= newVelocity * (dragPercentage * deltaTime);

    for (int i = 0; i < circleColliderCount; i++)
    {
        vec4 c = circleColliders[i];
        newVelocity = circleConstraint(newVelocity, currPosition, c.xy, c.z,
            COLLIDER_RESILIENCE, COLLIDER_FRICTION, COLLIDER_MIN_VELOCITY);
    }

    for (int i = 0; i < rectColliderCount; i++)
    {
        vec4 r = rectColliders[i];
        newVelocity = rectConstraint(newVelocity, currPosition, r.xy, r.z, r.w,
            COLLIDER_RESILIENCE, COLLIDER_FRICTION, COLLIDER_MIN_VELOCITY);
    }

    // Wall-bounce test on the post-force, post-collider velocity so accumulated changes are respected.
    vec2 newPosition = currPosition + (newVelocity * deltaTime);

    if (newPosition.x < 0.0 || newPosition.x > spawnWidth)
    {
        newVelocity.x = -newVelocity.x;
        newVelocity -= newVelocity * dragPercentage;
    }

    if (newPosition.y < 0.0 || newPosition.y > spawnHeight)
    {
        newVelocity.y = -newVelocity.y;
        newVelocity -= newVelocity * dragPercentage;
    }

    fragmentColor = vec4(newVelocity, 0.0, 1.0);
}
