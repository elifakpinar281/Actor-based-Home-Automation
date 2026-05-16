package at.fhv.sysarch.lab2.homeautomation.shared.model.order;

import java.time.LocalDateTime;
import java.util.*;

public record Receipt(
        String receiptId,
        String orderId,
        LocalDateTime timestamp,
        List<OrderLineItem> lineItems,
        double totalPrice
) {

    public Receipt {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(lineItems, "lineItems");
        lineItems = List.copyOf(lineItems);
    }

    public static Receipt create(String orderId, List<OrderLineItem> lineItems, double totalPrice) {
        return new Receipt(
                UUID.randomUUID().toString(),
                orderId,
                LocalDateTime.now(),
                lineItems,
                totalPrice
        );
    }

    @Override
    public String toString() {
        return String.format("Receipt[%s] Order: %s - €%.2f - %s", receiptId, orderId, totalPrice, timestamp);
    }
}
