package com.lfp.rsocket.ipc.metadata;

import java.util.concurrent.CopyOnWriteArrayList;

import reactor.core.Disposable;
import reactor.core.Disposables;

public class DisposableAddList<X> extends CopyOnWriteArrayList<X> {

	private static final long serialVersionUID = 1L;

	public static <XX> DisposableAddList<XX> create() {
		return new DisposableAddList<XX>();
	}

	public Disposable disposableAdd(X value) {
		Disposable disposable = Disposables.composite(() -> {
			this.removeIf(v -> v == value);
		});
		this.add(value);
		return disposable;
	}
}
