package com.innerlogic.libgdx.modules.fallthru.screens;

import com.innerlogic.libgdx.InnerLogicGame;
import com.innerlogic.libgdx.common.screens.BaseMainMenuScreen;

public class MainMenuScreen extends BaseMainMenuScreen {
	private final static String WELCOME_TEXT = "fallthru";
	private final static String CLICK_TO_BEGIN_TEXT = "Touch to begin";

	public MainMenuScreen(final InnerLogicGame game) {
		super(game, WELCOME_TEXT, CLICK_TO_BEGIN_TEXT);
	}

	@Override
	protected void switchScreen() {
		_game.setScreen(new GameScreen(_game));
	}
}
