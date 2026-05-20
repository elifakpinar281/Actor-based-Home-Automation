package at.fhv.sysarch.lab2.homeautomation.shared.model.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.InvalidWeatherConditionException;

public enum WeatherCondition {
    SUNNY,
    RAINY,
    CLOUDY,
    SNOWY,
    STORMY;

    public static WeatherCondition fromString(String value) {
        if (value == null) {
            throw new InvalidWeatherConditionException("null");
        }

        String normalized = value.trim().toUpperCase();

        switch (normalized) {
            case "STORM":
                return STORMY;
            case "SNOW":
                return SNOWY;
            case "RAIN":
                return RAINY;
            case "SUN":
                return SUNNY;
            case "CLOUD":
                return CLOUDY;
            default:
        }

        for (WeatherCondition condition : values()) {
            if (condition.name().equals(normalized)) {
                return condition;
            }
        }
        throw new InvalidWeatherConditionException(value);
    }
}