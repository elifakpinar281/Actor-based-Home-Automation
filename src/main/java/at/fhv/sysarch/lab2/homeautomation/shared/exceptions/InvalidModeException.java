package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InvalidModeException extends DomainException {
    public InvalidModeException(String message) {
        super(message, ErrorCode.MODE001);
    }
}
