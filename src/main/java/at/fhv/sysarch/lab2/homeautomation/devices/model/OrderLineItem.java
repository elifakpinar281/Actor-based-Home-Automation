package at.fhv.sysarch.lab2.homeautomation.devices.model;


import java.util.Objects;

public record OrderLineItem(
        String productId,
        String productName,
        int quantity,
        double unitPrice,
        double weightPerUnit
) {
    public OrderLineItem {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        if (weightPerUnit < 0) {
            throw new IllegalArgumentException("weightPerUnit must not be negative");
        }
    }

    public double totalPrice() {
        return unitPrice * quantity;
    }

    public double totalWeight() {
        return weightPerUnit * quantity;
    }

}
