package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

import java.util.List;

public class ValidationActor extends AbstractBehavior<ValidationActor.Command> {

    public interface Command {}

    public record ValidateOrder(
            List<OrderItemData> items,
            ActorRef<ValidationResult> replyTo
    ) implements Command {}

    public record OrderItemData(String productId, int quantity, double unitPrice) {}

    public record ValidationResult(
            boolean valid,
            String orderId,
            String errorMessage,
            List<OrderItemData> items,
            double totalPrice
    ) {
        public static ValidationResult success(String orderId, List<OrderItemData> items, double totalPrice) {
            return new ValidationResult(true, orderId, null, items, totalPrice);
        }

        public static ValidationResult failure(String errorMessage, List<OrderItemData> items) {
            return new ValidationResult(false, null, errorMessage, items, 0.0);
        }
    }

    private final ActorRef<PersistenceActor.Command> persistenceActor;

    public static Behavior<Command> create(ActorRef<PersistenceActor.Command> persistenceActor) {
        return Behaviors.setup(context -> new ValidationActor(context, persistenceActor));
    }

    private ValidationActor(ActorContext<Command> context,
                            ActorRef<PersistenceActor.Command> persistenceActor) {
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
        String error = findValidationError(message.items());
        if (error != null) {
            message.replyTo().tell(ValidationResult.failure(error, message.items()));
            return this;
        }

        getContext().getLog().info(
                "ValidationActor: validation passed for {} items, forwarding to persistence",
                message.items().size());

        persistenceActor.tell(new PersistenceActor.PersistOrder(message.items(), message.replyTo()));
        return this;
    }

    private String findValidationError(List<OrderItemData> items) {
        if (items == null || items.isEmpty()) {
            return "Order must contain at least one item";
        }
        for (OrderItemData item : items) {
            if (item.productId() == null || item.productId().isBlank()) {
                return "Product ID is required";
            }
            if (item.quantity() <= 0) {
                return "Quantity must be > 0 for product " + item.productId();
            }
            if (item.unitPrice() < 0) {
                return "Unit price must not be negative for product " + item.productId();
            }
        }
        return null;
    }
}