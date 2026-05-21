package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PersistenceActor extends EventSourcedBehavior<PersistenceActor.Command, PersistenceActor.OrderPersisted, PersistenceActor.OrderState> {
    public interface Command {}

    public record PersistOrder(
            List<ValidationActor.OrderItemData> items,
            ActorRef<ValidationActor.ValidationResult> replyTo
    ) implements Command {}

    public record OrderPersisted(
            String orderId,
            List<OrderPersistedItem> items,
            double totalPrice
    ) {
        public OrderPersisted {
            items = List.copyOf(items);
        }
    }

    public record OrderPersistedItem(
            String productId,
            int quantity,
            double unitPrice
    ) {}

    public record OrderState(List<String> processedOrderIds) {
        public OrderState() {
            this(List.of());
        }

        public OrderState {
            processedOrderIds = List.copyOf(processedOrderIds);
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

    private Effect<OrderPersisted, OrderState> onPersistOrder(
            OrderState state, PersistOrder command) {

        String orderId = UUID.randomUUID().toString();

        List<OrderPersistedItem> eventItems = new ArrayList<>(command.items().size());
        double rawTotal = 0.0;
        for (ValidationActor.OrderItemData item : command.items()) {
            eventItems.add(new OrderPersistedItem(
                    item.productId(),
                    item.quantity(),
                    item.unitPrice()));
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
                        (state, event) -> state.withOrder(event.orderId()))
                .build();
    }
}