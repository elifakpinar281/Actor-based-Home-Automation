package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class OrderProcessingException extends FridgeException {
    public OrderProcessingException(String message) {
        super("Order processing failed: " + message, ErrorCode.FRIDGE005);
    }

    public OrderProcessingException(String message, Throwable cause) {
        super("Order processing failed: " + message, ErrorCode.FRIDGE005, cause);
    }
}