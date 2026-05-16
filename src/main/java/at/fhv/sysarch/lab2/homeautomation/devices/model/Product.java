package at.fhv.sysarch.lab2.homeautomation.devices.model;

import java.io.Serializable;
import java.util.Objects;

public record Product(
        String id,
        String name,
        double weight,
        double price,
        int quantity
) implements Serializable {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
    }

    public Product withQuantity(int newQuantity) {
        return new Product(id, name, weight, price, Math.max(0, newQuantity));
    }

    public Product addQuantity(int amount) {
        return withQuantity(quantity + amount);
    }

    public Product removeQuantity(int amount) {
        return withQuantity(quantity - amount);
    }

    public double totalWeight() {
        return weight * quantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Product otherProduct)) return false;
        return Objects.equals(id, otherProduct.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s (x%d, %.2fkg/unit, €%.2f)", name, quantity, weight, price);
    }
}
