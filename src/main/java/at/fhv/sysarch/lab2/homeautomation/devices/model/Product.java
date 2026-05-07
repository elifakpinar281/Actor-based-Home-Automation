package at.fhv.sysarch.lab2.homeautomation.devices.model;

import java.io.Serializable;
import java.util.Objects;

public class Product implements Serializable {
    private final String id;
    private final String name;
    private final double weight;  // in kg
    private final double price;   // in €
    private int quantity;

    public Product(String id, String name, double weight, double price, int quantity) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.weight = weight;
        this.price = price;
        this.quantity = quantity;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getWeight() { return weight; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(0, quantity);
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public void removeQuantity(int amount) {
        this.quantity = Math.max(0, this.quantity - amount);
    }

    public double getTotalWeight() {
        return weight * quantity;
    }

    @Override
    public String toString() {
        return String.format("%s (x%d, %.1fkg/unit, €%.2f)", name, quantity, weight, price);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product product = (Product) o;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}