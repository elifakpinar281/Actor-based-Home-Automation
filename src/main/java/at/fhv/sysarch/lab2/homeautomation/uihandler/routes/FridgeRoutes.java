package at.fhv.sysarch.lab2.homeautomation.uihandler.routes;

import at.fhv.sysarch.lab2.homeautomation.devices.Fridge;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Product;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.*;
import at.fhv.sysarch.lab2.homeautomation.uihandler.exception.ErrorResponse;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FridgeRoutes extends AllDirectives {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ActorRef<Fridge.FridgeCommand> fridgeActor;
    private final ActorSystem<?> system;

    public FridgeRoutes(ActorRef<Fridge.FridgeCommand> fridgeActor, ActorSystem<?> system) {
        this.fridgeActor = fridgeActor;
        this.system = system;
    }

    public Route routes() {
        return pathPrefix("fridge", () -> concat(
                path("products", this::getProducts),
                path("capacity", this::getCapacity),
                path("consume", this::consumeProduct),
                path("order", this::orderProducts),
                path("history", this::getOrderHistory)
        ));
    }

    private Route getProducts() {
        return get(() ->
                onComplete(
                        AskPattern.ask(fridgeActor, Fridge.GetProducts::new, TIMEOUT, system.scheduler()),
                        response -> {
                            if (!response.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("Failed to get products"), Jackson.marshaller());
                            }
                            List<ProductDto> dtos = new ArrayList<>();
                            for (Product p : response.get().products()) {
                                dtos.add(new ProductDto(p.getId(), p.getName(), p.getWeight(), p.getPrice(), p.getQuantity()));
                            }
                            return complete(StatusCodes.OK, new ProductsDto(dtos), Jackson.marshaller());
                        }
                )
        );
    }

    private Route getCapacity() {
        return get(() ->
                onComplete(
                        AskPattern.ask(fridgeActor, Fridge.GetCapacity::new, TIMEOUT, system.scheduler()),
                        response -> {
                            if (!response.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("Failed to get capacity"), Jackson.marshaller());
                            }
                            Fridge.CapacityResponse c = response.get();
                            return complete(StatusCodes.OK,
                                    new CapacityDto(c.currentItems(), c.maxItems(), Math.round(c.currentWeight() * 100.0) / 100.0, c.maxWeight()),
                                    Jackson.marshaller());
                        }
                )
        );
    }

    private Route getOrderHistory() {
        return get(() ->
                onComplete(
                        AskPattern.ask(fridgeActor, Fridge.GetProducts::new, TIMEOUT, system.scheduler()),
                        productsResult -> {
                            if (!productsResult.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("Failed to get products"), Jackson.marshaller());
                            }
                            Map<String, Product> byId = new HashMap<>();
                            for (Product p : productsResult.get().products()) {
                                byId.put(p.getId(), p);
                            }
                            return onComplete(
                                    AskPattern.ask(fridgeActor, Fridge.GetOrderHistory::new, TIMEOUT, system.scheduler()),
                                    historyResult -> {
                                        if (!historyResult.isSuccess()) {
                                            return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                                    new ErrorResponse("Failed to get history"), Jackson.marshaller());
                                        }
                                        List<OrderDto> dtos = new ArrayList<>();
                                        for (Order o : historyResult.get().orders()) {
                                            List<OrderItemDto> itemDtos = new ArrayList<>();
                                            for (Map.Entry<String, Integer> entry : o.getItems().entrySet()) {
                                                Product p = byId.get(entry.getKey());
                                                String name = p != null ? p.getName()  : entry.getKey();
                                                double unitPrice = p != null ? p.getPrice() : 0.0;
                                                itemDtos.add(new OrderItemDto(entry.getKey(), name, entry.getValue(), unitPrice));
                                            }
                                            dtos.add(new OrderDto(
                                                    o.getOrderId(),
                                                    o.getTimestamp().toString(),
                                                    o.getStatus().name(),
                                                    o.getTotalPrice(),
                                                    itemDtos
                                            ));
                                        }
                                        return complete(StatusCodes.OK, new OrderHistoryDto(dtos), Jackson.marshaller());
                                    }
                            );
                        }
                )
        );
    }

    private Route consumeProduct() {
        return post(() ->
                parameter("productId", productId ->
                        parameter("quantity", quantityStr -> {
                            int quantity = Integer.parseInt(quantityStr);
                            if (quantity <= 0) {
                                return complete(StatusCodes.BAD_REQUEST,
                                        new ErrorResponse("Quantity must be positive"), Jackson.marshaller());
                            }
                            fridgeActor.tell(new Fridge.ConsumeProduct(productId, quantity));
                            return complete(StatusCodes.ACCEPTED, new SuccessResponse("Product consumption request sent"), Jackson.marshaller());
                        })
                )
        );
    }

    private Route orderProducts() {
        return post(() ->
                entity(Jackson.unmarshaller(OrderRequest.class), request -> {
                    if (request.items() == null || request.items().isEmpty()) {
                        return complete(StatusCodes.BAD_REQUEST, new ErrorResponse("Items are required"), Jackson.marshaller());
                    }
                    for (Integer qty : request.items().values()) {
                        if (qty == null || qty <= 0) {
                            return complete(StatusCodes.BAD_REQUEST,
                                    new ErrorResponse("Quantities must be positive"), Jackson.marshaller());
                        }
                    }
                    fridgeActor.tell(new Fridge.OrderProducts(request.items(), null));
                    return complete(StatusCodes.ACCEPTED, new SuccessResponse("Order request sent"), Jackson.marshaller());
                })
        );
    }
}