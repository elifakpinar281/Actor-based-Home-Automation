package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

// Child-Actor des Fridge. Wird nur intern genutzt -> kein Receptionist-Eintrag nötig.
public class WeightSensor extends AbstractBehavior<WeightSensor.WeightSensorCommand> {
    public interface WeightSensorCommand {}

    public record WeightChanged(double totalKg) implements WeightSensorCommand {}
    public record GetWeight(ActorRef<WeightResponse> replyTo) implements WeightSensorCommand {}
    public record WeightResponse(double totalKg, String unit) {}

    private static final String UNIT_KG = "kg";

    private final String identifier;
    private double currentTotalKg;

    public static Behavior<WeightSensorCommand> create(String identifier, double initialKg) {
        return Behaviors.setup(context -> new WeightSensor(context, identifier, initialKg));
    }

    private WeightSensor(ActorContext<WeightSensorCommand> context, String identifier, double initialKg) {
        super(context);
        this.identifier = identifier;
        this.currentTotalKg = initialKg;
        getContext().getLog().info("WeightSensor '{}' started with initial reading {} {}", identifier, currentTotalKg, UNIT_KG);
    }

    @Override
    public Receive<WeightSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeightChanged.class, this::onWeightChanged)
                .onMessage(GetWeight.class, this::onGetWeight)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<WeightSensorCommand> onWeightChanged(WeightChanged msg) {
        this.currentTotalKg = msg.totalKg();
        getContext().getLog().debug("WeightSensor '{}': reading updated to {} {}", identifier, currentTotalKg, UNIT_KG);
        return Behaviors.same();
    }

    private Behavior<WeightSensorCommand> onGetWeight(GetWeight msg) {
        msg.replyTo().tell(new WeightResponse(currentTotalKg, UNIT_KG));
        return Behaviors.same();
    }

    private Behavior<WeightSensorCommand> onPostStop() {
        getContext().getLog().info("WeightSensor '{}' stopped", identifier);
        return this;
    }
}