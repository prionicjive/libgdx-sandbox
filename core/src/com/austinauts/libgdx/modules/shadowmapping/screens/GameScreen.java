package com.austinauts.libgdx.modules.shadowmapping.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.utils.UserFloatFrameBuffer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.austinauts.libgdx.common.entities.SpriteEntity;
import com.austinauts.libgdx.common.utils.ShaderHelper;
import com.austinauts.libgdx.common.utils.logging.PeriodicLogger;

public class GameScreen extends ScreenAdapter {
	// Reference to main game object
	private final AustinautsGame _game;

	// --------------------
	// Game Entities
	// --------------------
	private Array<SpriteEntity> blockArray;
	private final int NUM_BLOCKS = 40;

	// --------------------
	// Scratch variables
	// --------------------
	private int screenWidth; // TODO Consider moving elsewhere
	private int screenHeight; // TODO Consider moving elsewhere

	// ------------------------
	// Periodic logger
	// ------------------------
	private PeriodicLogger periodicLogger;

	// ------------------------------------
	// Shader related
	// Adapted from https://github.com/mattdesl/lwjgl-basics/wiki/2D-Pixel-Perfect-Shadows
	// ------------------------------------

	// TODO Rename to "light radius" or "light diameter"
	// The number of rays emitted for a "light", as well as their length. This is used in a 360 degree fashion, so the higher, the higher the precision
	// but higher fill rate as well. Needs to be power of 2
	private int lightSize = 256;

	private UserFloatFrameBuffer occludersFBO;
	private UserFloatFrameBuffer shadowMapFBO;

	ShaderProgram shadowMapShader, shadowRenderShader;

	Array<Light> lights;

	boolean additive = true;
	boolean softShadows = true;

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// -------------------------------------
		// Set up scratch variables
		// -------------------------------------
		screenWidth = Gdx.graphics.getWidth();
		screenHeight = Gdx.graphics.getHeight();

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------
		blockArray = new Array<>(true, NUM_BLOCKS);
		for (int i = 0; i < NUM_BLOCKS; i++) {
			blockArray.add(generateValidBlock());
		}

		// Set up the periodic logger and its logging actions
		periodicLogger = new PeriodicLogger();
		periodicLogger.addLoggingAction(() -> Gdx.app.log("FPSLogger", "fps: " + Gdx.graphics.getFramesPerSecond()));

		// -------------------------------------
		// Set up shader related things
		// -------------------------------------

		// Set up Occluders FBO and texture that'll be generated from FBO
		occludersFBO = new UserFloatFrameBuffer(lightSize, lightSize, false);
		occludersFBO.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

		// Set up 1D Shadow map FBO and texture that'll be generated from FBO
		shadowMapFBO = new UserFloatFrameBuffer(lightSize, 1, false);
		occludersFBO.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

		// Ensure that not everything about a shader needs to be configured
		ShaderProgram.pedantic = false;

		// Get the vertex passthrough shader
		final String VERT_SRC = Gdx.files.internal("shaders/2dshadowmapping/pass.vert").readString();

		// Shader that uses the  occluders texture to generate a 1D shadow map texture
		shadowMapShader = ShaderHelper.createShader(VERT_SRC, Gdx.files.internal("shaders/2dshadowmapping/shadowMap.frag").readString());

		// Shader that samples the 1D shadow map texture to create shadows
		shadowRenderShader = ShaderHelper.createShader(VERT_SRC, Gdx.files.internal("shaders/2dshadowmapping/shadowRender.frag").readString());

		// Set up lights
		lights = new Array<>(true, 20);

		Gdx.input.setInputProcessor(new InputAdapter() {

			public boolean touchDown(int x, int y, int pointer, int button) {
				float mx = x;
				float my = Gdx.graphics.getHeight() - y;

				lights.add(new Light(mx, my, randomColor()));

				return true;
			}

			public boolean keyDown(int key) {
				if (key == Input.Keys.SPACE) {
					clearLights();
					return true;
				}
				else if (key == Input.Keys.A) {
					additive = !additive;
					return true;
				}
				else if (key == Input.Keys.S) {
					softShadows = !softShadows;
					return true;
				}

				return false;
			}
		});

		clearLights();
	}

	void clearLights() {
		lights.clear();

		// TODO Properly pull from screen width / height
		lights.add(new Light(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY(), Color.WHITE));
	}

	static Color randomColor() {
		float intensity = MathUtils.random() * 0.5f + 0.5f;

		// TODO Improve randomness
		return new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), intensity);
	}

	// TODO Probably can be added elsewhere
	private SpriteEntity generateValidBlock() {
		SpriteEntity block = new SpriteEntity(_game.assetManager.get(AustinautsGame.TEXTURE_BLOCK, Texture.class));
		block.setSize(10, 10);
		block.setPosition(MathUtils.random(0, screenWidth - block.getWidth()), MathUtils.random(0, screenHeight - block.getHeight()));
		block.setVelocity(MathUtils.random(-1.0f, 1.0f), MathUtils.random(-1.0f, 1.0f));
		block.setSpeed(MathUtils.random(20, 200));

		return block;
	}

	@Override
	public void render(float delta) {
		// TODO Flesh out game loop as needed

		// Outline of game loop:
		//
		// 1) Update camera
		// 2) Handle input
		// 3) Update entities
		// 4) Process collision / physics
		// 5) Render

		_game.camera.update();

		updateEntities(delta);

		// Clear the backbuffer (Deep, dark gray)
		Gdx.gl30.glClearColor(.1f, .1f, .1f, 1f);
		Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

		// Set additive if needed
		if (additive) {
			_game.batch.setBlendFunction(GL30.GL_SRC_ALPHA, GL30.GL_ONE);
		}

		for (int i = 0; i < lights.size; i++) {
			// Update the last light's pos to be where the mouse is
			Light currLight = lights.get(i);

			if (i == lights.size - 1) {
				currLight.pos.x = Gdx.input.getX();
				currLight.pos.y = Gdx.graphics.getHeight() - Gdx.input.getY();
			}

			renderLight(currLight);
		}

		// Reset color and shader
		_game.batch.setColor(Color.WHITE);
		_game.batch.setShader(null);

		// Lastly, restore the prior blending mode if additive blending was used
		if (additive) {
			_game.batch.setBlendFunction(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
		}

		//        _game.batch.begin();
		//
		//        for(SpriteEntity currBlock : blockArray)
		//        {
		//            currBlock.draw(_game.batch);
		//        }
		//
		//        _game.batch.end();
	}

	// ------------------------
	// Private methods
	// ------------------------

	private void updateEntities(float delta) {

		for (SpriteEntity currBlock : blockArray) {
			// Update the velocity
			float updatedX = currBlock.getX() + (currBlock.getVelocity().x * currBlock.getSpeed() * delta);
			float updatedY = currBlock.getY() + (currBlock.getVelocity().y * currBlock.getSpeed() * delta);
			currBlock.setPosition(updatedX, updatedY);

			// Keep the entity within screen space
			if (currBlock.getX() < 0) {
				currBlock.setX(0);

				// Negate x component of velocity
				currBlock.negateVelocityX();
			}
			else if (currBlock.getX() > screenWidth - currBlock.getWidth()) {
				currBlock.setX(screenWidth - currBlock.getWidth());

				// Negate x component of velocity
				currBlock.negateVelocityX();
			}

			if (currBlock.getY() < 0) {
				currBlock.setY(0);

				// Negate y component of velocity
				currBlock.negateVelocityY();
			}
			else if (currBlock.getY() > screenHeight - currBlock.getHeight()) {
				currBlock.setY(screenHeight - currBlock.getHeight());

				// Negate y component of velocity
				currBlock.negateVelocityY();
			}
		}

		// We aren't comparing the blocks against each other, so nothing more to do
	}

	private void renderLight(Light lightToRender) {
		// STEP 1: Render the occluders to texture via FBO

		// Bind the occludersTexture FBO
		occludersFBO.begin();
		{
			// Clear the FBO fully
			Gdx.gl30.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

			// Set the camera to the size of our FBO
			_game.camera.setToOrtho(false, occludersFBO.getWidth(), occludersFBO.getHeight());

			// Translate camera so that light is in the center
			_game.camera.translate(lightToRender.pos.x - lightSize / 2f, lightToRender.pos.y - lightSize / 2f);

			// Make sure the camera is up to date
			_game.camera.update();

			// Set up our batch for the occluders pass
			_game.batch.setProjectionMatrix(_game.camera.combined);
			_game.batch.setShader(null); // Use default shader

			_game.batch.begin();
			{
				// Draw all the blocks for determining the occlusion map
				for (SpriteEntity currBlock : blockArray) {
					// Render our game entities
					currBlock.draw(_game.batch);
				}
			}
			// End the batch before unbinding the FBO
			_game.batch.end();
		}
		// Unbind the FBO
		occludersFBO.end();

		// STEP 2: Build a 1D shadow map texture via FBO with the occluders as the bound texture

		// Bind the shadow map
		shadowMapFBO.begin();
		{
			// Clear the FBO fully
			Gdx.gl30.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

			// Set our shadow map shader
			_game.batch.setShader(shadowMapShader);
			_game.batch.begin();
			{
				shadowMapShader.setUniformf("lightCastLength", lightSize);

				// Reset our camera to the FBO size
				_game.camera.setToOrtho(false, shadowMapFBO.getWidth(), shadowMapFBO.getHeight());
				_game.batch.setProjectionMatrix(_game.camera.combined);

				// Draw the capture Occluders texture to our 1D shadow map FBO
				_game.batch.draw(occludersFBO.getColorBufferTexture(), 0, 0, lightSize, shadowMapFBO.getHeight());
			}
			// Flush batch
			_game.batch.end();
		}
		// Unbind shadow map FBO
		shadowMapFBO.end();

		// STEP 3: Render the blurred shadows using the 1D shadow map texture.

		// Reset projection matrix to screen
		_game.camera.setToOrtho(false);
		_game.batch.setProjectionMatrix(_game.camera.combined);

		// Set the shader which will actually render the shadows and light
		_game.batch.setShader(shadowRenderShader);
		_game.batch.begin();
		{
			shadowRenderShader.setUniformf("lightCastLength", lightSize);
			shadowRenderShader.setUniformf("softShadows", softShadows ? 1f : 0f);

			// Set the color of the light
			_game.batch.setColor(lightToRender.color);

			// draw centered on light position
			_game.batch.draw(shadowMapFBO.getColorBufferTexture(), lightToRender.pos.x - lightSize / 2, lightToRender.pos.y - lightSize / 2, lightSize, lightSize);
		}
		// Flush the batch before swapping shaders
		_game.batch.end();
	}

	private class Light {
		Vector2 pos;
		Color color;

		public Light(float x, float y, Color color) {
			pos = new Vector2(x, y);
			this.color = color;
		}
	}
}


