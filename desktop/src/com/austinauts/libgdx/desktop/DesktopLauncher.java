package com.austinauts.libgdx.desktop;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {
	public static void main(String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();

		// Set up the config
		config.setTitle("LibGDX Sandbox");
		// TODO Consider the benefits of using GLES 3.0 on the desktop
		config.useOpenGL3(true, 3, 3);

		// 1280x720 is minimum supported resolution
		// TODO Share this as the viewport info
		config.setWindowedMode(1280, 720);
		config.setResizable(true);

		new Lwjgl3Application(new AustinautsGame(), config);
	}
}
