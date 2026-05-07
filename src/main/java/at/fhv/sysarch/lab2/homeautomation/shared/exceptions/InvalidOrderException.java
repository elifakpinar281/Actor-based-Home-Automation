package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InvalidOrderException extends FridgeException {
    public InvalidOrderException(String reason) {
        super("Invalid order: " + reason, ErrorCode.FRIDGE004);
    }

    public InvalidOrderException(String reason, Throwable cause) {
        super("Invalid order: " + reason, ErrorCode.FRIDGE004, cause);
    }
}