package at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions;

public class ProductNotAvailableException extends FridgeException {
    public ProductNotAvailableException(String productName, int requested, int available) {
        super(String.format("Product '%s' not available. Requested: %d, Available: %d", productName, requested, available),
                ErrorCode.FRIDGE_PRODUCT_NOT_AVAILABLE
        );
    }
}