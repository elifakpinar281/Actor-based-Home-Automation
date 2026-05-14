package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class AirCondition extends AbstractBehavior<AirCondition.AirConditionCommand> {
    public interface AirConditionCommand { }
    public record PowerAirCondition(boolean value) implements AirConditionCommand { }
    public record EnrichedTemperature(double value, String unit) implements AirConditionCommand { }
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements AirConditionCommand { }

    public record StatusResponse(boolean isPoweredOn, boolean isCooling) { }

    private static final double THRESHOLD = 20.0;

    public static Behavior<AirConditionCommand> create(String identifier) {
        return Behaviors.setup(context -> new AirCondition(context, identifier));
    }

    private final String identifier;
    private boolean isCooling = false;
    private boolean isPoweredOn = true;

    public AirCondition(ActorContext<AirConditionCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;
        getContext().getLog().info("AirCondition '{}' started", identifier);
    }

    @Override
    public Receive<AirConditionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnrichedTemperature.class, this::onReadTemperature)
                .onMessage(PowerAirCondition.class, this::onPowerAirCondition)
                .onMessage(GetStatus.class, this::onGetStatus)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<AirConditionCommand> onReadTemperature(EnrichedTemperature r) {
        getContext().getLog().info("AirCondition '{}': received temperature {} {}", identifier, String.format("%.1f", r.value()), r.unit());

        if (!isPoweredOn) {
            getContext().getLog().info("AirCondition '{}': is powered off, ignoring temperature", identifier);
            return Behaviors.same();
        }

        if (r.value() > THRESHOLD && !isCooling) {
            isCooling = true;
            getContext().getLog().info("AirCondition '{}': Temperature {}{} > {}°C -> START COOLING", identifier, String.format("%.1f", r.value()), r.unit(), THRESHOLD);
        } else if (r.value() <= THRESHOLD && isCooling) {
            isCooling = false;
            getContext().getLog().info("AirCondition '{}': Temperature {}{} <= {}°C -> STOP COOLING (turned off)", identifier, String.format("%.1f", r.value()), r.unit(), THRESHOLD);
        }

        return Behaviors.same();
    }

    private Behavior<AirConditionCommand> onPowerAirCondition(PowerAirCondition message) {
        this.isPoweredOn = message.value();
        if (!isPoweredOn) {
            isCooling = false;
        }
        getContext().getLog().info("AirCondition '{}': Power {}", identifier, isPoweredOn ? "ON" : "OFF");
        return Behaviors.same();
    }

    private Behavior<AirConditionCommand> onGetStatus(GetStatus msg) {
        msg.replyTo.tell(new StatusResponse(isPoweredOn, isCooling));
        return Behaviors.same();
    }

    private AirCondition onPostStop() {
        getContext().getLog().info("AirCondition actor {} stopped", identifier);
        return this;
    }
}
