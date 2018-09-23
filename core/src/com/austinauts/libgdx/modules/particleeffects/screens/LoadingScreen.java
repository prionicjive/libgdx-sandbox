package com.austinauts.libgdx.modules.particleeffects.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.screens.BaseLoadingScreen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

public class LoadingScreen extends BaseLoadingScreen {
	public LoadingScreen(final AustinautsGame game) {
		super(game);

		// Tell the asset manager what needs to be loaded
		_game.assetManager.load(AustinautsGame.PARTICLE_EFFECT_DEFAULT, ParticleEffect.class);
		_game.assetManager.load(AustinautsGame.TEXTURE_PARTICLE, Texture.class);
	}

	@Override
	public void switchScreen() {
		_game.setScreen(new MainMenuScreen(_game));
	}
}
