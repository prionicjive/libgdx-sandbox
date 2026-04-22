# particlesgalore — GPU particle system

GPU-resident particle simulation. State (position, velocity) lives in floating-point textures; simulation steps are fragment-shader passes that read "previous state" textures and write "next state" textures via FBOs. The CPU never reads particle state back — it only issues draw calls and sets uniforms.

Shaders used by this module live at `assets/shaders/particlesgalore/`:
- `passthru.vert` — shared vertex shader for every **update** pass (straight `SpriteBatch`-style passthrough).
- `update/initPositions.frag`, `update/initVelocities.frag` — one-time state seeding.
- `update/updatePositions.frag`, `update/updateVelocities.frag` — per-frame integrator + forces + collisions.
- `update/copyTexture.frag` — scratchpad → state blit (ping-pong resolve).
- `render/transformAndCalculateVertexColor.vert` — reads `positionMap` in the vertex shader to place `GL_POINTS`.
- `render/setPointSpriteColor.frag` — samples the point-sprite texture, tints by per-vertex color.

## The data layout trick

`SQRT_MAX_PARTICLES = 128` → 16,384 particles laid out as a 128×128 grid. Every particle owns exactly one texel in each of three `FloatFrameBuffer`s (RGBA32F). A particle's "slot" is identified by a 2D UV in `[0,1)`:

- `positionRT` — texel `xy` = world-space position of that particle.
- `velocityRT` — texel `xy` = velocity.
- `temporaryRT` — scratchpad used to resolve the read-while-write hazard.

The per-vertex CPU mesh (`particlesVB`, `MAX_PARTICLES` points) stores each particle's **own UV into the state textures** in its `a_position` attribute (`position.x = i/SQRT, position.y = j/SQRT`). Per-vertex color is set once at init; it never updates. So the vertex buffer is essentially a static table of "where do I look up my state?" handles.

## Ping-pong via a scratchpad FBO

OpenGL disallows sampling from a texture that is simultaneously bound as the current FBO's color attachment. `ParticleSystem.executeUpdateTechnique(shader, rtToUse)` works around that with a two-pass ping-pong using `temporaryRT`:

1. **Bind `temporaryRT`.** Bind the existing `positionTexture` and `velocityTexture` as `sampler2D` uniforms (`positionMap` → TEXTURE1, `velocityMap` → TEXTURE2). Set force/attractor/delta uniforms. Use `SpriteBatch` to draw `randomTexture` (TEXTURE0, which becomes the fragment shader's `u_texture`) as a full-size quad covering the FBO. The quad is the ONLY reason the fragment shader runs — it's a GPGPU-style "compute over all texels" trigger, and `vTexCoord0` inside the shader *is* the particle's slot UV.
2. **Bind `rtToUse`** (`positionRT` or `velocityRT`). Switch shader to `copyTexture`. Draw `temporaryTexture` onto it. This blits the fresh results from scratchpad into the canonical state texture so subsequent passes read the updated values.

Order matters in `simulateParticles()`:
```
updateVelocities → writes new velocity into velocityRT
updatePositions  → reads the *updated* velocity + old position, writes new position
```
Velocity is integrated first so the position shader sees the freshly-written velocity. `updatePositions.frag` uses `currVelocity * deltaTime` (Euler), so passing the new velocity is intentional, not a bug.

Note that `positionMapUpdate`/`velocityMapUpdate` only get bound inside `executeUpdateTechnique` when `areDataTexutresInitialized` is true. The very first two calls (`initVelocities`, `initPositions`) run without those samplers — the init shaders only touch `u_texture` (the random texture). After the init pass, the flag latches and every subsequent pass binds both maps.

## `randomTexture` — compile-time randomness table

`initializeParticles()` fills `randomTexture` (RGBA32F, 128×128) with `MathUtils.random()` values and uploads it once. Because LibGDX's `SpriteBatch.draw(randomTexture, ...)` binds it to TEXTURE0, the fragment shaders receive it as `u_texture`. Init shaders use it to produce deterministic per-particle seeds without needing a GLSL RNG; update shaders currently ignore it but the binding is kept for shader symmetry. This texture is read-only — never a render target.

## Render pass — vertex shader texture fetch

`renderParticles()`:
1. Enables additive blending (`GL_SRC_ALPHA, GL_ONE`), `GL_VERTEX_PROGRAM_POINT_SIZE`, and `GL_POINT_SPRITE` (hex `0x8861`, hardcoded because LibGDX's GL30 bindings don't expose it).
2. Binds `positionRT.getColorBufferTexture()` to TEXTURE0 (`positionMap`) and the sprite atlas `psTexture` to TEXTURE1 (`pointSpriteTex`).
3. `particlesVB.render(particleRender, GL_POINTS, 0, MAX_PARTICLES)` issues 16,384 points.

Inside `transformAndCalculateVertexColor.vert`, **the vertex shader samples `positionMap`** using the incoming `a_position.xy` (the particle's slot UV, not a world position) to fetch its current world-space position. `gl_PointSize = pointSize`; `gl_Position = u_projTrans * worldPosition`. `setPointSpriteColor.frag` samples the sprite using `gl_PointCoord` and multiplies by the per-vertex color.

This is the payoff of the whole architecture: particle state never leaves GPU memory. The vertex shader reads the simulation output texture directly.

## GameScreen feedback-buffer trail

`GameScreen` adds a `accumulationFBO` (`FloatFrameBuffer` at virtual screen size, 1280×720). Each frame:

1. Draw a nearly-transparent black full-screen quad (`alpha ≈ 0.075`) over `accumulationFBO` with normal alpha blending — this **fades** last frame's content toward black instead of clearing.
2. Call `particleSystem.renderParticles()` (additive) on top of the faded buffer.
3. Unbind the FBO, re-apply the `FitViewport`, clear the back buffer, and draw `accumulationFboTextureRegion` (flipped vertically at construction) via `SpriteBatch`.

The trail effect is entirely a function of the fade alpha. Don't `glClear` the accumulation FBO — that kills the trail. Adjust trail length by tweaking the fade quad's alpha (longer trails = smaller alpha).

## Uniforms the update shaders expect

Set per-pass inside `executeUpdateTechnique`:
- `spawnWidth`, `spawnHeight` — `virtualScreenSize` (bounds for init + wall bounce).
- `attractorPos` (`vec3`, unprojected from screen to world), `attractorForce`.
- `dragPercentage` (0.05), `deltaTime` (already multiplied by `timeScale`).

`updateVelocities.frag` hardcodes additional colliders (a circle at (750,200) r=100, a circle at (500,200) r=30, a rect at (400,200) 50×100) and a repulsor at (200,500). These are intentionally baked in — there is a TODO to parameterize them.

Render pass uniforms: `u_projTrans` (camera combined), `pointSize`, plus sampler bindings.

## Input (handled inside `ParticleSystem`)

`ParticleSystem.initializeInputProcessor()` installs a **global** `InputAdapter` in its constructor, overwriting any existing input processor. Keys `1`/`2`/`3` jump `timeScale` to `0` / `1` / `10`; `+`/`-` nudge by `0.1` within `[0, 10]`. Touch adds `3000` to attractor force. Mouse position is unprojected every frame into `attractorPosition`. If you ever wire this module into a larger input stack, replace the bare `setInputProcessor` with an `InputMultiplexer`.

## Constraints & pitfalls

- **`SQRT_MAX_PARTICLES` must be a power of two** and must match the mesh layout — changing one without the other produces garbage lookups.
- All three state FBOs are created with `hasDepth=false`; no stencil. Don't add depth-test code to the update passes — it's pointless and would need the FBO reconstructed.
- The state textures use `Nearest` filtering (essential — linear filtering would interpolate between unrelated particles' state).
- `ShaderProgram.pedantic = false` is set globally in `initializeShaders()` because several uniforms (`velocityMap` in the init shaders, `positionMap` in `initVelocities`) are declared but unused. Keep it off or split the shaders.
- `ParticleSystem` has no `dispose()`. FBOs, shaders, mesh, and `randomTexture` leak if the screen is swapped. Fix before reusing the module or running it alongside another.
- `GameScreen.accumulationFBO` is also not disposed.
- The render pass mutates GL state (blend func, point-sprite enables) and restores most of it, but leaves the active texture unit at `GL_TEXTURE0` — fine for `SpriteBatch` that follows, watch out if you chain other custom GL.
- `renderParticles()` uses `_game.camera` with the virtual-screen ortho set by `GameScreen.render` immediately before; the update passes (`executeUpdateTechnique`) *also* mutate `_game.camera` to the 128×128 FBO ortho. `GameScreen` re-sets the ortho between `updateAndSimulate` and the FBO render — don't remove that line.
