package at.fhv.sysarch.lab2.homeautomation.grpcdemo;

import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.*;
import org.apache.pekko.actor.typed.ActorSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OrderProcessorServiceImpl implements OrderService {

    final ActorSystem<?> system;
    private int orderCounter = 0;

    public OrderProcessorServiceImpl(ActorSystem<?> system) {
        this.system = system;
    }

    @Override
    public CompletionStage<OrderResponse> processOrder(OrderRequest request) {
        // Validierung
        if (request.getQuantity() <= 0) {
            return CompletableFuture.completedFuture(
                    OrderResponse.newBuilder()
                            .setSuccess(false)
                            .setOrderId("")
                            .setMessage("Quantity muss > 0 sein")
                            .build()
            );
        }

        if (request.getProductId().isEmpty()) {
            return CompletableFuture.completedFuture(
                    OrderResponse.newBuilder()
                            .setSuccess(false)
                            .setOrderId("")
                            .setMessage("Product ID ist erforderlich")
                            .build()
            );
        }

        // Order verarbeiten
        String orderId = "ORD-" + (++orderCounter) + "-" + System.currentTimeMillis();
        double totalPrice = request.getQuantity() * request.getUnitPrice();

        OrderReceipt receipt = OrderReceipt.newBuilder()
                .setOrderId(orderId)
                .setProductId(request.getProductId())
                .setQuantity(request.getQuantity())
                .setUnitPrice(request.getUnitPrice())
                .setTotalPrice(totalPrice)
                .setTimestamp(System.currentTimeMillis())
                .setStatus("COMPLETED")
                .build();

        // Erfolgreiche Response
        OrderResponse response = OrderResponse.newBuilder()
                .setSuccess(true)
                .setOrderId(orderId)
                .setMessage("Order erfolgreich verarbeitet")
                .setReceipt(receipt)
                .build();

        System.out.println("Order verarbeitet: " + orderId + " - Total: " + totalPrice);

        return CompletableFuture.completedFuture(response);
    }
}