package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

public record CapacityDto(
        int currentItems,
        int maxItems,
        double currentWeight,
        double maxWeight
) {
}
