package at.fhv.sysarch.lab2.orderprocessor;

import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class OrderServiceActorImpl implements OrderService {
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(5);

    private final ActorSystem<?> system;
    private final ActorRef<ValidationActor.Command> validationActor;

    public OrderServiceActorImpl(ActorSystem<?> system, ActorRef<ValidationActor.Command> validationActor) {
        this.system = system;
        this.validationActor = validationActor;
    }

    @Override
    public CompletionStage<OrderResponse> processOrder(OrderRequest request) {
        List<ValidationActor.OrderItemData> items = request.getItemsList().stream()
                .map(item -> new ValidationActor.OrderItemData(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                .toList();

        return AskPattern.<ValidationActor.Command, ValidationActor.ValidationResult>ask(validationActor, replyTo -> new ValidationActor.ValidateOrder(items, replyTo),
                ASK_TIMEOUT, system.scheduler()
        ).thenApply(this::toGrpcResponse);
    }

    private OrderResponse toGrpcResponse(ValidationActor.ValidationResult result) {
        if (!result.valid()) {
            return OrderResponse.newBuilder()
                    .setSuccess(false)
                    .setOrderId("")
                    .setMessage(result.errorMessage())
                    .build();
        }

        double totalPrice = result.items().stream()
                .mapToDouble(item -> item.quantity() * item.unitPrice())
                .sum();

        OrderReceipt.Builder receiptBuilder = OrderReceipt.newBuilder()
                .setOrderId(result.orderId())
                .setTotalPrice(totalPrice)
                .setTimestamp(System.currentTimeMillis())
                .setStatus("COMPLETED");

        for (ValidationActor.OrderItemData item : result.items()) {
            receiptBuilder.addItems(
                    OrderItem.newBuilder()
                            .setProductId(item.productId())
                            .setQuantity(item.quantity())
                            .setUnitPrice(item.unitPrice())
                            .build()
            );
        }

        return OrderResponse.newBuilder()
                .setSuccess(true)
                .setOrderId(result.orderId())
                .setMessage("Order processed successfully")
                .setReceipt(receiptBuilder.build())
                .build();
    }
}