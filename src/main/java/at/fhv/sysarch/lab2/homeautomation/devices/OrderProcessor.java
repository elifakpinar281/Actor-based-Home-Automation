package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
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

    public record ProcessOrder(
            Order order,
            Map<String, Integer> items,
            Map<String, Double> prices,  // NEU: productId → unitPrice
            ActorRef<Fridge.OrderResponse> replyTo,
            String fridgeId,
            ActorRef<Fridge.FridgeCommand> fridgeRef
    ) implements OrderProcessorCommand {}

    // Wird intern als Adapter für die async gRPC-Antwort genutzt
    private record GrpcResponse(
            OrderResponse response, Order order,
            ActorRef<Fridge.OrderResponse> replyTo,
            ActorRef<Fridge.FridgeCommand> fridgeRef
    ) implements OrderProcessorCommand {}

    private record GrpcFailure(
            Throwable error, Order order,
            ActorRef<Fridge.OrderResponse> replyTo,
            ActorRef<Fridge.FridgeCommand> fridgeRef
    ) implements OrderProcessorCommand {}

    private final OrderServiceClient grpcClient;

    public static Behavior<OrderProcessorCommand> create() {
        return Behaviors.setup(context -> {
            context.getLog().info("OrderProcessor: SETUP STARTED");
            ActorSystem<?> system = context.getSystem();
            try {
                context.getLog().info("OrderProcessor: creating gRPC client with config...");
                OrderServiceClient client = OrderServiceClient.create(
                        GrpcClientSettings.fromConfig("orderprocessing.OrderService", system),
                        system
                );
                context.getLog().info("OrderProcessor: gRPC client created OK");
                return new OrderProcessor(context, client);
            } catch (Exception e) {
                context.getLog().error("OrderProcessor: FAILED: {}", e.getMessage(), e);
                throw e;
            }
        });
    }

    private OrderProcessor(ActorContext<OrderProcessorCommand> context, OrderServiceClient client) {
        super(context);
        this.grpcClient = client;
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
        getContext().getLog().info("OrderProcessor: onProcessOrder called, items: {}", msg.items.size());

        OrderRequest.Builder requestBuilder = OrderRequest.newBuilder();

        for (Map.Entry<String, Integer> entry : msg.items.entrySet()) {
            getContext().getLog().info("OrderProcessor: adding item {} qty {} price {}",
                    entry.getKey(), entry.getValue(), msg.prices.get(entry.getKey()));
            requestBuilder.addItems(
                    OrderItem.newBuilder()
                            .setProductId(entry.getKey())
                            .setQuantity(entry.getValue())
                            .setUnitPrice(msg.prices.get(entry.getKey()))
                            .build()
            );
        }

        getContext().getLog().info("OrderProcessor: sending gRPC request...");

        getContext().pipeToSelf( // ?
                grpcClient.processOrder(requestBuilder.build()),
                (response, error) -> {
                    if (error != null) {
                        getContext().getLog().error("OrderProcessor: gRPC error in pipeToSelf: {}", error.getMessage());
                        return new GrpcFailure(error, msg.order, msg.replyTo, msg.fridgeRef);
                    }
                    getContext().getLog().info("OrderProcessor: gRPC response received, success={}", response.getSuccess());
                    return new GrpcResponse(response, msg.order, msg.replyTo, msg.fridgeRef);
                }
        );
        return Behaviors.same();
    }

    private Behavior<OrderProcessorCommand> onGrpcResponse(GrpcResponse msg) {
        getContext().getLog().info("OrderProcessor: response success={}, message='{}'",
                msg.response().getSuccess(), msg.response().getMessage());
        if (msg.response.getSuccess()) {
            OrderReceipt grpcReceipt = msg.response.getReceipt();
            Receipt receipt = new Receipt(
                    msg.order.getOrderId(),
                    msg.order.getItems(),
                    grpcReceipt.getTotalPrice()
            );
            msg.order.setStatus(Order.OrderStatus.COMPLETED);
            msg.order.setReceipt(receipt.getReceiptId());

            msg.fridgeRef.tell(new Fridge.OrderCompleted(msg.order, receipt));

            if (msg.replyTo != null) {
                msg.replyTo.tell(new Fridge.OrderResponse(true, "Order processed via gRPC", receipt));
            }
        } else {
            msg.order.setStatus(Order.OrderStatus.FAILED);
            if (msg.replyTo != null) {
                msg.replyTo.tell(new Fridge.OrderResponse(false, msg.response.getMessage(), null));
            }
        }
        return Behaviors.stopped();
    }

    private Behavior<OrderProcessorCommand> onGrpcFailure(GrpcFailure msg) {
        getContext().getLog().error("gRPC call failed: {}", msg.error.getMessage());
        msg.order.setStatus(Order.OrderStatus.FAILED);
        if (msg.replyTo != null) {
            msg.replyTo.tell(new Fridge.OrderResponse(false, "gRPC error: " + msg.error.getMessage(), null));
        }
        return Behaviors.stopped();
    }
}