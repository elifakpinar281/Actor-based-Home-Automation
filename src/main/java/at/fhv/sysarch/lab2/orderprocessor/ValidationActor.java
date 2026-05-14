package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

public class ValidationActor extends AbstractBehavior<ValidationActor.Command> {

    public interface Command {}

    public record ValidateOrder(
            String productId, int quantity, double unitPrice,
            ActorRef<ValidationResult> replyTo
    ) implements Command {}

    public record ValidationResult(boolean valid, String message,
                                   String productId, int quantity, double unitPrice) {}

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
        if (msg.quantity <= 0) {
            msg.replyTo.tell(new ValidationResult(false, "Quantity must be > 0",
                    msg.productId, msg.quantity, msg.unitPrice));
            return Behaviors.same();
        }
        if (msg.productId == null || msg.productId.isEmpty()) {
            msg.replyTo.tell(new ValidationResult(false, "Product ID required",
                    msg.productId, msg.quantity, msg.unitPrice));
            return Behaviors.same();
        }
        if (msg.unitPrice < 0) {
            msg.replyTo.tell(new ValidationResult(false, "Price cannot be negative",
                    msg.productId, msg.quantity, msg.unitPrice));
            return Behaviors.same();
        }

        // Validierung OK → an PersistenceActor weiterleiten
        persistenceActor.tell(new PersistenceActor.PersistOrder(
                msg.productId, msg.quantity, msg.unitPrice, msg.replyTo
        ));
        return Behaviors.same();
    }
}