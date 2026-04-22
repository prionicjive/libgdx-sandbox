// Collision helpers used by the update-velocities pass.
// Depends on `deltaTime` being declared by the including shader.

vec2 bounceReflection(vec2 velocity, vec2 normal, bool alreadyInside,
    float resilience, float friction, float minimalVelocity)
{
    float normalContribution = dot(velocity, normal);
    vec2 vNormal = normalContribution * normal;
    vec2 vTangent = velocity - vNormal;

    if (alreadyInside)
    {
        // Push the particle back out as quickly as possible.
        if (normalContribution < 0.0)
        {
            velocity = vTangent - vNormal;
        }
    }
    else
    {
        if (length(velocity) < minimalVelocity)
        {
            friction = 1.0;
        }

        velocity = vTangent * friction - vNormal * resilience;
    }

    return velocity;
}

bool isPointInRect(vec2 point, vec2 bottomLeft, vec2 topRight)
{
    return (bottomLeft.x <= point.x && topRight.x >= point.x
         && bottomLeft.y <= point.y && topRight.y >= point.y);
}

bool segmentsIntersect(vec2 p1, vec2 p2, vec2 p3, vec2 p4)
{
    float d = (p4.y - p3.y) * (p2.x - p1.x) - (p4.x - p3.x) * (p2.y - p1.y);
    if (d == 0.0)
    {
        return false;
    }

    float xd = p1.x - p3.x;
    float yd = p1.y - p3.y;
    float ua = ((p4.x - p3.x) * yd - (p4.y - p3.y) * xd) / d;
    if (ua < 0.0 || ua > 1.0)
    {
        return false;
    }

    float ub = ((p2.x - p1.x) * yd - (p2.y - p1.y) * xd) / d;
    return (ub >= 0.0 && ub <= 1.0);
}

vec2 circleConstraint(vec2 velocity, vec2 position, vec2 sphereCenter, float radius,
    float resilience, float friction, float minimalVelocity)
{
    vec2 posToCheck = position + (velocity * deltaTime);
    vec2 delta = posToCheck - sphereCenter;
    float dist = length(delta);
    if (dist < radius)
    {
        velocity = bounceReflection(velocity, delta / dist,
            distance(position, sphereCenter) < radius,
            resilience, friction, minimalVelocity);
    }
    return velocity;
}

vec2 rectConstraint(vec2 velocity, vec2 position, vec2 rectCenter, float halfWidth, float halfHeight,
    float resilience, float friction, float minimalVelocity)
{
    vec2 newPosition = position + (velocity * deltaTime);

    vec2 topLeft     = vec2(rectCenter.x - halfWidth, rectCenter.y + halfHeight);
    vec2 topRight    = vec2(rectCenter.x + halfWidth, rectCenter.y + halfHeight);
    vec2 bottomRight = vec2(rectCenter.x + halfWidth, rectCenter.y - halfHeight);
    vec2 bottomLeft  = vec2(rectCenter.x - halfWidth, rectCenter.y - halfHeight);

    if (!isPointInRect(newPosition, bottomLeft, topRight))
    {
        return velocity;
    }

    bool oldPosInRect = isPointInRect(position, bottomLeft, topRight);

    // Left side
    if (segmentsIntersect(position, newPosition, topLeft, bottomLeft))
    {
        return bounceReflection(velocity, vec2(-1.0, 0.0), oldPosInRect, resilience, friction, minimalVelocity);
    }
    // Top side
    if (segmentsIntersect(position, newPosition, topLeft, topRight))
    {
        return bounceReflection(velocity, vec2(0.0, 1.0), oldPosInRect, resilience, friction, minimalVelocity);
    }
    // Right side
    if (segmentsIntersect(position, newPosition, topRight, bottomRight))
    {
        return bounceReflection(velocity, vec2(1.0, 0.0), oldPosInRect, resilience, friction, minimalVelocity);
    }
    // Bottom side
    if (segmentsIntersect(position, newPosition, bottomLeft, bottomRight))
    {
        return bounceReflection(velocity, vec2(0.0, -1.0), oldPosInRect, resilience, friction, minimalVelocity);
    }

    return velocity;
}
