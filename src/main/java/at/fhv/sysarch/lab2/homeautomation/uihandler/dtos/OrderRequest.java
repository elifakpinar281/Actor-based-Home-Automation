package at.fhv.sysarch.lab2.homeautomation.uihandler.dtos;

import java.util.Map;

public record OrderRequest(
        Map<String, Integer> items
) {}
