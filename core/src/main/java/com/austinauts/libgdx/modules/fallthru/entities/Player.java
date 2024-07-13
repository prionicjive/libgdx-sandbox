package com.austinauts.libgdx.modules.fallthru.entities;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.entities.SpriteEntity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

public class Player extends SpriteEntity {
	// ---------------------------
	// Private members
	// ---------------------------
	private AustinautsGame _game;
	public boolean IsFallingThru;
	public Rectangle gapFallingThru;

	// Animation
	// TODO Can this be broken out better?
	Animation<TextureRegion> animation;
	Texture spriteSheet;
	TextureRegion[] animationFrames;
	private final int FRAME_COLS = 8;
	private final int FRAME_ROWS = 1;
	private final float animationLengthInSec = .25f;
	private float stateTime;
	private final float animationFPS = animationLengthInSec / (FRAME_COLS * FRAME_ROWS); // NUM_FRAMES / Millisecond to play whole animation

	public Player(AustinautsGame game) {
		super(game.assetManager.get(AustinautsGame.TEXTURE_PLAYER, Texture.class));

		_game = game;
		IsFallingThru = false;
		gapFallingThru = null;

		// Perform animation setup
		spriteSheet = game.assetManager.get(AustinautsGame.TEXTURE_PLAYERANMIATION, Texture.class);
		TextureRegion[][] tmp = TextureRegion.split(spriteSheet, spriteSheet.getWidth() / FRAME_COLS, spriteSheet.getHeight() / FRAME_ROWS);
		animationFrames = new TextureRegion[FRAME_COLS * FRAME_ROWS];
		int index = 0;
		for (int i = 0; i < FRAME_ROWS; i++) {
			for (int j = 0; j < FRAME_COLS; j++) {
				animationFrames[index++] = tmp[i][j];
			}
		}
		animation = new Animation<>(animationFPS, animationFrames);
		stateTime = 0f;
	}

	@Override
	public void draw(Batch batch) {
		stateTime += Gdx.graphics.getDeltaTime();
		setRegion(animation.getKeyFrame(stateTime, true));
		super.draw(batch);
	}
}
