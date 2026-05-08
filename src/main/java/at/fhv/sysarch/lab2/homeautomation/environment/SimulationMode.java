package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidModeException;

public enum SimulationMode {
    INTERNAL,
    EXTERNAL_MQTT,
    FIXED,
    DISABLED;


    public static SimulationMode fromString(String value) {
        for (SimulationMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new InvalidModeException("Unknown simulation mode: " + value);
    }
}
