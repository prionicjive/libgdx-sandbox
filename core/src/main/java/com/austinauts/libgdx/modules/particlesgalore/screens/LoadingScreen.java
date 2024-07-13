package com.austinauts.libgdx.modules.particlesgalore.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.austinauts.libgdx.common.screens.BaseLoadingScreen;

public class LoadingScreen extends BaseLoadingScreen {
	public LoadingScreen(final AustinautsGame game) {
		super(game);

		// Tell the asset manager what needs to be loaded
		_game.assetManager.load(AustinautsGame.TEXTURE_PARTICLE, Texture.class);
		//_game.assetManager.load(AustinautsGame.MUSIC_TRACK, Music.class);
	}

	@Override
	public void switchScreen() {
		_game.setScreen(new MainMenuScreen(_game));
	}
}

