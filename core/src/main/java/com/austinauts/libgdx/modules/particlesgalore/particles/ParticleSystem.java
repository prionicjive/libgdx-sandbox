package com.austinauts.libgdx.modules.particlesgalore.particles;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.utils.DisposalHelper;
import com.austinauts.libgdx.common.utils.ShaderHelper;
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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;

import java.util.Arrays;

public class ParticleSystem implements Disposable {
	// Reference to the game
	private AustinautsGame _game;

	// Render targets. State is kept on the GPU; positionRT/velocityRT are the canonical "current state".
	// temporaryRT is a scratchpad used to ping-pong around GL's "can't read+write the same texture" rule.
	private FloatFrameBuffer positionRT;
	private FloatFrameBuffer velocityRT;
	private FloatFrameBuffer temporaryRT;

	// Per-particle vertex buffer — each vertex's a_position.xy is the particle's slot UV into the state textures.
	private Mesh particlesVB;

	// Fullscreen quad used as the GPGPU trigger for every update pass.
	private Mesh updatePassQuad;

	// TODO Externalize this all and make it reloadable in game via developer command

	// Dimension for render targets. Maximum particle count = SQRT_MAX_PARTICLES^2. NOTE: must be a power of 2.
	private final static int SQRT_MAX_PARTICLES = 128;
	private final static int MAX_PARTICLES = SQRT_MAX_PARTICLES * SQRT_MAX_PARTICLES;
	private final static float POINT_SIZE = 2;

	// Position attribute - (x, y)
	public static final int POSITION_COMPONENTS = 2;
	// Color attribute - (r, g, b, a)
	public static final int COLOR_COMPONENTS = 4;
	// Total number of components for all attributes
	public static final int NUM_COMPONENTS = POSITION_COMPONENTS + COLOR_COMPONENTS;

	// Sim state
	private Vector3 attractorPosition;
	private static final float idealAttractorForce = 1500.0f;
	private float currentAttractorForce = idealAttractorForce;
	private static final float ATTRACTOR_MAX_DISTANCE = 250.0f;
	private static final float dragPercentage = 0.05f;
	private float deltaTimeForShader = 0.0f;

	// Collider / point-force storage. Layout matches the uniform array element size in updateVelocities.frag
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
	private float timeScale = 1.0f;
	private static final float MIN_TIME_SCALE = 0f;
	private static final float MAX_TIME_SCALE = 10f;
	private static final float TIME_SCALE_INCREMENT = 0.1f;

	// Render-pass inputs
	private Texture psTexture;
	private float spawnWidth;
	private float spawnHeight;

	// True once the initial seed shaders have populated positionRT/velocityRT.
	private boolean areDataTexutresInitialized = false;

	// Update shaders
	private ShaderProgram initPositions, initVelocities, updatePositions, updateVelocities, copyTexture;

	// Render shader
	private ShaderProgram particleRender;

	// Cached handles to the underlying textures of each FBO (set once in init; FBO owns their lifetime).
	private Texture temporaryTexture;
	private Texture positionTexture;
	private Texture velocityTexture;

	// Read-only RGBA32F texture of per-particle random values. Used by the init shaders for seeding.
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
		Gdx.gl30.glEnable(GL30.GL_BLEND);
		Gdx.gl30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE); // TODO Better determine blend function or make toggleable
		Gdx.gl30.glEnable(GL30.GL_VERTEX_PROGRAM_POINT_SIZE);
		Gdx.gl30.glEnable(0x8861); // GL_POINT_SPRITE — LibGDX's GL30 bindings don't expose this constant.

		particleRender.begin();
		{
			particleRender.setUniformMatrix("u_projTrans", _game.camera.combined);
			particleRender.setUniformf("pointSize", POINT_SIZE);

			positionRT.getColorBufferTexture().bind(0);
			particleRender.setUniformi("positionMap", 0);

			psTexture.bind(1);
			particleRender.setUniformi("pointSpriteTex", 1);

			particlesVB.render(particleRender, GL30.GL_POINTS, 0, MAX_PARTICLES);
		}
		particleRender.end();

		Gdx.gl30.glDisable(GL30.GL_VERTEX_PROGRAM_POINT_SIZE);
		Gdx.gl.glDisable(0x8861);
		Gdx.gl30.glDisable(GL30.GL_BLEND);
		Gdx.gl30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
		Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0);
	}

	@Override
	public void dispose() {
		DisposalHelper.disposeCollection(Arrays.asList(
			positionRT, velocityRT, temporaryRT,
			particlesVB, updatePassQuad,
			randomTexture,
			initPositions, initVelocities, updatePositions, updateVelocities, copyTexture,
			particleRender));
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
					timeScale = Math.min(MAX_TIME_SCALE, timeScale + TIME_SCALE_INCREMENT);
					return true;
				}
				else if (key == Input.Keys.MINUS) {
					timeScale = Math.max(MIN_TIME_SCALE, timeScale - TIME_SCALE_INCREMENT);
					return true;
				}
				return false;
			}
		});
	}

	private void initializeShaders() {
		spawnWidth = _game.virtualScreenSize.width;
		spawnHeight = _game.virtualScreenSize.height;

		// Every shader we compile here declares all the uniforms it's set with. Run pedantic so any future
		// mismatch surfaces at the setUniform call that introduced it.
		ShaderProgram.pedantic = true;

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

		updatePassQuad = ShaderHelper.createFullScreenQuad(SQRT_MAX_PARTICLES, SQRT_MAX_PARTICLES);

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
				// Slot UV into the state textures for this particle.
				position.x = (float) i / (float) SQRT_MAX_PARTICLES;
				position.y = (float) j / (float) SQRT_MAX_PARTICLES;

				particlesComponentsArray.add(position.x);
				particlesComponentsArray.add(position.y);

				color.set(0.1f + MathUtils.random() * 0.3f, 0.3f + MathUtils.random() * 0.5f, 0.3f + MathUtils.random() * 0.7f, 1.0f);

				particlesComponentsArray.add(color.r);
				particlesComponentsArray.add(color.g);
				particlesComponentsArray.add(color.b);
				particlesComponentsArray.add(color.a);

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

		FloatTextureData userFloatTextureData = new FloatTextureData(SQRT_MAX_PARTICLES, SQRT_MAX_PARTICLES, GL30.GL_RGBA32F, GL30.GL_RGBA, GL30.GL_FLOAT, false);
		userFloatTextureData.prepare();
		BufferUtils.copy(randomValuesComponentsArray.items, 0, userFloatTextureData.getBuffer(), MAX_PARTICLES * 4);
		randomTexture = new Texture(userFloatTextureData);
		randomTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
		randomTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
	}

	private void initializeAttractors() {
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
		deltaTimeForShader = delta;

		float addedAttractorForce = Gdx.input.isTouched() ? 3000.0f : 0f;
		currentAttractorForce = idealAttractorForce + addedAttractorForce;

		// Screen-space cursor position, unprojected into the same world frame as the particles.
		attractorPosition.x = Gdx.input.getX();
		attractorPosition.y = Gdx.input.getY();
		_game.camera.unproject(attractorPosition, _game.viewport.getScreenX(), _game.viewport.getScreenY(), _game.viewport.getScreenWidth(), _game.viewport.getScreenHeight());
	}

	private void simulateParticles() {
		if (!areDataTexutresInitialized) {
			executeUpdateTechnique(initVelocities, velocityRT);
			executeUpdateTechnique(initPositions, positionRT);
			areDataTexutresInitialized = true;
		}

		executeUpdateTechnique(updateVelocities, velocityRT);
		executeUpdateTechnique(updatePositions, positionRT);
	}

	// Two-pass ping-pong:
	//   1) Render `shader` into temporaryRT, reading state from positionTexture/velocityTexture as needed.
	//   2) Copy temporaryRT into rtToUse so subsequent passes see the updated state.
	private void executeUpdateTechnique(ShaderProgram shader, FrameBuffer rtToUse) {
		Gdx.gl30.glDisable(GL30.GL_BLEND);

		// Pass 1: compute into scratchpad.
		temporaryRT.begin();
		{
			Gdx.gl30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
			Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

			_game.camera.setToOrtho(false, temporaryRT.getWidth(), temporaryRT.getHeight());

			shader.bind();
			shader.setUniformMatrix("u_projTrans", _game.camera.combined);
			applyUpdateShaderUniforms(shader);

			updatePassQuad.render(shader, GL30.GL_TRIANGLES);
		}
		temporaryRT.end();

		// Pass 2: blit scratchpad into the canonical state texture.
		rtToUse.begin();
		{
			_game.camera.setToOrtho(false, rtToUse.getWidth(), rtToUse.getHeight());

			copyTexture.bind();
			copyTexture.setUniformMatrix("u_projTrans", _game.camera.combined);
			temporaryTexture.bind(0);
			copyTexture.setUniformi("u_texture", 0);

			updatePassQuad.render(copyTexture, GL30.GL_TRIANGLES);
		}
		rtToUse.end();

		Gdx.gl30.glEnable(GL30.GL_BLEND);
	}

	// Each update shader only declares the uniforms it actually reads, so we set exactly that set.
	// Under ShaderProgram.pedantic any mismatch will throw here — which is the point.
	private void applyUpdateShaderUniforms(ShaderProgram shader) {
		if (shader == initPositions) {
			bindRandomTexture(shader);
			shader.setUniformf("spawnWidth", spawnWidth);
			shader.setUniformf("spawnHeight", spawnHeight);
			return;
		}

		if (shader == initVelocities) {
			bindRandomTexture(shader);
			return;
		}

		// updatePositions + updateVelocities both read the canonical state textures and deltaTime.
		positionTexture.bind(1);
		shader.setUniformi("positionMap", 1);
		velocityTexture.bind(2);
		shader.setUniformi("velocityMap", 2);
		shader.setUniformf("deltaTime", deltaTimeForShader * timeScale);

		if (shader == updateVelocities) {
			shader.setUniformf("spawnWidth", spawnWidth);
			shader.setUniformf("spawnHeight", spawnHeight);
			shader.setUniformf("attractorPos", attractorPosition);
			shader.setUniformf("attractorForce", currentAttractorForce);
			shader.setUniformf("attractorMaxDistance", ATTRACTOR_MAX_DISTANCE);
			shader.setUniformf("dragPercentage", dragPercentage);

			shader.setUniformi("circleColliderCount", circleColliderCount);
			shader.setUniform4fv("circleColliders", circleColliderData, 0, circleColliderData.length);
			shader.setUniformi("rectColliderCount", rectColliderCount);
			shader.setUniform4fv("rectColliders", rectColliderData, 0, rectColliderData.length);
			shader.setUniformi("pointForceCount", pointForceCount);
			shader.setUniform4fv("pointForces", pointForceData, 0, pointForceData.length);
		}
	}

	private void bindRandomTexture(ShaderProgram shader) {
		randomTexture.bind(0);
		shader.setUniformi("u_texture", 0);
	}
}
