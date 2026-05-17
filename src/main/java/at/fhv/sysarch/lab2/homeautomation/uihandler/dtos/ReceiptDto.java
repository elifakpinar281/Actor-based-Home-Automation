package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

import java.util.List;

public record ReceiptDto(
        String receiptId,
        String orderId,
        String timestamp,
        double totalPrice,
        List<OrderItemDto> items
) {
}
