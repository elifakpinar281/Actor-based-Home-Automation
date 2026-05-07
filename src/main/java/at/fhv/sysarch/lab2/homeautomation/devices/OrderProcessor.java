package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Receipt;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.util.Map;

public class OrderProcessor extends AbstractBehavior<OrderProcessor.OrderProcessorCommand> {

    public interface OrderProcessorCommand { }

    public record ProcessOrder(
            Order order,
            Map<String, Integer> items,
            ActorRef<Fridge.OrderResponse> replyTo,
            String fridgeId
    ) implements OrderProcessorCommand { }

    public record OrderCompleted(Order order, Receipt receipt, ActorRef<Fridge.OrderResponse> replyTo) implements OrderProcessorCommand { }

    public static Behavior<OrderProcessorCommand> create() {
        return Behaviors.setup(OrderProcessor::new);
    }

    public OrderProcessor(ActorContext<OrderProcessorCommand> context) {
        super(context);
        getContext().getLog().debug("OrderProcessor created");
    }

    @Override
    public Receive<OrderProcessorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessOrder.class, this::onProcessOrder)
                .onMessage(OrderCompleted.class, this::onOrderCompleted)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<OrderProcessorCommand> onProcessOrder(ProcessOrder msg) {
        getContext().getLog().info("OrderProcessor: Processing order {} with {} items",
                msg.order.getOrderId(), msg.items.size());

        // Hier würde die gRPC-Kommunikation mit dem externen Order-Processor stattfinden
        // Für Demonstration: Synchron verarbeiten

        Order order = msg.order;
        order.setStatus(Order.OrderStatus.COMPLETED);

        // Dummy-Preisberechnung
        double totalPrice = msg.items.size() * 10.0;
        order.setTotalPrice(totalPrice);

        Receipt receipt = new Receipt(order.getOrderId(), msg.items, totalPrice);
        order.setReceipt(receipt.getReceiptId());

        getContext().getLog().info("OrderProcessor: Order {} processed successfully",
                order.getOrderId());

        msg.replyTo.tell(new Fridge.OrderResponse(true, "Order processed successfully", receipt));

        return Behaviors.stopped();
    }

    private Behavior<OrderProcessorCommand> onOrderCompleted(OrderCompleted msg) {
        getContext().getLog().info("OrderProcessor: Order {} marked as completed",
                msg.order.getOrderId());
        return Behaviors.stopped();
    }

    private OrderProcessor onPostStop() {
        getContext().getLog().debug("OrderProcessor stopped");
        return this;
    }
}