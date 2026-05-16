package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

import java.util.List;

public class ValidationActor extends AbstractBehavior<ValidationActor.Command> {

    public interface Command {}

    public record ValidateOrder(List<OrderItemData> items, ActorRef<ValidationResult> replyTo) implements Command {}
    public record OrderItemData(String productId, int quantity, double unitPrice) {}

    public record ValidationResult(boolean valid, String orderId, String errorMessage, List<OrderItemData> items) {
        public static ValidationResult success(String orderId, List<OrderItemData> items) {
            return new ValidationResult(true, orderId, null, items);
        }
        public static ValidationResult failure(String errorMessage, List<OrderItemData> items) {
            return new ValidationResult(false, null, errorMessage, items);
        }
    }

    private final ActorRef<PersistenceActor.Command> persistenceActor;

    public static Behavior<Command> create(ActorRef<PersistenceActor.Command> persistenceActor) {
        return Behaviors.setup(ctx -> new ValidationActor(ctx, persistenceActor));
    }

    private ValidationActor(ActorContext<Command> context, ActorRef<PersistenceActor.Command> persistenceActor) {
        super(context);
        this.persistenceActor = persistenceActor;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ValidateOrder.class, this::onValidate)
                .build();
    }

    private Behavior<Command> onValidate(ValidateOrder message) {
        if (message.items == null || message.items.isEmpty()) {
            message.replyTo.tell(ValidationResult.failure("Order must contain at least one item", List.of()));
            return Behaviors.same();
        }

        for (OrderItemData item : message.items) {
            if (item.productId() == null || item.productId().isBlank()) {
                message.replyTo.tell(ValidationResult.failure("Product ID is required", message.items));
                return Behaviors.same();
            }
            if (item.quantity() <= 0) {
                message.replyTo.tell(ValidationResult.failure("Quantity must be > 0 for product " + item.productId(), message.items));
                return Behaviors.same();
            }
            if (item.unitPrice() < 0) {
                message.replyTo.tell(ValidationResult.failure("Unit price must not be negative for product " + item.productId(), message.items));
                return Behaviors.same();
            }
        }

        getContext().getLog().info("ValidationActor: validation passed for {} items, forwarding to persistence", message.items.size());
        persistenceActor.tell(new PersistenceActor.PersistOrder(message.items, message.replyTo));
        return Behaviors.same();
    }
}
