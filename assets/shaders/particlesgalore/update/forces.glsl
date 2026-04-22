// Point-force helpers used by the update-velocities pass.
// Depends on `spawnWidth` being declared by the including shader.

float getScaledDistance(float dist, float maxDistance)
{
    if (maxDistance <= 0.0)
    {
        return (5.0 * dist) / (spawnWidth * 0.5);
    }
    if (dist < maxDistance)
    {
        return (5.0 * dist) / maxDistance;
    }
    return 0.0;
}

// A signed-strength point force. Positive `strength` attracts, negative repels.
vec2 localAttractor(vec2 position, vec2 forcePosition, float strength, float maxDistance)
{
    vec2 toCenter = forcePosition - position;
    float dist = length(toCenter);
    if (dist <= 0.0)
    {
        return vec2(0.0);
    }

    float scaledDistance = getScaledDistance(dist, maxDistance);
    if (scaledDistance <= 0.0)
    {
        return vec2(0.0);
    }

    float s = strength / ((scaledDistance * scaledDistance) + 10.0);
    return s * (toCenter / dist);
}
