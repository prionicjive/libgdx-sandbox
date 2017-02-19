package com.innerlogic.libgdx.common.utils.logging;

import com.badlogic.gdx.utils.Array;

public class PeriodicLogger {
	private long startTime;
	private long intervalInSeconds;
	private Array<LoggingAction> loggingActions;

	public PeriodicLogger() {
		this(1);
	}

	public PeriodicLogger(long interval) {
		// Default to 1 second if given something lower
		if (interval < 0) {
			interval = 1;
		}

		intervalInSeconds = interval;
		startTime = System.currentTimeMillis();

		loggingActions = new Array<>(true, 10);
	}

	public void log() {
		if (System.currentTimeMillis() - startTime > (intervalInSeconds * 1000L)) {
			// Reset the start time
			startTime = System.currentTimeMillis();

			// Perform all logging actions
			for (LoggingAction currAction : loggingActions) {
				currAction.doAction();
			}
		}
	}

	public void addLoggingAction(LoggingAction action) {
		loggingActions.add(action);
	}

	public void clearLoggingActions() {
		loggingActions.clear();
	}
}
