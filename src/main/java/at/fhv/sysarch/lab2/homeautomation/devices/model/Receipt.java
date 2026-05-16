package at.fhv.sysarch.lab2.homeautomation.devices.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

public record Receipt(
        String receiptId,
        String orderId,
        LocalDateTime timestamp,
        Map<String, Integer> items,
        double totalPrice
) {

    public Receipt {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(items, "items");
        items = Collections.unmodifiableMap(new HashMap<>(items));
    }

    public static Receipt create(String orderId, Map<String, Integer> items, double totalPrice) {
        return new Receipt(
                UUID.randomUUID().toString(),
                orderId,
                LocalDateTime.now(),
                items,
                totalPrice
        );
    }

    @Override
    public String toString() {
        return String.format("Receipt[%s] Order: %s - €%.2f - %s", receiptId, orderId, totalPrice, timestamp);
    }
}