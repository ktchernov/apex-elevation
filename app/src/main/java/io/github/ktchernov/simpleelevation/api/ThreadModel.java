package io.github.ktchernov.simpleelevation.api;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

@Singleton public class ThreadModel {

	@Inject ThreadModel() {
	}

	public <T> Observable.Transformer<T, T> transformer() {
		return observable -> observable
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread());
	}
}
