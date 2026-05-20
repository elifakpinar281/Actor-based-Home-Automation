package at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions;

public class InvalidModeException extends DomainException {
    public InvalidModeException(String message) {
        super(message, ErrorCode.INVALID_MODE);
    }
}
