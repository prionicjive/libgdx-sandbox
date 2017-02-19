package com.innerlogic.libgdx.modules.quadtree.screens;

import com.badlogic.gdx.graphics.Texture;
import com.innerlogic.libgdx.InnerLogicGame;
import com.innerlogic.libgdx.common.screens.BaseLoadingScreen;

public class LoadingScreen extends BaseLoadingScreen {
	public LoadingScreen(final InnerLogicGame game) {
		super(game);

		// Tell the asset manager what needs to be loaded
		_game.assetManager.load(InnerLogicGame.TEXTURE_BLOCK, Texture.class);
	}

	@Override
	public void switchScreen() {
		_game.setScreen(new MainMenuScreen(_game));
	}
}

