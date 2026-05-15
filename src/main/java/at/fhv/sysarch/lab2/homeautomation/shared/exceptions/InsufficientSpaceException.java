package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InsufficientSpaceException extends FridgeException {
    public InsufficientSpaceException(int currentSize, int maxSize, int attemptedAddition) {
        super(String.format("Insufficient space in fridge. Current: %d, Max: %d, Attempted addition: %d", currentSize, maxSize, attemptedAddition),
                ErrorCode.FRIDGE001
        );
    }
}