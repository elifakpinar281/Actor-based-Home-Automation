package at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions;

public abstract class FridgeException extends DomainException {
    public FridgeException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }

    public FridgeException(String message, ErrorCode errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}