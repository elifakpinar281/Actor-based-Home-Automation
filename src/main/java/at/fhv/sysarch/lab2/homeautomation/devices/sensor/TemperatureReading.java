package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

public record TemperatureReading(double value, String unit) {
    public static TemperatureReading celsius(double value) {
        return new TemperatureReading(value, "°C");
    }
}