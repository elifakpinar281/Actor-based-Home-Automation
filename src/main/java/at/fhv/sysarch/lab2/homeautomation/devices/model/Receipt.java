package at.fhv.sysarch.lab2.homeautomation.devices.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Receipt implements Serializable {
    private final String receiptId;
    private final String orderId;
    private final LocalDateTime timestamp;
    private final Map<String, Integer> items;  // productId -> quantity
    private final double totalPrice;
    private final String status;

    public Receipt(String orderId, Map<String, Integer> items, double totalPrice) {
        this.receiptId = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.timestamp = LocalDateTime.now();
        this.items = new HashMap<>(items);
        this.totalPrice = totalPrice;
        this.status = "COMPLETED";
    }

    public String getReceiptId() { return receiptId; }
    public String getOrderId() { return orderId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Integer> getItems() { return new HashMap<>(items); }
    public double getTotalPrice() { return totalPrice; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return String.format("Receipt[%s] Order: %s - €%.2f - %s", receiptId, orderId, totalPrice, timestamp);
    }
}