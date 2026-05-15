package at.fhv.sysarch.lab2.homeautomation.shared.model;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidWeatherConditionException;

public enum WeatherCondition {
    SUNNY,
    RAINY,
    CLOUDY,
    SNOWY;

    public static WeatherCondition fromString(String value) {
        if (value == null) {
            throw new InvalidWeatherConditionException("Weather condition value must not be null");
        }
        for (WeatherCondition condition : values()) {
            if (condition.name().equalsIgnoreCase(value.trim())) {
                return condition;
            }
        }
        throw new InvalidWeatherConditionException("Unknown weather condition: '" + value + "'");
    }
}
