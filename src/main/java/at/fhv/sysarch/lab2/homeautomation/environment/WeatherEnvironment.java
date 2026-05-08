package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.time.Duration;
import java.util.Random;

public class WeatherEnvironment extends AbstractBehavior<WeatherEnvironment.WeatherEnvironmentCommand> {
    public interface WeatherEnvironmentCommand {}
    public record GetWeather(ActorRef<WeatherCondition> replyTo) implements WeatherEnvironmentCommand {}
    public record RequestWeather(ActorRef<WeatherSensor.WeatherSensorCommand> replyTo) implements WeatherEnvironmentCommand {}
    public record SetWeather(WeatherCondition condition) implements WeatherEnvironmentCommand {}
    public record Tick() implements WeatherEnvironmentCommand {}
    public record SetEnvironmentSwitch(ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch) implements WeatherEnvironmentCommand {}

    private WeatherCondition current = WeatherCondition.SUNNY;
    private final Random random = new Random();
    private static final WeatherCondition[] CONDITIONS = WeatherCondition.values();
    private ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch;

    public static Behavior<WeatherEnvironmentCommand> create() {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers -> {timers.startTimerWithFixedDelay("tick", new Tick(), Duration.ofSeconds(10));
                return new WeatherEnvironment(context);
                })
        );
    }

    private WeatherEnvironment(ActorContext<WeatherEnvironmentCommand> context) {
        super(context);
    }

    @Override
    public Receive<WeatherEnvironmentCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(GetWeather.class, this::onGetWeather)
                .onMessage(SetWeather.class, this::onSetWeather)
                .onMessage(SetEnvironmentSwitch.class, this::onSetEnvironmentSwitch)
                .onMessage(RequestWeather.class, this::onRequestWeather)
                .build();
    }

    private Behavior<WeatherEnvironmentCommand> onTick(Tick message) {
        int currentIndex = current.ordinal();
        int nextIndex = random.nextInt(CONDITIONS.length - 1);

        if (nextIndex >= currentIndex) {
            nextIndex++;
        }

        current = CONDITIONS[nextIndex];
        getContext().getLog().debug("Weather changed to {}", current);
        if (environmentSwitch != null) {
            environmentSwitch.tell(new EnvironmentSwitch.InternalWeatherUpdate(current));
        }

        return this;
    }

    private Behavior<WeatherEnvironmentCommand> onGetWeather(GetWeather message) {
        message.replyTo().tell(current);
        return this;
    }

    private Behavior<WeatherEnvironmentCommand> onSetWeather(SetWeather message) {
        current = message.condition();
        getContext().getLog().info("Weather set to {}", current);
        return this;
    }

    private Behavior<WeatherEnvironmentCommand> onSetEnvironmentSwitch(SetEnvironmentSwitch message) {
        this.environmentSwitch = message.environmentSwitch();
        return this;
    }

    private Behavior<WeatherEnvironmentCommand> onRequestWeather(RequestWeather message) {
        message.replyTo().tell(new WeatherSensor.WeatherResult(current));
        return this;
    }
}
