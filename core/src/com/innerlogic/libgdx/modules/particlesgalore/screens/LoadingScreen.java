package com.innerlogic.libgdx.modules.particlesgalore.screens;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.innerlogic.libgdx.InnerLogicGame;
import com.innerlogic.libgdx.common.screens.BaseLoadingScreen;

public class LoadingScreen extends BaseLoadingScreen {
	public LoadingScreen(final InnerLogicGame game) {
		super(game);

		// Tell the asset manager what needs to be loaded
		_game.assetManager.load(InnerLogicGame.TEXTURE_PARTICLE, Texture.class);
		_game.assetManager.load(InnerLogicGame.MUSIC_TRACK, Music.class);
	}

	@Override
	public void switchScreen() {
		_game.setScreen(new MainMenuScreen(_game));
	}
}

