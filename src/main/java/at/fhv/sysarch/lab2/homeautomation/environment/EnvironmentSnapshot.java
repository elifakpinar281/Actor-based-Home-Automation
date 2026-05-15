package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.Temperature;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;

// Hat den current view des Environment Coordinators, damit die anderen Komponenten nicht ständig den Coordinator fragen müssen.
public record EnvironmentSnapshot(
        Temperature temperature,
        WeatherCondition weather,
        SimulationMode mode
) {}
