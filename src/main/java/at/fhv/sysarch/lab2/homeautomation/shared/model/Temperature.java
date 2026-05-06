package at.fhv.sysarch.lab2.homeautomation.shared.model;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidTemperatureException;

public record Temperature(double value, String unit) {
    private static final double MIN_TEMPERATURE = -50.0;
    private static final double MAX_TEMPERATURE = 60.0;

    public static Temperature celsius(double celsius) {
        if (celsius < MIN_TEMPERATURE || celsius > MAX_TEMPERATURE) {
            throw new InvalidTemperatureException(celsius);
        }
        return new Temperature(celsius, "°C");
    }

    @Override
    public String toString() {
        return String.format("%.1f%s", value, unit);
    }
}
