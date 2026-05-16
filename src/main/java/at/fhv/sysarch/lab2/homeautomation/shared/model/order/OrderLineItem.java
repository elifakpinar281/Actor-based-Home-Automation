package at.fhv.sysarch.lab2.homeautomation.shared.model.order;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidOrderLineItemException;

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
            throw new InvalidOrderLineItemException("quantity must be positive");
        }
        if (unitPrice < 0) {
            throw new InvalidOrderLineItemException("unitPrice must not be negative");
        }
        if (weightPerUnit < 0) {
            throw new InvalidOrderLineItemException("weightPerUnit must not be negative");
        }
    }

    public double totalPrice() {
        return Math.round(unitPrice * quantity * 100.0) / 100.0;
    }

    public double totalWeight() {
        return weightPerUnit * quantity;
    }
}