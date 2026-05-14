package at.fhv.sysarch.lab2.orderprocessor;

import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class OrderServiceActorImpl implements OrderService {

    private final ActorSystem<?> system;
    private final ActorRef<ValidationActor.Command> validationActor;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public OrderServiceActorImpl(ActorSystem<?> system,
                                 ActorRef<ValidationActor.Command> validationActor) {
        this.system = system;
        this.validationActor = validationActor;
    }

    @Override
    public CompletionStage<OrderResponse> processOrder(OrderRequest request) {
        List<ValidationActor.OrderItemData> items = request.getItemsList().stream()
                .map(i -> new ValidationActor.OrderItemData(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();

        return AskPattern.ask(
                validationActor,
                (ActorRef<ValidationActor.ValidationResult> replyTo) ->
                        new ValidationActor.ValidateOrder(items, replyTo),
                TIMEOUT,
                system.scheduler()
        ).thenApply(result -> {
            if (result.valid()) {
                double total = result.items().stream()
                        .mapToDouble(i -> i.quantity() * i.unitPrice()).sum();

                OrderReceipt.Builder receiptBuilder = OrderReceipt.newBuilder()
                        .setOrderId(result.message())
                        .setTotalPrice(total)
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

                OrderReceipt receipt = receiptBuilder.build();
                return OrderResponse.newBuilder()
                        .setSuccess(true)
                        .setOrderId(result.message())
                        .setMessage("Order processed successfully")
                        .setReceipt(receipt)
                        .build();
            } else {
                return OrderResponse.newBuilder()
                        .setSuccess(false)
                        .setOrderId("")
                        .setMessage(result.message())
                        .build();
            }
        });
    }
}