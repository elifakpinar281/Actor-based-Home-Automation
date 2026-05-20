package at.fhv.sysarch.lab2.orderprocessor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PersistenceActor extends EventSourcedBehavior<PersistenceActor.Command, PersistenceActor.OrderPersisted, PersistenceActor.OrderState> {

    public interface Command {}

    public record PersistOrder(
            List<ValidationActor.OrderItemData> items,
            ActorRef<ValidationActor.ValidationResult> replyTo
    ) implements Command {}

    // Event (wird in die DB geschrieben)
    public static final class OrderPersisted {
        public final String orderId;
        public final List<OrderPersistedItem> items;
        public final double totalPrice;

        @JsonCreator
        public OrderPersisted(
                @JsonProperty("orderId") String orderId,
                @JsonProperty("items") List<OrderPersistedItem> items,
                @JsonProperty("totalPrice") double totalPrice) {
            this.orderId = orderId;
            this.items = items;
            this.totalPrice = totalPrice;
        }
    }

    public static final class OrderPersistedItem {
        public final String productId;
        public final int quantity;
        public final double unitPrice;

        @JsonCreator
        public OrderPersistedItem(
                @JsonProperty("productId") String productId,
                @JsonProperty("quantity") int quantity,
                @JsonProperty("unitPrice") double unitPrice) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }

    // State (wird beim Recovery aus den Events aufgebaut)
    public static final class OrderState {
        public final List<String> processedOrderIds;

        public OrderState() {
            this.processedOrderIds = Collections.emptyList();
        }

        public OrderState(List<String> processedOrderIds) {
            this.processedOrderIds = processedOrderIds;
        }

        public OrderState withOrder(String orderId) {
            List<String> updated = new ArrayList<>(processedOrderIds);
            updated.add(orderId);
            return new OrderState(updated);
        }
    }

    private final ActorContext<Command> context;

    public static Behavior<Command> create() {
        return Behaviors.setup(context ->
                new PersistenceActor(PersistenceId.ofUniqueId("order-processor"), context));
    }

    private PersistenceActor(PersistenceId persistenceId, ActorContext<Command> context) {
        super(persistenceId);
        this.context = context;
    }

    @Override
    public OrderState emptyState() {
        return new OrderState();
    }

    @Override
    public CommandHandler<Command, OrderPersisted, OrderState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(PersistOrder.class, this::onPersistOrder)
                .build();
    }

    private org.apache.pekko.persistence.typed.javadsl.Effect<OrderPersisted, OrderState> onPersistOrder(
            OrderState state, PersistOrder command) {

        String orderId = UUID.randomUUID().toString();

        List<OrderPersistedItem> eventItems = new ArrayList<>(command.items().size());
        double rawTotal = 0.0;
        for (ValidationActor.OrderItemData item : command.items()) {
            eventItems.add(new OrderPersistedItem(item.productId(), item.quantity(), item.unitPrice()));
            rawTotal += item.quantity() * item.unitPrice();
        }
        double totalPrice = Math.round(rawTotal * 100.0) / 100.0;

        OrderPersisted event = new OrderPersisted(orderId, eventItems, totalPrice);

        return Effect()
                .persist(event)
                .thenRun(newState -> {
                    context.getLog().info(
                            "PersistenceActor: persisted order {} ({} items, total €{})",
                            orderId, command.items().size(), totalPrice);
                    command.replyTo().tell(
                            ValidationActor.ValidationResult.success(orderId, command.items()));
                });
    }

    @Override
    public EventHandler<OrderState, OrderPersisted> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderPersisted.class,
                        (state, event) -> state.withOrder(event.orderId))
                .build();
    }
}