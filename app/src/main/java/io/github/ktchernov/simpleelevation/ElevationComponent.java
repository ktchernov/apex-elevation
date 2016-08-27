package io.github.ktchernov.simpleelevation;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = ElevationModule.class)
public interface ElevationComponent {
	void inject(ElevationActivity elevationActivity);
}
