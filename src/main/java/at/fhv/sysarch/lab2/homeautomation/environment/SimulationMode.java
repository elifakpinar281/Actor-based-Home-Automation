package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.InvalidModeException;

public enum SimulationMode {
    INTERNAL,
    EXTERNAL_MQTT,
    FIXED,
    DISABLED;

    public static SimulationMode fromString(String value) {
        if (value == null) {
            throw new InvalidModeException("Simulation mode value must not be null");
        }
        for (SimulationMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new InvalidModeException("Unknown simulation mode: '" + value + "'");
    }
}
