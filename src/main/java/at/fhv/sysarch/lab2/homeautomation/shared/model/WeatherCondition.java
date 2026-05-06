package at.fhv.sysarch.lab2.homeautomation.shared.model;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidWeatherConditionException;

public enum WeatherCondition {
    SUNNY,
    CLOUDY,
    RAINY;

    public static WeatherCondition fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidWeatherConditionException(value == null ? "null" : value);
        }

        String normalized = value.trim().toUpperCase();
        for (WeatherCondition condition : values()) {
            if (condition.name().equals(normalized)) {
                return condition;
            }
        }
        throw new InvalidWeatherConditionException(value);
    }
}
