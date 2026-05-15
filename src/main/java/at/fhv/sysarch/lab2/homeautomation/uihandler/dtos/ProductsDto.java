package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

import at.fhv.sysarch.lab2.homeautomation.uihandler.HttpServer;

import java.util.List;

public record ProductsDto(
        List<HttpServer.ProductDto> products
) {
}
