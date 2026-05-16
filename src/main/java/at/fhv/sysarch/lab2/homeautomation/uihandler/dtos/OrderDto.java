package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

import java.util.List;

public record OrderDto(
        String orderId,
        String timestamp,
        String status,
        double totalPrice,
        List<OrderItemDto>items
) {
}
