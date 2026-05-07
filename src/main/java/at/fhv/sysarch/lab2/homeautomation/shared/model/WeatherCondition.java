package at.fhv.sysarch.lab2.homeautomation.shared.model;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidWeatherConditionException;

public enum WeatherCondition {
    SUNNY,
    CLOUDY,
    RAINY,
    STORM,
    SNOW;

    public static WeatherCondition fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidWeatherConditionException(value == null ? "null" : value);
        }
        String normalized = value.trim().toUpperCase();

        switch (normalized) {
            case "STORM": return STORM;
            case "SNOW":  return SNOW;
            case "RAIN":  return RAINY;
            case "SUNNY": return SUNNY;
            case "CLOUDY": return CLOUDY;
            default:
                for (WeatherCondition condition : values()) {
                    if (condition.name().equals(normalized)) return condition;
                }
                throw new InvalidWeatherConditionException(value);
        }
    }
}
