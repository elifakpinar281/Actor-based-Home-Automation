package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

public record OrderItemDto(
        String productId,
        String productName,
        int quantity,
        double unitPrice
) {
}
