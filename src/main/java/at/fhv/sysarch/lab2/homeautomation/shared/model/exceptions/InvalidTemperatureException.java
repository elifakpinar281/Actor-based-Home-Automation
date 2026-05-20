package at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions;

public class InvalidTemperatureException extends DomainException {
    public InvalidTemperatureException(double temperature) {
        super("Temperature value " + temperature + "°C is outside the valid range (-50°C to 60°C).", ErrorCode.ENV_INVALID_TEMPERATURE);
    }
}
