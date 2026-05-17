package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InvalidOrderLineItemException extends FridgeException {
    public InvalidOrderLineItemException(String reason) {
        super("Invalid order line item: " + reason, ErrorCode.FRIDGE_INVALID_LINE_ITEM);
    }

    public InvalidOrderLineItemException(String reason, Throwable cause) {
        super("Invalid order line item: " + reason, ErrorCode.FRIDGE_INVALID_LINE_ITEM, cause);
    }
}