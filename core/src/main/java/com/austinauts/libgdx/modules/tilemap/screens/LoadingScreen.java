package com.austinauts.libgdx.modules.tilemap.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.austinauts.libgdx.common.screens.BaseLoadingScreen;

public class LoadingScreen extends BaseLoadingScreen {
	public LoadingScreen(final AustinautsGame game) {
		super(game);

		// Tell the asset manager what needs to be loaded
		// TODO Load textures and other assets
		_game.assetManager.load(AustinautsGame.TILEMAP_SAMPLE_PALETTE, Texture.class);

		// Specifically set the loader for a Tiled map
		_game.assetManager.setLoader(TiledMap.class, new TmxMapLoader(new InternalFileHandleResolver()));
		_game.assetManager.load(AustinautsGame.TILEMAP_SAMPLE_MAP, TiledMap.class);
		//_game.assetManager.load(AustinautsGame.MAP_SAMPLE, Texture.class);
		//        _game.assetManager.load(AustinautsGame.TEXTURE_PLAYER, Texture.class);
		//        _game.assetManager.load(AustinautsGame.TEXTURE_PLAYERANMIATION, Texture.class);
	}

	@Override
	public void switchScreen() {
		_game.setScreen(new MainMenuScreen(_game));
	}
}
