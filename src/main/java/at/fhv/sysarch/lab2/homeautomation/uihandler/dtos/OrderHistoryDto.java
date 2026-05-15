package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

import at.fhv.sysarch.lab2.homeautomation.uihandler.HttpServer;

import java.util.List;

public record OrderHistoryDto(
        List<OrderDto> orders
) {}
