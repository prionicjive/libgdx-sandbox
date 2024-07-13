#version 330
#ifdef GL_ES
    precision highp float;
#endif

// Incoming ins to be aware of on the Fragment object
 // NOTE: These have been interpolated over the verticies
 in vec2 vTexCoord0;
 in vec4 vColor;

 out vec4 fragmentColor;

 // Point sprite texture
 uniform sampler2D pointSpriteTex;

 void main(void)
 {
     // Calculate final color
     fragmentColor = texture(pointSpriteTex, gl_PointCoord) * vColor;
 }
