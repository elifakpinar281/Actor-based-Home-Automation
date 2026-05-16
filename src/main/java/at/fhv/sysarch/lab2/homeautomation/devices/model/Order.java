package at.fhv.sysarch.lab2.homeautomation.devices.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Order(
        String orderId,
        List<OrderLineItem> lineItems,
        LocalDateTime timestamp,
        OrderStatus status,
        double totalPrice,
        String receiptId
) {

    public Order {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(lineItems, "lineItems");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(status, "status");
        lineItems = List.copyOf(lineItems);
    }

    public static Order create(List<OrderLineItem> lineItems) {
        double total = 0.0;
        for (OrderLineItem item : lineItems) {
            total += item.totalPrice();
        }
        return new Order(
                UUID.randomUUID().toString(),
                lineItems,
                LocalDateTime.now(),
                OrderStatus.PENDING,
                total,
                null
        );
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(orderId, lineItems, timestamp, newStatus, totalPrice, receiptId);
    }

    public Order completed(String newReceiptId) {
        return new Order(orderId, lineItems, timestamp, OrderStatus.COMPLETED, totalPrice, newReceiptId);
    }

    public Order failed() {
        return new Order(orderId, lineItems, timestamp, OrderStatus.FAILED, totalPrice, receiptId);
    }

    @Override
    public String toString() {
        return String.format("Order[%s] %s - %s - €%.2f", orderId, timestamp, status, totalPrice);
    }
}