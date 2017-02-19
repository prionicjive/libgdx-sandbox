package com.innerlogic.libgdx.modules.tileengine.screens;

import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.innerlogic.libgdx.InnerLogicGame;
import com.innerlogic.libgdx.common.screens.BaseLoadingScreen;

public class LoadingScreen extends BaseLoadingScreen {
	public LoadingScreen(final InnerLogicGame game) {
		super(game);

		// Tell the asset manager what needs to be loaded
		// TODO Load textures and other assets

		// Specifically set the loader for a Tiled map
		_game.assetManager.setLoader(TiledMap.class, new TmxMapLoader(new InternalFileHandleResolver()));
		_game.assetManager.load(InnerLogicGame.TILEMAP_SAMPLE_MAP, TiledMap.class);
		//_game.assetManager.load(InnerLogicGame.MAP_SAMPLE, Texture.class);
		//        _game.assetManager.load(InnerLogicGame.TEXTURE_PLAYER, Texture.class);
		//        _game.assetManager.load(InnerLogicGame.TEXTURE_PLAYERANMIATION, Texture.class);
	}

	@Override
	public void switchScreen() {
		_game.setScreen(new MainMenuScreen(_game));
	}
}
