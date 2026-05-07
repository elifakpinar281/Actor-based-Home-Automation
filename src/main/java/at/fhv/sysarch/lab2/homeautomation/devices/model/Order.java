package at.fhv.sysarch.lab2.homeautomation.devices.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Order implements Serializable {
    private final String orderId;
    private final Map<String, Integer> items;  // productId -> quantity
    private final LocalDateTime timestamp;
    private OrderStatus status;
    private double totalPrice;
    private String receipt;

    public enum OrderStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    public Order(Map<String, Integer> items) {
        this.orderId = UUID.randomUUID().toString();
        this.items = new HashMap<>(items);
        this.timestamp = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
        this.totalPrice = 0.0;
    }

    public String getOrderId() { return orderId; }
    public Map<String, Integer> getItems() { return new HashMap<>(items); }
    public LocalDateTime getTimestamp() { return timestamp; }
    public OrderStatus getStatus() { return status; }
    public double getTotalPrice() { return totalPrice; }
    public String getReceipt() { return receipt; }

    public void setStatus(OrderStatus status) { this.status = status; }
    public void setTotalPrice(double price) { this.totalPrice = price; }
    public void setReceipt(String receipt) { this.receipt = receipt; }

    @Override
    public String toString() {
        return String.format("Order[%s] %s - %s - €%.2f", orderId, timestamp, status, totalPrice);
    }
}