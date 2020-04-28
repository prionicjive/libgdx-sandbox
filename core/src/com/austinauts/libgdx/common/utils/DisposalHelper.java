package com.austinauts.libgdx.common.utils;

import com.badlogic.gdx.utils.Disposable;

public class DisposalHelper {
	public static void disposeCollection(Iterable<? extends Disposable> valuesToDispose) {
		for (Disposable valueToDispose : valuesToDispose) {
			valueToDispose.dispose();
		}
	}
}
