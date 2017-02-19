package com.innerlogic.libgdx.modules.particlesgalore.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.innerlogic.libgdx.InnerLogicGame;
import com.innerlogic.libgdx.common.utils.ShaderHelper;
import com.innerlogic.libgdx.common.utils.UserFloatFrameBuffer;
import com.innerlogic.libgdx.modules.particlesgalore.particles.ParticleSystem;

public class GameScreen extends ScreenAdapter {
	// Reference to main game object
	private final InnerLogicGame _game;

	private UserFloatFrameBuffer accumulationFBO;
	private TextureRegion accumulationFboTextureRegion;

	// TODO See how to better set up rendering a simple quad with the default shader
	private ShaderProgram defaultShader;
	private Mesh fullscreenQuad;

	// Music and other assets
	private Music musicTrack;

	// -----------------------
	// Particles
	// -----------------------
	private ParticleSystem particleSystem;

	public GameScreen(final InnerLogicGame game) {
		_game = game;

		// TODO See how to better set up rendering a simple quad with the default shader
		defaultShader = ShaderHelper.createDefaultShader();
		fullscreenQuad = ShaderHelper.createFullScreenQuad(_game.masterWorldWidth, _game.masterWorldHeight, false, 0f, 0f, 0f, 0.075f); // TODO Make tweakable with keypresses

		// -------------------------------------
		// Set up shader and particle related things
		// -------------------------------------
		particleSystem = new ParticleSystem(game);

		// Set up the accumulation FBO
		accumulationFBO = new UserFloatFrameBuffer(_game.masterWorldWidth, _game.masterWorldHeight, false);
		accumulationFboTextureRegion = new TextureRegion(accumulationFBO.getColorBufferTexture());
		accumulationFboTextureRegion.flip(false, true); // Needed to display correctly if rendering to screen

		// Get that music going!
		// Set up music to be played
//		musicTrack = _game.assetManager.get(InnerLogicGame.MUSIC_TRACK);
//		musicTrack.setVolume(0.5f);
//		musicTrack.setLooping(true);
//		musicTrack.play();
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

		// Update and simulate particles
		particleSystem.updateAndSimulate(delta);

		// Resize the camera and the batch for the particles, which should be full screen
		_game.camera.setToOrtho(false, _game.masterWorldWidth, _game.masterWorldHeight);

		// Bind the accumulation FBO for rendering
		accumulationFBO.begin();
		{
			// Ensure texturing
			Gdx.graphics.getGL30().glEnable(GL30.GL_TEXTURE_2D);

			// Ensure blending of the right kind
			Gdx.graphics.getGL30().glEnable(GL30.GL_BLEND);
			Gdx.graphics.getGL30().glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);

			// Render the transparent "helper" full screen quad to help fade what already exists on the accumulation buffer
			defaultShader.begin();
			{
				defaultShader.setUniformMatrix("u_projTrans", _game.camera.combined);
				fullscreenQuad.render(defaultShader, GL30.GL_TRIANGLES);
			}
			defaultShader.end();

			// Render the latest state of the particle system to the accumulation FBO
			particleSystem.renderParticles();
		}
		accumulationFBO.end();

		// Reapply the FitViewport now that we are back to rendering our batches are expected
		_game.viewport.apply();

		// Set the sprite batch to use the camera's combined projection/view matrix
		_game.batch.setProjectionMatrix(_game.camera.combined);

		// Properly clear out the default rendering target/frame buffer before drawing
		Gdx.gl30.glClearColor(0.0f, 0.0f, 0.0f, 1f);
		Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

		_game.batch.begin();
		{
			// Use the FLIPPED texture region when rendering to the screen
			_game.batch.draw(accumulationFboTextureRegion, 0, 0);
		}
		_game.batch.end();
	}
}
