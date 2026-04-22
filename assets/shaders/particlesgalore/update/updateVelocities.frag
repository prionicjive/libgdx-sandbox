#version 330

in vec2 vTexCoord0;

out vec4 fragmentColor;

uniform sampler2D positionMap;
uniform sampler2D velocityMap;

uniform float spawnWidth;
uniform float spawnHeight;

uniform vec3 attractorPos;
uniform float attractorForce;
uniform float dragPercentage;
uniform float deltaTime;

vec2 bounceReflection(vec2 _Velocity, vec2 _Normal, bool _AlreadyInside,
	float _Resilience, float _Friction, float _MinimalVelocity)
{
	// Distribute velocity to normal and tangential contributions.
	//
	// normal component of velocity = dot(velocity, normal) * velocity
	float normalContribution = dot(_Velocity, _Normal);
	vec2 vNormal = normalContribution * _Normal;

	// tangental component of velocity = velocity - normal
	vec2 vTangent = _Velocity - vNormal;

	if (_AlreadyInside)
	{
		// Get particle outside the collider as quickly as possible,
		// either with original or reflected velocity.
		if (normalContribution < 0.0)
			_Velocity = vTangent - vNormal;
	}
	else
	{
		// If the velocity is less than the minimal velocity, don't attempt to apply friction
		if (length(_Velocity) < _MinimalVelocity)
		{
			_Friction = 1.0;
		}

		// Slowdown tangential movement with friction (in theory 1 - friction)
		// and reflected normal movement via resilience factor.
		//
		// Velocity = (friction factor * tangental component of velocity) - (resilience factor * normal component of velocity)
		_Velocity = vTangent * _Friction - vNormal * _Resilience;
	}

	return _Velocity;
}

bool isPointInRect(vec2 point, vec2 bottomLeft, vec2 topRight)
{
    return (bottomLeft.x <= point.x && topRight.x >= point.x && bottomLeft.y <= point.y && topRight.y >= point.y);
}

bool segmentsIntersect(vec2 point1, vec2 point2, vec2 point3, vec2 point4)
{
    float xd, yd, ua, ub;
    float d = (point4.y - point3.y) * (point2.x - point1.x) - (point4.x - point3.x) * (point2.y - point1.y);

    if (d != 0.0)
    {
        yd = point1.y - point3.y;
        xd = point1.x - point3.x;
        ua = ((point4.x - point3.x) * yd - (point4.y - point3.y) * xd) / d;
        if (ua >= 0.0 && ua <= 1.0)
        {
            ub = ((point2.x - point1.x) * yd - (point2.y - point1.y) * xd) / d;
            if (ub >= 0.0 && ub <= 1.0)
            {
                return true;
            }
        }
    }

    return false;
}

vec2 circleConstraint(vec2 _Velocity, vec2 _Position, vec2 _SphereCenter, float _Radius,
                        float _Resilience, float _Friction, float _MinimalVelocity)
{
    vec2 posToCheck = _Position + (_Velocity * deltaTime);
	vec2 delta = posToCheck - _SphereCenter;
	float dist = length(delta);
	if (dist < _Radius)
	{
		_Velocity = bounceReflection(_Velocity, delta / dist, distance(_Position, _SphereCenter) < _Radius, _Resilience, _Friction, _MinimalVelocity);
	}

	return _Velocity;
}

// TODO Check if new point is inside rect and if old one was not. From here, we can narrow down and guess what side
vec2 rectConstraint(vec2 _Velocity, vec2 _Position, vec2 rectCenter, float halfWidth, float halfHeight,
                    	float _Resilience, float _Friction, float _MinimalVelocity)
{
    vec2 newPosition = _Position + (_Velocity * deltaTime);

    vec2 topLeft = vec2(rectCenter.y - halfWidth, rectCenter.y + halfHeight);
    vec2 topRight = vec2(rectCenter.y + halfWidth, rectCenter.y + halfHeight);
    vec2 bottomRight = vec2(rectCenter.y + halfWidth, rectCenter.y - halfHeight);
    vec2 bottomLeft = vec2(rectCenter.y - halfWidth, rectCenter.y - halfHeight);

    // First, see if the new position is in the rectangle
    if(isPointInRect(newPosition, bottomLeft, topRight))
    {
        bool oldPosInRect = isPointInRect(_Position, bottomLeft, topRight);

        // Left side
        vec2 point1 = _Position;
        vec2 point2 = newPosition;
        vec2 point3 = topLeft;
        vec2 point4 = bottomLeft;

        bool foundIntersection = false;

        if(segmentsIntersect(point1, point2, point3, point4))
        {
            foundIntersection = true;
            _Velocity = bounceReflection(_Velocity, vec2(-1,0), oldPosInRect, _Resilience, _Friction, _MinimalVelocity);
        }

        // Top side
        if(!foundIntersection)
        {
            point3 = topLeft;
            point4 = topRight;

            if(segmentsIntersect(point1, point2, point3, point4))
            {
                foundIntersection = true;
                _Velocity = bounceReflection(_Velocity, vec2(0,1), oldPosInRect, _Resilience, _Friction, _MinimalVelocity);
            }
        }

        // Right side
        if(!foundIntersection)
        {
            point3 = topRight;
            point4 = bottomRight;

            if(segmentsIntersect(point1, point2, point3, point4))
            {
                foundIntersection = true;
                _Velocity = bounceReflection(_Velocity, vec2(1,0), oldPosInRect, _Resilience, _Friction, _MinimalVelocity);
            }
        }

        // Bottom side
        if(!foundIntersection)
        {
            point3 = bottomLeft;
            point4 = bottomRight;

            if(segmentsIntersect(point1, point2, point3, point4))
            {
                foundIntersection = true;
                _Velocity = bounceReflection(_Velocity, vec2(0,-1), oldPosInRect, _Resilience, _Friction, _MinimalVelocity);
            }
        }
    }

    return _Velocity;
}

float getScaledDistance(float distance, float maxDistance)
{
    float scaledDistance = 0.0;

    // If no max distance, assume half the spawnWidth as the max
    if(maxDistance <= 0.0)
    {
        scaledDistance = (5.0 * distance) / (spawnWidth * 0.5);
    }
    else if(distance < maxDistance)
    {
        scaledDistance = (5.0 * distance) / maxDistance;
    }

    return scaledDistance;
}

vec2 localAttractor(vec2 _Position, vec2 _ForcePosition, float _Strength, float maxDistance)
{
	vec2 vecToCenter = _ForcePosition - _Position;
	float distance = length(vecToCenter);
	vec2 direction = normalize(vecToCenter);

	float strength = 0.0;
    float scaledDistance = getScaledDistance(distance, maxDistance);

	if(scaledDistance > 0.0)
	{
	    strength = _Strength / ((scaledDistance * scaledDistance) + 10.0);

	    // Make sure we properly cap the max force

	}

	return strength * direction;
}

vec2 localAttractor(vec2 _Position, vec2 _ForcePosition, float _Strength)
{
    return localAttractor(_Position, _ForcePosition, _Strength, 0.0);
}

vec2 localRepulsor(vec2 _Position, vec2 _ForcePosition, float _Strength, float maxDistance)
{
	return localAttractor(_Position, _ForcePosition, -_Strength, maxDistance);
}

vec2 localRepulsor(vec2 _Position, vec2 _ForcePosition, float _Strength)
{
	return localRepulsor(_Position, _ForcePosition, _Strength, 0.0);
}

void main(void)
{
    vec2 currVelocity = texture(velocityMap, vTexCoord0).xy;
    vec2 currPosition = texture(positionMap, vTexCoord0).xy;

    // TODO Apply gravity and local forces (Attractors, repulsors, etc)
    vec2 acceleration = localRepulsor(currPosition, vec2(200, 500), 15000.0, 75.0);
    acceleration += localAttractor(currPosition, attractorPos.xy, attractorForce, 250.0);

    // Calculate the new velocity by following this formula...
    // newVelocity = currVelocity + (acceleration * deltaTime)
    vec2 newVelocity = currVelocity + (acceleration * deltaTime);

    // Apply dampening/drag
    newVelocity -= newVelocity * (dragPercentage * deltaTime);

    // Here, we check to see if the particle is beyond the screen bounds. We do this to ensure that we know to reflect the
    // proper component of velocity before updating positions later on.

    // TODO Make parameters for a certain amount of programmatically definable circle colliders
    newVelocity = circleConstraint(newVelocity, currPosition, vec2(750.0, 200.0), 100.0, 0.8, 0.7, 0.01);
    newVelocity = circleConstraint(newVelocity, currPosition, vec2(500.0, 200.0), 30.0, 0.8, 0.7, 0.01);

    // TODO Make parameters for a certain amount of programmatically definable box colliders
    newVelocity = rectConstraint(newVelocity, currPosition, vec2(400.0, 200.0), 50.0, 100.0, 0.8, 0.7, 0.01);

    // TODO Pass in half the point size

    // TODO GREATLY clean up bounds logic, preferably use boundsReflect() method
    vec2 newPosition = currPosition + (currVelocity * deltaTime);

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

    // Send out the velocity
    fragmentColor = vec4(newVelocity, 0.0, 1.0);
}

