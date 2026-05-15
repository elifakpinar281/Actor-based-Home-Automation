package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

import at.fhv.sysarch.lab2.homeautomation.uihandler.HttpServer;

import java.util.List;

public record OrderDto(
        String orderId,
        String timestamp, //???
        String status,
        double totalPrice,
        List<HttpServer.OrderItemDto>items
) {
}
