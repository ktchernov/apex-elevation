package io.github.ktchernov.simpleelevation;

import java.util.Locale;

class UnitLocale {
	private static final double FEET_TO_METRES = 0.3048;
	static UnitLocale IMPERIAL = new UnitLocale(true);
	private static UnitLocale METRIC = new UnitLocale(false);

	private final boolean isImperial;

	static UnitLocale getDefault() {
		return getFrom(Locale.getDefault());
	}

	private static UnitLocale getFrom(Locale locale) {
		String countryCode = locale.getCountry();
		if ("US".equals(countryCode)) return IMPERIAL; // USA
		if ("LR".equals(countryCode)) return IMPERIAL; // liberia
		if ("MM".equals(countryCode)) return IMPERIAL; // burma
		return METRIC;
	}

	private UnitLocale(boolean isImperial) {
		this.isImperial = isImperial;
	}

	public final double convertMetres(double metres) {
		return isImperial ? metres / 0.3048 : metres;
	}
}
