package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

public record StatusDto(
        double temperature,
        String weather,
        String simulationMode,
        boolean acPoweredOn,
        boolean acCooling,
        boolean blindsClosed,
        boolean moviePlaying,
        String currentMovie
) {
}
