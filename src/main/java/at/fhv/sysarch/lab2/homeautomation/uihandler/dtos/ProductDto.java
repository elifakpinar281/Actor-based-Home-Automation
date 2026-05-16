package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

public record ProductDto(
        String id,
        String name,
        double weight,
        double price,
        int quantity
) {}
