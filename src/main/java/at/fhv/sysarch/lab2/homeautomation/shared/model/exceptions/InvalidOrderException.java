package at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions;

public class InvalidOrderException extends FridgeException {
    public InvalidOrderException(String reason) {
        super("Invalid order: " + reason, ErrorCode.FRIDGE_INVALID_ORDER);
    }

    public InvalidOrderException(String reason, Throwable cause) {
        super("Invalid order: " + reason, ErrorCode.FRIDGE_INVALID_ORDER, cause);
    }
}