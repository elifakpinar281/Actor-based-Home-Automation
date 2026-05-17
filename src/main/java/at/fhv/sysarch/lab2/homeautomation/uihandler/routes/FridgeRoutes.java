package at.fhv.sysarch.lab2.homeautomation.uihandler.routes;

import at.fhv.sysarch.lab2.homeautomation.devices.Fridge;
import at.fhv.sysarch.lab2.homeautomation.shared.model.order.Order;
import at.fhv.sysarch.lab2.homeautomation.shared.model.order.OrderLineItem;
import at.fhv.sysarch.lab2.homeautomation.shared.model.order.Product;
import at.fhv.sysarch.lab2.homeautomation.shared.model.order.Receipt;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidOrderException;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.CapacityDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.OrderDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.OrderHistoryDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.OrderItemDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.OrderRequest;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.OrderResponseDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.ProductDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.ProductsDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.ReceiptDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.SuccessResponse;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FridgeRoutes extends AllDirectives {
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ORDER_ASK_TIMEOUT = Duration.ofSeconds(10);

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
        return get(() -> onSuccess(
                AskPattern.ask(fridgeActor, Fridge.GetProducts::new, ASK_TIMEOUT, system.scheduler()),
                response -> {
                    List<ProductDto> dtos = new ArrayList<>();
                    for (Product product : response.products()) {
                        dtos.add(new ProductDto(product.id(), product.name(), product.weight(), product.price(), product.quantity()));
                    }
                    return complete(StatusCodes.OK, new ProductsDto(dtos), Jackson.marshaller());
                }
        ));
    }

    private Route getCapacity() {
        return get(() -> onSuccess(
                AskPattern.ask(fridgeActor, Fridge.GetCapacity::new, ASK_TIMEOUT, system.scheduler()),
                response -> complete(StatusCodes.OK,
                        new CapacityDto(
                                response.currentItems(),
                                response.maxItems(),
                                response.currentWeight(),
                                response.maxWeight()),
                        Jackson.marshaller())
        ));
    }

    private Route getOrderHistory() {
        return get(() -> onSuccess(
                AskPattern.ask(fridgeActor, Fridge.GetOrderHistory::new, ASK_TIMEOUT, system.scheduler()),
                historyResponse -> {
                    List<OrderDto> dtos = new ArrayList<>();
                    for (Order order : historyResponse.orders()) {
                        dtos.add(toOrderDto(order));
                    }
                    return complete(StatusCodes.OK, new OrderHistoryDto(dtos), Jackson.marshaller());
                }
        ));
    }

    private OrderDto toOrderDto(Order order) {
        List<OrderItemDto> itemDtos = new ArrayList<>();
        for (OrderLineItem item : order.lineItems()) {
            itemDtos.add(new OrderItemDto(item.productId(), item.productName(), item.quantity(), item.unitPrice()));
        }
        return new OrderDto(
                order.orderId(),
                order.timestamp().toString(),
                order.status().name(),
                order.totalPrice(),
                itemDtos
        );
    }

    private OrderResponseDto toOrderResponseDto(Fridge.OrderResponse response) {
        ReceiptDto receiptDto = null;
        Receipt receipt = response.receipt();
        if (receipt != null) {
            List<OrderItemDto> itemDtos = new ArrayList<>();
            for (OrderLineItem item : receipt.lineItems()) {
                itemDtos.add(new OrderItemDto(item.productId(), item.productName(), item.quantity(), item.unitPrice()));
            }
            receiptDto = new ReceiptDto(
                    receipt.receiptId(),
                    receipt.orderId(),
                    receipt.timestamp().toString(),
                    receipt.totalPrice(),
                    itemDtos
            );
        }
        return new OrderResponseDto(response.success(), response.message(), receiptDto);
    }

    private Route consumeProduct() {
        return post(() ->
                parameter("productId", productId ->
                        parameter("quantity", quantityStr -> {
                            int quantity = Integer.parseInt(quantityStr);
                            if (quantity <= 0) {
                                throw new InvalidOrderException("Quantity must be positive");
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
                        throw new InvalidOrderException("Items are required");
                    }
                    for (Map.Entry<String, Integer> entry : request.items().entrySet()) {
                        if (entry.getValue() == null || entry.getValue() <= 0) {
                            throw new InvalidOrderException("Quantity must be positive for product " + entry.getKey());
                        }
                    }
                    return onSuccess(
                            AskPattern.<Fridge.FridgeCommand, Fridge.OrderResponse>ask(
                                    fridgeActor,
                                    replyTo -> new Fridge.OrderProducts(request.items(), replyTo),
                                    ORDER_ASK_TIMEOUT,
                                    system.scheduler()
                            ),
                            orderResponse -> {
                                OrderResponseDto dto = toOrderResponseDto(orderResponse);
                                if (orderResponse.success()) {
                                    return complete(StatusCodes.OK, dto, Jackson.marshaller());
                                } else {
                                    return complete(StatusCodes.UNPROCESSABLE_ENTITY, dto, Jackson.marshaller());
                                }
                            }
                    );
                })
        );
    }
}