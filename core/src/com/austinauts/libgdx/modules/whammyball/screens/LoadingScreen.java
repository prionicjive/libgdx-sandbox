package com.austinauts.libgdx.modules.whammyball.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.screens.BaseLoadingScreen;
import com.badlogic.gdx.graphics.Texture;

public class LoadingScreen extends BaseLoadingScreen {
	public LoadingScreen(final AustinautsGame game) {
		super(game);

		// Tell the asset manager what needs to be loaded
		_game.assetManager.load(AustinautsGame.TEXTURE_TILE, Texture.class);
		_game.assetManager.load(AustinautsGame.TEXTURE_PLAYER, Texture.class);
	}

	@Override
	public void switchScreen() {
		_game.setScreen(new MainMenuScreen(_game));
	}
}

