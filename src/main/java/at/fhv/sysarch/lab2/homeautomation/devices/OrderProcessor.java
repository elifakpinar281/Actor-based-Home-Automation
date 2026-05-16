package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.OrderLineItem;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Product;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Receipt;
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.grpc.GrpcClientSettings;

import java.util.Map;

public class OrderProcessor extends AbstractBehavior<OrderProcessor.OrderProcessorCommand> {
    public interface OrderProcessorCommand {}

    public record ProcessOrder(Order order, ActorRef<Fridge.OrderResponse> replyTo) implements OrderProcessorCommand {}

    // Adapter für die CompletableFuture des gRPC-Calls
    private record GrpcResponse(OrderResponse response, Order order, ActorRef<Fridge.OrderResponse> replyTo) implements OrderProcessorCommand {}
    private record GrpcFailure(Throwable error, Order order, ActorRef<Fridge.OrderResponse> replyTo) implements OrderProcessorCommand {}

    private final OrderServiceClient grpcClient;
    private final ActorRef<Fridge.FridgeCommand> fridge;

    public static Behavior<OrderProcessorCommand> create(ActorRef<Fridge.FridgeCommand> fridge) {
        return Behaviors.setup(context -> {
            ActorSystem<?> system = context.getSystem();
            OrderServiceClient client = OrderServiceClient.create(GrpcClientSettings.fromConfig("orderprocessing.OrderService", system), system);
            context.getLog().debug("OrderProcessor session started");
            return new OrderProcessor(context, client, fridge);
        });
    }

    private OrderProcessor(ActorContext<OrderProcessorCommand> context, OrderServiceClient client, ActorRef<Fridge.FridgeCommand> fridge) {
        super(context);
        this.grpcClient = client;
        this.fridge = fridge;
    }

    @Override
    public Receive<OrderProcessorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessOrder.class, this::onProcessOrder)
                .onMessage(GrpcResponse.class, this::onGrpcResponse)
                .onMessage(GrpcFailure.class, this::onGrpcFailure)
                .build();
    }

    private Behavior<OrderProcessorCommand> onProcessOrder(ProcessOrder msg) {
        OrderRequest.Builder requestBuilder = OrderRequest.newBuilder();
        for (OrderLineItem item : msg.order().lineItems()) {
            requestBuilder.addItems(
                    OrderItem.newBuilder()
                            .setProductId(item.productId())
                            .setQuantity(item.quantity())
                            .setUnitPrice(item.unitPrice())
                            .build()
            );
        }

        getContext().getLog().info("OrderProcessor: sending order {} via gRPC ({} positions)", msg.order().orderId(), msg.order().lineItems().size());

        // gRPC-Future-Ergebnis als Pekko-Message zurück an uns selbst.
        getContext().pipeToSelf(
                grpcClient.processOrder(requestBuilder.build()),
                (response, error) -> error != null
                        ? new GrpcFailure(error, msg.order(), msg.replyTo())
                        : new GrpcResponse(response, msg.order(), msg.replyTo())
        );
        return Behaviors.same();
    }

    private Behavior<OrderProcessorCommand> onGrpcResponse(GrpcResponse msg) {
        if (msg.response().getSuccess()) {
            Receipt receipt = Receipt.create(
                    msg.order().orderId(),
                    msg.order().lineItems(),
                    msg.response().getReceipt().getTotalPrice()
            );
            getContext().getLog().info("OrderProcessor: order {} completed by external system", msg.order().orderId());
            fridge.tell(new Fridge.OrderCompleted(msg.order(), receipt, msg.replyTo()));
        } else {
            String reason = "External processor rejected: " + msg.response().getMessage();
            getContext().getLog().warn("OrderProcessor: order {} rejected — {}", msg.order().orderId(), msg.response().getMessage());
            fridge.tell(new Fridge.OrderFailed(msg.order(), reason, msg.replyTo()));
        }
        return Behaviors.stopped();
    }

    private Behavior<OrderProcessorCommand> onGrpcFailure(GrpcFailure msg) {
        String reason = "gRPC communication failed: " + msg.error().getMessage();
        getContext().getLog().error("OrderProcessor: order {} failed — {}", msg.order().orderId(), msg.error().getMessage());
        fridge.tell(new Fridge.OrderFailed(msg.order(), reason, msg.replyTo()));
        return Behaviors.stopped();
    }
}