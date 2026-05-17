package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InvalidProductException extends FridgeException {
    public InvalidProductException(String reason) {
        super("Invalid product: " + reason, ErrorCode.FRIDGE_INVALID_PRODUCT);
    }

    public InvalidProductException(String reason, Throwable cause) {
        super("Invalid product: " + reason, ErrorCode.FRIDGE_INVALID_PRODUCT, cause);
    }
}