package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

import java.util.List;

public record OrderHistoryDto(
        List<OrderDto> orders
) {}
