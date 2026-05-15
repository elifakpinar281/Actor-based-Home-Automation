package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

public record MediaStatusResponse(
        String currentMovie,
        boolean isPlaying
) {
}
