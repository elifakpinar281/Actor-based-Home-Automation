package at.fhv.sysarch.lab2.orderprocessor;

import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderItem;
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderReceipt;
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderRequest;
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderResponse;
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderService;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class OrderServiceActorImpl implements OrderService {
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String MESSAGE_SUCCESS = "Order processed successfully";

    private final ActorSystem<?> system;
    private final ActorRef<ValidationActor.Command> validationActor;

    public OrderServiceActorImpl(ActorSystem<?> system,
                                 ActorRef<ValidationActor.Command> validationActor) {
        this.system = system;
        this.validationActor = validationActor;
    }

    @Override
    public CompletionStage<OrderResponse> processOrder(OrderRequest request) {
        List<ValidationActor.OrderItemData> items = toOrderItemData(request);

        return AskPattern.<ValidationActor.Command, ValidationActor.ValidationResult>ask(
                validationActor,
                replyTo -> new ValidationActor.ValidateOrder(items, replyTo),
                ASK_TIMEOUT,
                system.scheduler()
        ).thenApply(this::toGrpcResponse);
    }

    private List<ValidationActor.OrderItemData> toOrderItemData(OrderRequest request) {
        List<ValidationActor.OrderItemData> items = new ArrayList<>(request.getItemsList().size());
        for (OrderItem item : request.getItemsList()) {
            items.add(new ValidationActor.OrderItemData(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitPrice()));
        }
        return items;
    }

    private OrderResponse toGrpcResponse(ValidationActor.ValidationResult result) {
        if (!result.valid()) {
            return OrderResponse.newBuilder()
                    .setSuccess(false)
                    .setOrderId("")
                    .setMessage(result.errorMessage())
                    .build();
        }
        return buildSuccessResponse(result);
    }

    private OrderResponse buildSuccessResponse(ValidationActor.ValidationResult result) {
        OrderReceipt.Builder receiptBuilder = OrderReceipt.newBuilder()
                .setOrderId(result.orderId())
                .setTotalPrice(result.totalPrice())
                .setTimestamp(System.currentTimeMillis())
                .setStatus(STATUS_COMPLETED);

        for (ValidationActor.OrderItemData item : result.items()) {
            receiptBuilder.addItems(
                    OrderItem.newBuilder()
                            .setProductId(item.productId())
                            .setQuantity(item.quantity())
                            .setUnitPrice(item.unitPrice())
                            .build());
        }

        return OrderResponse.newBuilder()
                .setSuccess(true)
                .setOrderId(result.orderId())
                .setMessage(MESSAGE_SUCCESS)
                .setReceipt(receiptBuilder.build())
                .build();
    }
}