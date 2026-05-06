package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.Temperature;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.time.Duration;
import java.util.Optional;

public class EnvironmentActor extends AbstractBehavior<EnvironmentActor.EnvironmentCommand> {
    public static final ServiceKey<EnvironmentCommand> ENVIRONMENT_SERVICE_KEY =
            ServiceKey.create(EnvironmentCommand.class, "environmentActor");

    public interface EnvironmentCommand {}

    public enum EnvironmentSource {
        SIMULATION,
        MQTT,
        MANUAL,
        DISABLED
    }

    public record SwitchSource(EnvironmentSource newSource) implements EnvironmentCommand {}
    public record SetTemperature(double temperature) implements EnvironmentCommand {}
    public record SetWeather(WeatherCondition condition) implements EnvironmentCommand {}

    public record GetEnvironmentStatus(ActorRef<EnvironmentStatus> replyTo) implements EnvironmentCommand {}

    public record EnvironmentStatus(
            Optional<Double> temperature,
            Optional<WeatherCondition> weather,
            EnvironmentSource activeSource
    ) {}

    private enum PollTick implements EnvironmentCommand {
        INSTANCE
    }

    private record TemperatureSimulatorResponse(double temperature) implements EnvironmentCommand {}
    private record WeatherSimulatorResponse(WeatherCondition condition) implements EnvironmentCommand {}

    public record TemperatureUpdate(double temperature) {}
    public record WeatherUpdate(WeatherCondition condition) {}

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Object POLL_TIMER_KEY = "environment-poll";

    private EnvironmentSource activeSource;
    private Double currentTemperature;
    private WeatherCondition currentWeather;
    private final TimerScheduler<EnvironmentCommand> timers;

    private final ActorRef<TemperatureSimulator.TemperatureSimulatorCommand> temperatureSimulator;
    private final ActorRef<WeatherSimulator.WeatherSimulatorCommand> weatherSimulator;
    private final ActorRef<TemperatureSimulator.TemperatureResponse> temperatureResponseAdapter;
    private final ActorRef<WeatherSimulator.WeatherResponse> weatherResponseAdapter;
    private ActorRef<TemperatureSensorNotification> temperatureSensorRef;
    private ActorRef<WeatherSensorNotification> weatherSensorRef;

    public interface TemperatureSensorNotification {
        record EnvironmentTemperatureChanged(double temperature) implements TemperatureSensorNotification {}
    }

    public interface WeatherSensorNotification {
        record EnvironmentWeatherChanged(WeatherCondition condition) implements WeatherSensorNotification {}
    }

    public static Behavior<EnvironmentCommand> create() {
        return Behaviors.setup(context -> Behaviors.withTimers(timers -> new EnvironmentActor(context, timers)));
    }

    private EnvironmentActor(ActorContext<EnvironmentCommand> context, TimerScheduler<EnvironmentCommand> timers) {
        super(context);
        this.timers = timers;
        this.activeSource = EnvironmentSource.SIMULATION;
        this.currentTemperature = null;
        this.currentWeather = null;
        this.temperatureSimulator = context.spawn(TemperatureSimulator.create(), "temperatureSimulator");
        this.weatherSimulator = context.spawn(WeatherSimulator.create(), "weatherSimulator");

        this.temperatureResponseAdapter = context.messageAdapter(TemperatureSimulator.TemperatureResponse.class, response -> new TemperatureSimulatorResponse(response.temperature()));
        this.weatherResponseAdapter = context.messageAdapter(WeatherSimulator.WeatherResponse.class, response -> new WeatherSimulatorResponse(response.condition()));

        context.getSystem().receptionist().tell(Receptionist.register(ENVIRONMENT_SERVICE_KEY, context.getSelf()));
        timers.startTimerAtFixedRate(POLL_TIMER_KEY, PollTick.INSTANCE, POLL_INTERVAL);
        getContext().getLog().info("EnvironmentActor started (source: {}, poll interval: {}s)", activeSource, POLL_INTERVAL.getSeconds());
    }

    @Override
    public Receive<EnvironmentCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(PollTick.class, this::onPollTick)
                .onMessage(TemperatureSimulatorResponse.class, this::onTemperatureSimulatorResponse)
                .onMessage(WeatherSimulatorResponse.class, this::onWeatherSimulatorResponse)
                .onMessage(SwitchSource.class, this::onSwitchSource)
                .onMessage(SetTemperature.class, this::onSetTemperature)
                .onMessage(SetWeather.class, this::onSetWeather)
                .onMessage(GetEnvironmentStatus.class, this::onGetEnvironmentStatus)
                .onMessage(RegisterTemperatureSensor.class, this::onRegisterTemperatureSensor)
                .onMessage(RegisterWeatherSensor.class, this::onRegisterWeatherSensor)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<EnvironmentCommand> onPollTick(PollTick tick) {
        switch (activeSource) {
            case SIMULATION:
                temperatureSimulator.tell(new TemperatureSimulator.ReadCurrentTemperature(temperatureResponseAdapter));
                weatherSimulator.tell(new WeatherSimulator.ReadCurrentWeather(weatherResponseAdapter));
                break;

            case MQTT:
                break;

            case MANUAL:
                publishCurrentValues();
                break;

            case DISABLED:
                break;
        }
        return this;
    }

    private Behavior<EnvironmentCommand> onTemperatureSimulatorResponse(TemperatureSimulatorResponse response) {
        if (activeSource != EnvironmentSource.SIMULATION) {
            return this;
        }
        currentTemperature = response.temperature();
        getContext().getLog().debug("Environment temperature updated: {}°C", currentTemperature);
        publishTemperature();
        return this;
    }

    private Behavior<EnvironmentCommand> onWeatherSimulatorResponse(WeatherSimulatorResponse response) {
        if (activeSource != EnvironmentSource.SIMULATION) {
            return this;
        }
        currentWeather = response.condition();
        getContext().getLog().debug("Environment weather updated: {}", currentWeather);
        publishWeather();
        return this;
    }

    private Behavior<EnvironmentCommand> onSwitchSource(SwitchSource command) {
        EnvironmentSource previousSource = this.activeSource;
        this.activeSource = command.newSource();
        getContext().getLog().info("Environment source switched: {} -> {}", previousSource, activeSource);
        if (activeSource == EnvironmentSource.DISABLED) {
            currentTemperature = null;
            currentWeather = null;
        }
        return this;
    }

    private Behavior<EnvironmentCommand> onSetTemperature(SetTemperature command) {
        Temperature validated = Temperature.celsius(command.temperature());

        if (activeSource != EnvironmentSource.MANUAL && activeSource != EnvironmentSource.MQTT) {
            getContext().getLog().info("Switching to MANUAL mode due to explicit temperature set");
            activeSource = EnvironmentSource.MANUAL;
        }

        currentTemperature = validated.value();
        getContext().getLog().info("Temperature manually set to {}", validated);
        publishTemperature();
        return this;
    }

    private Behavior<EnvironmentCommand> onSetWeather(SetWeather command) {
        if (activeSource != EnvironmentSource.MANUAL && activeSource != EnvironmentSource.MQTT) {
            getContext().getLog().info("Switching to MANUAL mode due to explicit weather set");
            activeSource = EnvironmentSource.MANUAL;
        }

        currentWeather = command.condition();
        getContext().getLog().info("Weather manually set to {}", currentWeather);
        publishWeather();
        return this;
    }

    private Behavior<EnvironmentCommand> onGetEnvironmentStatus(GetEnvironmentStatus command) {
        command.replyTo().tell(new EnvironmentStatus(Optional.ofNullable(currentTemperature), Optional.ofNullable(currentWeather),
                activeSource));
        return this;
    }

    private void publishTemperature() {
        if (currentTemperature == null) {
            return;
        }
        if (temperatureSensorRef != null) {
            temperatureSensorRef.tell(new TemperatureSensorNotification.EnvironmentTemperatureChanged(currentTemperature));
        }
    }

    private void publishWeather() {
        if (currentWeather == null) {
            return;
        }
        if (weatherSensorRef != null) {
            weatherSensorRef.tell(
                    new WeatherSensorNotification.EnvironmentWeatherChanged(currentWeather));
        }
    }

    private void publishCurrentValues() {
        publishTemperature();
        publishWeather();
    }

    public record RegisterTemperatureSensor(ActorRef<TemperatureSensorNotification> sensorRef) implements EnvironmentCommand {}
    public record RegisterWeatherSensor(ActorRef<WeatherSensorNotification> sensorRef) implements EnvironmentCommand {}

    private Behavior<EnvironmentCommand> onRegisterTemperatureSensor(RegisterTemperatureSensor command) {
        this.temperatureSensorRef = command.sensorRef();
        getContext().getLog().info("Temperature sensor registered");
        publishTemperature();
        return this;
    }

    private Behavior<EnvironmentCommand> onRegisterWeatherSensor(RegisterWeatherSensor command) {
        this.weatherSensorRef = command.sensorRef();
        getContext().getLog().info("Weather sensor registered");
        publishWeather();
        return this;
    }

    private EnvironmentActor onPostStop() {
        getContext().getLog().info("EnvironmentActor stopped");
        return this;
    }
}
