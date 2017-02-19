package com.austinauts.libgdx.modules.particlesgalore.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.screens.BaseMainMenuScreen;

public class MainMenuScreen extends BaseMainMenuScreen {
	private final static String WELCOME_TEXT = "Particles Galore";
	private final static String CLICK_TO_BEGIN_TEXT = "Touch to begin";

	public MainMenuScreen(final AustinautsGame game) {
		super(game, WELCOME_TEXT, CLICK_TO_BEGIN_TEXT);
	}

	@Override
	protected void switchScreen() {
		_game.setScreen(new GameScreen(_game));
	}
}
