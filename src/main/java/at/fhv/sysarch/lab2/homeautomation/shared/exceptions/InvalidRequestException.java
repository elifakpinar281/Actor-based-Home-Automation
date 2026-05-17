package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InvalidRequestException extends DomainException {
    public InvalidRequestException(String message) {
        super(message, ErrorCode.INVALID_REQUEST);
    }
}
