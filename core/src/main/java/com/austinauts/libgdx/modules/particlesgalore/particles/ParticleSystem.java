package com.austinauts.libgdx.modules.particlesgalore.particles;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FloatFrameBuffer;
import com.badlogic.gdx.graphics.glutils.FloatTextureData;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.FloatArray;
import com.austinauts.libgdx.common.utils.ShaderHelper;

import java.nio.FloatBuffer;

public class ParticleSystem {
	// Reference to the game
	private AustinautsGame _game;

	// Render target that will hold the positions of the particles
	private FloatFrameBuffer positionRT;

	// Render target that will hold the velocities of the particles
	private FloatFrameBuffer velocityRT;

	// Temporary render target, needed when updating the other render targets.
	// This is used because if youcan not read and write to a texture at the same time
	private FloatFrameBuffer temporaryRT;

	// Used to store vertex info from the pixel texture
	private Mesh particlesVB;

	// TODO Externalize this all and make it reloadable in game via developer command

	// Dimension for render targets. We will have a maximum of SQRT_MAX_PARTICLES * SQRT_MAX_PARTICLES particles.
	// NOTE: Be sure this is power of 2!
	//
	// 1x1 = 1
	// 2x2 = 4
	// 4x4 = 16
	// 8x8 = 64
	// 16x16 = 256
	// 32x32 = 1,024
	// 64x64 = 4,096
	// 128x128 = 16,384
	// 256x256 = 65,536
	// 512x512 = 262,144
	// 1024x1024 = 1,048,576
	private final static int SQRT_MAX_PARTICLES = 128; // TODO Make more tweakable
	private final static int MAX_PARTICLES = SQRT_MAX_PARTICLES * SQRT_MAX_PARTICLES;
	private final static float POINT_SIZE = 2; // TODO Make more tweakable
	private final static float HALF_POINT_SIZE = POINT_SIZE / 2;

	//Position attribute - (x, y)
	public static final int POSITION_COMPONENTS = 2;

	//Color attribute - (r, g, b, a)
	public static final int COLOR_COMPONENTS = 4;

	//Total number of components for all attributes
	public static final int NUM_COMPONENTS = POSITION_COMPONENTS + COLOR_COMPONENTS;

	// Update shader params
	private Texture positionMapUpdate;
	private Texture velocityMapUpdate;
	private Vector3 attractorPosition; // The location of the attractor
	private static final float idealAttractorForce = 1500.0f;
	private float currentAttractorForce = idealAttractorForce; // Linear attractor force
	private static final float ATTRACTOR_MAX_DISTANCE = 250.0f;
	private static final float dragPercentage = 0.05f;
	private float deltaTimeForShader = 0.0f;

	// Collider / point-force storage. Layout must match the uniform array element size in updateVelocities.frag
	// (all three groups are packed as vec4 for driver portability).
	public static final int MAX_CIRCLE_COLLIDERS = 8;
	public static final int MAX_RECT_COLLIDERS = 4;
	public static final int MAX_POINT_FORCES = 8;

	private final float[] circleColliderData = new float[MAX_CIRCLE_COLLIDERS * 4]; // xy=center, z=radius, w unused
	private final float[] rectColliderData = new float[MAX_RECT_COLLIDERS * 4];     // xy=center, zw=half-extents
	private final float[] pointForceData = new float[MAX_POINT_FORCES * 4];         // xy=position, z=signed strength, w=maxDistance
	private int circleColliderCount = 0;
	private int rectColliderCount = 0;
	private int pointForceCount = 0;

	// Time scale variables
	private float timeScale = 1.0f; // Utilized to speed up / slow down the simulation
	private static final float MIN_TIME_SCALE = 0f;
	private static final float MAX_TIME_SCALE = 10f;
	private static final float TIME_SCALE_INCREMENT = 0.1f;

	// Render shader params
	private Texture psTexture;
	private Texture positionMapRender;
	private float spawnWidth;
	private float spawnHeight;

	// Initialize flag
	private boolean areDataTexutresInitialized = false;

	// Update shaders
	private ShaderProgram initPositions, initVelocities, updatePositions, updateVelocities, copyTexture;

	// Render shaders
	private ShaderProgram particleRender;

	private Texture temporaryTexture;
	private Texture positionTexture;
	private Texture velocityTexture;

	// Used to store a series of random values, that will be accessed from various shaders
	//
	// This will only ever be READ from.
	private Texture randomTexture;

	public ParticleSystem(final AustinautsGame game) {
		_game = game;

		initializeInputProcessor();

		initializeShaders();
		initializeParticles();
		initializeAttractors();
		initializeDefaultColliders();
	}

	// ---------------------------
	// Collider configuration API
	// ---------------------------

	/** Add a circular collider. Particles entering its disc bounce off. */
	public void addCircleCollider(float centerX, float centerY, float radius) {
		if (circleColliderCount >= MAX_CIRCLE_COLLIDERS) {
			throw new IllegalStateException("Exceeded MAX_CIRCLE_COLLIDERS (" + MAX_CIRCLE_COLLIDERS + ")");
		}
		int base = circleColliderCount * 4;
		circleColliderData[base] = centerX;
		circleColliderData[base + 1] = centerY;
		circleColliderData[base + 2] = radius;
		circleColliderData[base + 3] = 0f;
		circleColliderCount++;
	}

	/** Add an axis-aligned rectangular collider specified by center + half-extents. */
	public void addRectCollider(float centerX, float centerY, float halfWidth, float halfHeight) {
		if (rectColliderCount >= MAX_RECT_COLLIDERS) {
			throw new IllegalStateException("Exceeded MAX_RECT_COLLIDERS (" + MAX_RECT_COLLIDERS + ")");
		}
		int base = rectColliderCount * 4;
		rectColliderData[base] = centerX;
		rectColliderData[base + 1] = centerY;
		rectColliderData[base + 2] = halfWidth;
		rectColliderData[base + 3] = halfHeight;
		rectColliderCount++;
	}

	/**
	 * Add a point-force that pulls (positive strength) or pushes (negative strength) particles within maxDistance.
	 * Pass maxDistance <= 0 to use the default falloff scaled by spawnWidth.
	 */
	public void addPointForce(float x, float y, float strength, float maxDistance) {
		if (pointForceCount >= MAX_POINT_FORCES) {
			throw new IllegalStateException("Exceeded MAX_POINT_FORCES (" + MAX_POINT_FORCES + ")");
		}
		int base = pointForceCount * 4;
		pointForceData[base] = x;
		pointForceData[base + 1] = y;
		pointForceData[base + 2] = strength;
		pointForceData[base + 3] = maxDistance;
		pointForceCount++;
	}

	public void clearColliders() {
		circleColliderCount = 0;
		rectColliderCount = 0;
		pointForceCount = 0;
	}

	public void updateAndSimulate(float delta) {
		updateParticles(delta);
		simulateParticles();
	}

	public void renderParticles() {
		// Set up enablement for point sprites
		Gdx.gl30.glEnable(GL30.GL_BLEND);
		Gdx.gl30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE); // TODO Better determine blend function or make toggleable
		Gdx.gl30.glEnable(GL30.GL_VERTEX_PROGRAM_POINT_SIZE); // Needed to properly leverage point size
		Gdx.gl30.glEnable(0x8861); // TODO Needed to properly leverage point size (GL_POINT_SPRITE)

		// Begin the shader
		particleRender.begin();
		{
			// Set shader params
			particleRender.setUniformMatrix("u_projTrans", _game.camera.combined);
			particleRender.setUniformf("pointSize", POINT_SIZE);

			// Set up the needed textures
			positionMapRender = positionRT.getColorBufferTexture();
			Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0);
			positionMapRender.bind();
			particleRender.setUniformi("positionMap", 0);

			Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE1);
			psTexture.bind();
			particleRender.setUniformi("pointSpriteTex", 1);

			// Render the points
			particlesVB.render(particleRender, GL30.GL_POINTS, 0, MAX_PARTICLES);
		}
		// End the shader
		particleRender.end();

		// Reset the render state to what it was
		Gdx.gl30.glDisable(GL30.GL_VERTEX_PROGRAM_POINT_SIZE);
		Gdx.gl.glDisable(0x8861); // TODO Needed to properly leverage point size (GL_POINT_SPRITE)
		Gdx.gl30.glDisable(GL30.GL_BLEND);
		Gdx.gl30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
		Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0);
	}

	// ---------------------------
	// Private methods
	// ---------------------------

	private void initializeInputProcessor() {
		// TODO Put InputProcessing in another place?
		Gdx.input.setInputProcessor(new InputAdapter() {
			public boolean keyDown(int key) {
				if (key == Input.Keys.NUM_1) {
					timeScale = MIN_TIME_SCALE;
					return true;
				}
				else if (key == Input.Keys.NUM_2) {
					timeScale = 1.0f;
					return true;
				}
				else if (key == Input.Keys.NUM_3) {
					timeScale = MAX_TIME_SCALE;
					return true;
				}
				else if (key == Input.Keys.PLUS) {
					timeScale += TIME_SCALE_INCREMENT;

					if (timeScale > MAX_TIME_SCALE) {
						timeScale = MAX_TIME_SCALE;
					}

					return true;
				}
				else if (key == Input.Keys.MINUS) {
					timeScale -= TIME_SCALE_INCREMENT;

					if (timeScale < MIN_TIME_SCALE) {
						timeScale = MIN_TIME_SCALE;
					}

					return true;
				}

				return false;
			}
		});
	}

	private void initializeShaders() {
		spawnWidth = _game.virtualScreenSize.width;
		spawnHeight = _game.virtualScreenSize.height;

		// SpriteBatch.flush() calls setUniformi("u_texture", 0) on every custom shader, but the update-pass
		// shaders don't all read u_texture. Leaving pedantic off avoids that mismatch. Moving the update passes
		// off SpriteBatch (direct Mesh/ShaderProgram) would let us re-enable pedantic.
		ShaderProgram.pedantic = false;

		// Shared passthrough vertex shader for every update pass.
		final String VERT_SRC = ShaderHelper.loadShaderSource("shaders/particlesgalore/passthru.vert");

		initPositions    = ShaderHelper.createShader(VERT_SRC, ShaderHelper.loadShaderSource("shaders/particlesgalore/update/initPositions.frag"));
		initVelocities   = ShaderHelper.createShader(VERT_SRC, ShaderHelper.loadShaderSource("shaders/particlesgalore/update/initVelocities.frag"));
		updatePositions  = ShaderHelper.createShader(VERT_SRC, ShaderHelper.loadShaderSource("shaders/particlesgalore/update/updatePositions.frag"));
		updateVelocities = ShaderHelper.createShader(VERT_SRC, ShaderHelper.loadShaderSource("shaders/particlesgalore/update/updateVelocities.frag"));
		copyTexture      = ShaderHelper.createShader(VERT_SRC, ShaderHelper.loadShaderSource("shaders/particlesgalore/update/copyTexture.frag"));

		particleRender = ShaderHelper.createShader(
			ShaderHelper.loadShaderSource("shaders/particlesgalore/render/transformAndCalculateVertexColor.vert"),
			ShaderHelper.loadShaderSource("shaders/particlesgalore/render/setPointSpriteColor.frag"));

		temporaryRT = new FloatFrameBuffer(SQRT_MAX_PARTICLES, SQRT_MAX_PARTICLES, false);
		temporaryTexture = temporaryRT.getColorBufferTexture();
		temporaryTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

		positionRT = new FloatFrameBuffer(SQRT_MAX_PARTICLES, SQRT_MAX_PARTICLES, false);
		positionTexture = positionRT.getColorBufferTexture();
		positionTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

		velocityRT = new FloatFrameBuffer(SQRT_MAX_PARTICLES, SQRT_MAX_PARTICLES, false);
		velocityTexture = velocityRT.getColorBufferTexture();
		velocityTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

		psTexture = _game.assetManager.get(AustinautsGame.TEXTURE_PARTICLE);
	}

	private void initializeParticles() {
		// Create our particle components array (One float for every component for every particle)
		FloatArray particlesComponentsArray = new FloatArray(true, MAX_PARTICLES * NUM_COMPONENTS);

		// Create our array that'll hold what constitutes the random values needed for our "random" texture. This will
		// help simplify determining random values from the shader as it can be a simple look up into the texture
		FloatArray randomValuesComponentsArray = new FloatArray(true, MAX_PARTICLES * 4);

		// Useful for initializing
		Vector2 position = new Vector2();
		Color color = new Color();

		// For each particle...
		for (int i = 0; i < SQRT_MAX_PARTICLES; i++) {
			for (int j = 0; j < SQRT_MAX_PARTICLES; j++) {
				// Fill the index with a default color and position.
				//
				// Be sure the position corresponds exactly with its coordinates in the particleCount * particleCount texture,

				// Set position
				position.x = (float) i / (float) SQRT_MAX_PARTICLES;
				position.y = (float) j / (float) SQRT_MAX_PARTICLES;

				particlesComponentsArray.add(position.x);
				particlesComponentsArray.add(position.y);

				// Set color
				color.set(0.1f + MathUtils.random() * 0.3f, 0.3f + MathUtils.random() * 0.5f, 0.3f + MathUtils.random() * 0.7f, 1.0f);

				particlesComponentsArray.add(color.r);
				particlesComponentsArray.add(color.g);
				particlesComponentsArray.add(color.b);
				particlesComponentsArray.add(color.a);

				// Set the random value
				randomValuesComponentsArray.add(MathUtils.random());
				randomValuesComponentsArray.add(MathUtils.random());
				randomValuesComponentsArray.add(MathUtils.random());
				randomValuesComponentsArray.add(MathUtils.random());
			}
		}

		// TODO Mesh and VB will need to be adjusted/rebuilt if there are only a certain number of "alive" particles
		particlesVB = new Mesh(true, MAX_PARTICLES, 0,
			new VertexAttribute(VertexAttributes.Usage.Position, POSITION_COMPONENTS, ShaderProgram.POSITION_ATTRIBUTE),
			new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, COLOR_COMPONENTS, ShaderProgram.COLOR_ATTRIBUTE));
		particlesVB.setVertices(particlesComponentsArray.items);

		// Build a texture with random Float values
		FloatTextureData userFloatTextureData = new FloatTextureData(SQRT_MAX_PARTICLES, SQRT_MAX_PARTICLES, GL30.GL_RGBA32F, GL30.GL_RGBA, GL30.GL_FLOAT, false);
		userFloatTextureData.prepare();
		BufferUtils.copy(randomValuesComponentsArray.items, 0, userFloatTextureData.getBuffer(), MAX_PARTICLES * 4);
		randomTexture = new Texture(userFloatTextureData);
		randomTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
		randomTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
	}

	private void initializeAttractors() {
		// Set the attractor position to be at the origin
		attractorPosition = new Vector3();
	}

	// Seeds the colliders used by the demo scene. Consumers can clearColliders() + add their own to override.
	private void initializeDefaultColliders() {
		addCircleCollider(750f, 200f, 100f);
		addCircleCollider(500f, 200f, 30f);
		addRectCollider(400f, 200f, 50f, 100f);
		addPointForce(200f, 500f, -15000f, 75f);
	}

	private void updateParticles(float delta) {
		// Get the time (In fractions of a second) since the last update
		deltaTimeForShader = delta;

		float addedAttractorForce = Gdx.input.isTouched() ? 3000.0f : 0f;
		currentAttractorForce = idealAttractorForce + addedAttractorForce;

		// Update the attractor position in screen coordinates (Origin is top left)
		attractorPosition.x = Gdx.input.getX();
		attractorPosition.y = Gdx.input.getY();

		// "Unproject" the attractor position from screen space to world/viewport space
		_game.camera.unproject(attractorPosition, _game.viewport.getScreenX(), _game.viewport.getScreenY(), _game.viewport.getScreenWidth(), _game.viewport.getScreenHeight());
	}

	private void simulateParticles() {
		// Run the physic passes, using the proper render target to read old values/write new values
		//
		// Initialize if we haven't
		if (!areDataTexutresInitialized) {
			executeUpdateTechnique(initVelocities, velocityRT);
			executeUpdateTechnique(initPositions, positionRT);

			// Set to true so we don't hit this again
			areDataTexutresInitialized = true;
		}

		executeUpdateTechnique(updateVelocities, velocityRT);
		executeUpdateTechnique(updatePositions, positionRT);
	}

	private void executeUpdateTechnique(ShaderProgram shaderToUse, FrameBuffer rtToUse) {
		Gdx.gl30.glDisable(GL30.GL_BLEND);

		// Set the graphic device's rendering target to our scratchpad render target
		temporaryRT.begin();
		{
			// Clear the render target
			// Clear the FBO fully
			Gdx.gl30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
			Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

			// Resize the camera and the batch for the FBO / RT
			_game.camera.setToOrtho(false, temporaryRT.getWidth(), temporaryRT.getHeight());
			_game.batch.setProjectionMatrix(_game.camera.combined);

			// Set the shader to use
			_game.batch.setShader(shaderToUse);

			// Begin the quickest and simplest sprite batch we can, then begin the specified technique!
			_game.batch.begin();
			{
				applyUpdateShaderUniforms(shaderToUse);

				// We pass in the randomTexture to be "drawn", even if it is just to make sure the shader has access to it
				//
				// We render a rectangle that is particleCount * particleCount to our current render target, which is the
				// scratchpad render target.
				//
				// This call will cause 4 different things, depending on the current technique/shader...
				//
				// InitVelocities: For each pixel coming in, set the outgoing color to contain all zeros to be stored in the scratchpad
				// texture's color channel at the current pixel.
				//
				// InitPositions: For each pixel coming in, set the outgoing color to contain a float4 with the current particle's
				// position within screen bounds. This is to be stored in the scratchpad texture's color channel at the current pixel.
				//
				// UpdateVelocity: For each pixel coming in, use the current pixel's UV coords to look at velocity texture
				// for previous data, and calculate the new velocity (Taking forces into account) that will be stored in the
				// scratchpad texture's color channel at the current pixel.
				//
				// UpdatePosition: For each pixel coming in, use the current pixel's UV coords to look at velocity and position
				// textures for previous data, and calculate the new position (Taking collision into account) that will be stored
				// in the scratchpad texture's color channel at the current pixel.
				//
				// Later, we can extract the scratchpad render target's data to a texture that
				Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0);

				_game.batch.draw(randomTexture, 0, 0, temporaryRT.getWidth(), temporaryRT.getHeight());
			}
			// End the technique and the sprite batch
			_game.batch.end();
		}
		temporaryRT.end();

		// Set the graphic device's rendering target to our scratchpad render target
		rtToUse.begin();
		{
			// Set the current technique to "CopyTexture"
			//
			// This is simply used to copy data from the temp/scratchpad texture to the proper by extracting data in the temp render target
			// to a texture.
			//
			// This way, textures can be pulled off later from the velocities or positions render targets for further processing.
			_game.batch.setShader(copyTexture);

			// Resize the camera and the batch for the FBO / RT
			_game.camera.setToOrtho(false, rtToUse.getWidth(), rtToUse.getHeight());
			_game.batch.setProjectionMatrix(_game.camera.combined);

			_game.batch.begin();
			{
				Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0);
				_game.batch.draw(temporaryTexture, 0, 0, rtToUse.getWidth(), rtToUse.getHeight());
			}
			_game.batch.end();
		}
		// Unbind the FBO
		rtToUse.end();

		_game.batch.setShader(null);

		Gdx.gl30.glEnable(GL30.GL_BLEND);
	}

	// Each update shader declares only the uniforms it actually uses, so set only those — ShaderProgram.pedantic
	// will otherwise throw on any unknown name.
	private void applyUpdateShaderUniforms(ShaderProgram shaderToUse) {
		if (shaderToUse == initPositions) {
			shaderToUse.setUniformf("spawnWidth", spawnWidth);
			shaderToUse.setUniformf("spawnHeight", spawnHeight);
			return;
		}

		if (shaderToUse == initVelocities || shaderToUse == copyTexture) {
			return;
		}

		// Both update passes read the canonical state textures.
		positionMapUpdate = positionTexture;
		Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE1);
		positionMapUpdate.bind();
		shaderToUse.setUniformi("positionMap", 1);

		velocityMapUpdate = velocityTexture;
		Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE2);
		velocityMapUpdate.bind();
		shaderToUse.setUniformi("velocityMap", 2);

		shaderToUse.setUniformf("deltaTime", deltaTimeForShader * timeScale);

		if (shaderToUse == updateVelocities) {
			shaderToUse.setUniformf("spawnWidth", spawnWidth);
			shaderToUse.setUniformf("spawnHeight", spawnHeight);
			shaderToUse.setUniformf("attractorPos", attractorPosition);
			shaderToUse.setUniformf("attractorForce", currentAttractorForce);
			shaderToUse.setUniformf("attractorMaxDistance", ATTRACTOR_MAX_DISTANCE);
			shaderToUse.setUniformf("dragPercentage", dragPercentage);

			shaderToUse.setUniformi("circleColliderCount", circleColliderCount);
			shaderToUse.setUniform4fv("circleColliders", circleColliderData, 0, circleColliderData.length);

			shaderToUse.setUniformi("rectColliderCount", rectColliderCount);
			shaderToUse.setUniform4fv("rectColliders", rectColliderData, 0, rectColliderData.length);

			shaderToUse.setUniformi("pointForceCount", pointForceCount);
			shaderToUse.setUniform4fv("pointForces", pointForceData, 0, pointForceData.length);
		}
	}
}
