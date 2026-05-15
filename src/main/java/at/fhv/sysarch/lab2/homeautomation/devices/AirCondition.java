package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.shared.model.Temperature;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

public class AirCondition extends AbstractBehavior<AirCondition.AirConditionCommand> {

    public interface AirConditionCommand {}

    public record PowerAirCondition(boolean value) implements AirConditionCommand {}
    public record EnrichedTemperature(Temperature temperature) implements AirConditionCommand {}
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements AirConditionCommand {}
    public record StatusResponse(boolean isPoweredOn, boolean isCooling) {}

    private static final double COOLING_THRESHOLD_CELSIUS = 20.0;

    public static final ServiceKey<AirConditionCommand> SERVICE_KEY = ServiceKey.create(AirConditionCommand.class, "airCondition");

    private final String identifier;
    private boolean isCooling = false;
    private boolean isPoweredOn = true;

    public static Behavior<AirConditionCommand> create(String identifier) {
        return Behaviors.setup(context -> new AirCondition(context, identifier));
    }

    private AirCondition(ActorContext<AirConditionCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;
        getContext().getLog().info("AirCondition '{}' started", identifier);
    }

    @Override
    public Receive<AirConditionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnrichedTemperature.class, this::onTemperatureReceived)
                .onMessage(PowerAirCondition.class, this::onPowerAirCondition)
                .onMessage(GetStatus.class, this::onGetStatus)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<AirConditionCommand> onTemperatureReceived(EnrichedTemperature message) {
        Temperature temperature = message.temperature();
        getContext().getLog().info("AirCondition '{}': received {}", identifier, temperature);

        if (!isPoweredOn) {
            getContext().getLog().info("AirCondition '{}': powered off, ignoring temperature", identifier);
            return Behaviors.same();
        }

        boolean shouldCool = temperature.value() > COOLING_THRESHOLD_CELSIUS;
        if (shouldCool && !isCooling) {
            isCooling = true;
            getContext().getLog().info("AirCondition '{}': {} > {}°C → START COOLING", identifier, temperature, COOLING_THRESHOLD_CELSIUS);
        } else if (!shouldCool && isCooling) {
            isCooling = false;
            getContext().getLog().info("AirCondition '{}': {} ≤ {}°C → STOP COOLING", identifier, temperature, COOLING_THRESHOLD_CELSIUS);
        }
        return Behaviors.same();
    }

    private Behavior<AirConditionCommand> onPowerAirCondition(PowerAirCondition message) {
        this.isPoweredOn = message.value();
        if (!isPoweredOn) {
            isCooling = false;
        }
        getContext().getLog().info("AirCondition '{}': power {}", identifier, isPoweredOn ? "ON" : "OFF");
        return Behaviors.same();
    }

    private Behavior<AirConditionCommand> onGetStatus(GetStatus message) {
        message.replyTo().tell(new StatusResponse(isPoweredOn, isCooling));
        return Behaviors.same();
    }

    private AirCondition onPostStop() {
        getContext().getLog().info("AirCondition '{}' stopped", identifier);
        return this;
    }
}
