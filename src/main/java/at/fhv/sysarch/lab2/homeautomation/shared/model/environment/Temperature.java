package at.fhv.sysarch.lab2.homeautomation.shared.model.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidTemperatureException;


public record Temperature(double value, String unit) {
    public static final double MIN_CELSIUS = -50.0;
    public static final double MAX_CELSIUS = 60.0;
    public static final String CELSIUS_UNIT = "°C";

    public Temperature {
        if (value < MIN_CELSIUS || value > MAX_CELSIUS) {
            throw new InvalidTemperatureException(value);
        }
    }

    public static Temperature celsius(double celsius) {
        return new Temperature(celsius, CELSIUS_UNIT);
    }

    public static double clampToRange(double celsius) {
        if (celsius < MIN_CELSIUS) return MIN_CELSIUS;
        if (celsius > MAX_CELSIUS) return MAX_CELSIUS;
        return celsius;
    }

    public static boolean isInRange(double celsius) {
        return celsius >= MIN_CELSIUS && celsius <= MAX_CELSIUS;
    }

    @Override
    public String toString() {
        return String.format("%.1f%s", value, unit);
    }
}