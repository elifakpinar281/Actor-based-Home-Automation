package at.fhv.sysarch.lab2.homeautomation.shared.model;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidWeatherConditionException;

public enum WeatherCondition {
    SUNNY,
    RAINY,
    CLOUDY,
    SNOWY;


    public static WeatherCondition fromString(String value) {
        for (WeatherCondition condition : values()) {
            if (condition.name().equalsIgnoreCase(value)) {
                return condition;
            }
        }
        throw new InvalidWeatherConditionException("Unknown condition: " + value);
    }
}

