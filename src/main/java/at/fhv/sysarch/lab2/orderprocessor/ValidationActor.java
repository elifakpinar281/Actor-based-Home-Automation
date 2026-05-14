package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

public class ValidationActor extends AbstractBehavior<ValidationActor.Command> {

    public interface Command {}

    public record ValidateOrder(
            java.util.List<OrderItemData> items,
            ActorRef<ValidationResult> replyTo
    ) implements Command {}

    public record OrderItemData(String productId, int quantity, double unitPrice) {}

    public record ValidationResult(boolean valid, String message,
                                   java.util.List<OrderItemData> items) {}

    private final ActorRef<PersistenceActor.Command> persistenceActor;

    public static Behavior<Command> create(ActorRef<PersistenceActor.Command> persistenceActor) {
        return Behaviors.setup(ctx -> new ValidationActor(ctx, persistenceActor));
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

    private Behavior<Command> onValidate(ValidateOrder msg) {
        for (OrderItemData item : msg.items) {
            if (item.quantity <= 0) {
                msg.replyTo.tell(new ValidationResult(false, "Quantity must be > 0 for " + item.productId, msg.items));
                return Behaviors.same();
            }
            if (item.productId == null || item.productId.isEmpty()) {
                msg.replyTo.tell(new ValidationResult(false, "Product ID required", msg.items));
                return Behaviors.same();
            }
            if (item.unitPrice < 0) {
                msg.replyTo.tell(new ValidationResult(false, "Price cannot be negative", msg.items));
                return Behaviors.same();
            }
        }
        persistenceActor.tell(new PersistenceActor.PersistOrder(msg.items, msg.replyTo));
        return Behaviors.same();
    }
}