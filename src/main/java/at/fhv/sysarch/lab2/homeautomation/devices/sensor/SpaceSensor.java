package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

// Child-Actor des Fridge. Wird nur intern genutzt -> kein Receptionist-Eintrag nötig.
public class SpaceSensor extends AbstractBehavior<SpaceSensor.SpaceSensorCommand> {
    public interface SpaceSensorCommand {}

    public record SpaceChanged(int totalItems) implements SpaceSensorCommand {}
    public record GetSpace(ActorRef<SpaceResponse> replyTo) implements SpaceSensorCommand {}
    public record SpaceResponse(int totalItems, String unit) {}

    private static final String UNIT_ITEMS = "items";

    private final String identifier;
    private int currentTotalItems;

    public static Behavior<SpaceSensorCommand> create(String identifier, int initialItems) {
        return Behaviors.setup(context -> new SpaceSensor(context, identifier, initialItems));
    }

    private SpaceSensor(ActorContext<SpaceSensorCommand> context, String identifier, int initialItems) {
        super(context);
        this.identifier = identifier;
        this.currentTotalItems = initialItems;
        getContext().getLog().info("SpaceSensor '{}' started with initial reading {} {}", identifier, currentTotalItems, UNIT_ITEMS);
    }

    @Override
    public Receive<SpaceSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(SpaceChanged.class, this::onSpaceChanged)
                .onMessage(GetSpace.class, this::onGetSpace)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<SpaceSensorCommand> onSpaceChanged(SpaceChanged msg) {
        this.currentTotalItems = msg.totalItems();
        getContext().getLog().debug("SpaceSensor '{}': reading updated to {} {}", identifier, currentTotalItems, UNIT_ITEMS);
        return Behaviors.same();
    }

    private Behavior<SpaceSensorCommand> onGetSpace(GetSpace msg) {
        msg.replyTo().tell(new SpaceResponse(currentTotalItems, UNIT_ITEMS));
        return Behaviors.same();
    }

    private Behavior<SpaceSensorCommand> onPostStop() {
        getContext().getLog().info("SpaceSensor '{}' stopped", identifier);
        return this;
    }
}