package at.fhv.sysarch.lab2.homeautomation.devices.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

public record Order(
        String orderId,
        Map<String, Integer> items,
        LocalDateTime timestamp,
        OrderStatus status,
        double totalPrice,
        String receiptId
) implements Serializable {


    public Order {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(status, "status");
        items = Collections.unmodifiableMap(new HashMap<>(items));
    }

    public static Order create(Map<String, Integer> items, double totalPrice) {
        return new Order(
                UUID.randomUUID().toString(),
                items,
                LocalDateTime.now(),
                OrderStatus.PENDING,
                totalPrice,
                null
        );
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(orderId, items, timestamp, newStatus, totalPrice, receiptId);
    }

    public Order withReceipt(String newReceiptId) {
        return new Order(orderId, items, timestamp, status, totalPrice, newReceiptId);
    }

    public Order completed(String newReceiptId) {
        return new Order(orderId, items, timestamp, OrderStatus.COMPLETED, totalPrice, newReceiptId);
    }

    public Order failed() {
        return new Order(orderId, items, timestamp, OrderStatus.FAILED, totalPrice, receiptId);
    }

    @Override
    public String toString() {
        return String.format("Order[%s] %s - %s - €%.2f", orderId, timestamp, status, totalPrice);
    }
}