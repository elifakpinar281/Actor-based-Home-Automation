package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InvalidWeatherConditionException extends DomainException {
    public InvalidWeatherConditionException(String condition) {
        super("Unknown weather condition: '" + condition + "'. " + "Valid conditions are: SUNNY, CLOUDY, RAINY.", ErrorCode.ENV002);
    }
}
