package at.fhv.sysarch.lab2.orderprocessor;

import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;
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
        return AskPattern.ask(
                validationActor,
                (ActorRef<ValidationActor.ValidationResult> replyTo) ->
                        new ValidationActor.ValidateOrder(
                                request.getProductId(),
                                request.getQuantity(),
                                request.getUnitPrice(),
                                replyTo
                        ),
                TIMEOUT,
                system.scheduler()
        ).thenApply(result -> {
            if (result.valid()) {
                OrderReceipt receipt = OrderReceipt.newBuilder()
                        .setOrderId(result.message()) // message enthält orderId bei Erfolg
                        .setProductId(result.productId())
                        .setQuantity(result.quantity())
                        .setUnitPrice(result.unitPrice())
                        .setTotalPrice(result.quantity() * result.unitPrice())
                        .setTimestamp(System.currentTimeMillis())
                        .setStatus("COMPLETED")
                        .build();

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