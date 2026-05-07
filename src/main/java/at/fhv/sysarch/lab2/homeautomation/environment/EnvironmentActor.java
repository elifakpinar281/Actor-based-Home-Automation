package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.environment.mqtt.MqttEnvironmentSource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class EnvironmentActor extends AbstractBehavior<EnvironmentActor.EnvironmentCommand> {
    public static final ServiceKey<EnvironmentCommand> ENVIRONMENT_SERVICE_KEY = ServiceKey.create(EnvironmentCommand.class, "environmentActor");

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

    public record EnvironmentStatus(Optional<Double> temperature, Optional<WeatherCondition> weather,
                                    EnvironmentSource activeSource) {}

    public record RegisterTemperatureSensor(ActorRef<TemperatureSensorNotification> sensorRef) implements EnvironmentCommand {}
    public record RegisterWeatherSensor(ActorRef<WeatherSensorNotification> sensorRef) implements EnvironmentCommand {}

    private enum PollTick implements EnvironmentCommand {
        INSTANCE
    }

    private record TemperatureSimulatorResponse(double temperature) implements EnvironmentCommand {}
    private record WeatherSimulatorResponse(WeatherCondition condition) implements EnvironmentCommand {}

    private record TemperatureSensorsUpdated(Set<ActorRef<TemperatureSensorNotification>> sensors) implements EnvironmentCommand {}

    private record WeatherSensorsUpdated(Set<ActorRef<WeatherSensorNotification>> sensors) implements EnvironmentCommand {}

    public interface TemperatureSensorNotification {
        record EnvironmentTemperatureChanged(double temperature) implements TemperatureSensorNotification {}
    }

    public interface WeatherSensorNotification {
        record EnvironmentWeatherChanged(WeatherCondition condition) implements WeatherSensorNotification {}
    }

    public static final ServiceKey<TemperatureSensorNotification> TEMPERATURE_SENSOR_SERVICE_KEY =
            ServiceKey.create(TemperatureSensorNotification.class, "temperatureSensor");

    public static final ServiceKey<WeatherSensorNotification> WEATHER_SENSOR_SERVICE_KEY = ServiceKey.create(WeatherSensorNotification.class, "weatherSensor");

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Object POLL_TIMER_KEY = "environment-poll";

    private EnvironmentSource activeSource;
    private Double currentTemperature;
    private WeatherCondition currentWeather;
    private final TimerScheduler<EnvironmentCommand> timers;

    private final ActorRef<TemperatureSimulator.TemperatureSimulatorCommand> temperatureSimulator;
    private final ActorRef<WeatherSimulator.WeatherSimulatorCommand> weatherSimulator;

    private ActorRef<MqttEnvironmentSource.MqttCommand> mqttSource;

    private final ActorRef<TemperatureSimulator.TemperatureResponse> temperatureResponseAdapter;
    private final ActorRef<WeatherSimulator.WeatherResponse> weatherResponseAdapter;

    private List<ActorRef<TemperatureSensorNotification>> temperatureSensors;
    private List<ActorRef<WeatherSensorNotification>> weatherSensors;

    public static Behavior<EnvironmentCommand> create() {
        return Behaviors.setup(context -> Behaviors.withTimers(
                timers -> new EnvironmentActor(context, timers)));
    }


    private EnvironmentActor(ActorContext<EnvironmentCommand> context, TimerScheduler<EnvironmentCommand> timers) {
        super(context);
        this.timers = timers;
        this.activeSource = EnvironmentSource.SIMULATION;
        this.currentTemperature = null;
        this.currentWeather = null;
        this.mqttSource = null;
        this.temperatureSensors = new ArrayList<>();
        this.weatherSensors = new ArrayList<>();
        this.temperatureSimulator = context.spawn(TemperatureSimulator.create(), "temperatureSimulator");
        this.weatherSimulator = context.spawn(WeatherSimulator.create(), "weatherSimulator");
        this.temperatureResponseAdapter = context.messageAdapter(TemperatureSimulator.TemperatureResponse.class,
                response -> new TemperatureSimulatorResponse(response.temperature()));

        this.weatherResponseAdapter = context.messageAdapter(WeatherSimulator.WeatherResponse.class,
                response -> new WeatherSimulatorResponse(response.condition()));

        context.getSystem().receptionist().tell(Receptionist.register(ENVIRONMENT_SERVICE_KEY, context.getSelf()));

        ActorRef<Receptionist.Listing> temperatureSensorListingAdapter = context.messageAdapter(Receptionist.Listing.class,
                listing -> {Set<ActorRef<TemperatureSensorNotification>> refs = listing.getServiceInstances(TEMPERATURE_SENSOR_SERVICE_KEY);
                    return new TemperatureSensorsUpdated(refs);
                });
        context.getSystem().receptionist().tell(Receptionist.subscribe(TEMPERATURE_SENSOR_SERVICE_KEY, temperatureSensorListingAdapter));

        ActorRef<Receptionist.Listing> weatherSensorListingAdapter = context.messageAdapter(Receptionist.Listing.class,
                listing -> {Set<ActorRef<WeatherSensorNotification>> refs = listing.getServiceInstances(WEATHER_SENSOR_SERVICE_KEY);
                    return new WeatherSensorsUpdated(refs);
                });
        context.getSystem().receptionist().tell(Receptionist.subscribe(WEATHER_SENSOR_SERVICE_KEY, weatherSensorListingAdapter));
        timers.startTimerAtFixedRate(POLL_TIMER_KEY, PollTick.INSTANCE, POLL_INTERVAL);
        getContext().getLog().info("EnvironmentActor started (source: {}, poll every {}s)", activeSource, POLL_INTERVAL.getSeconds());
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
                .onMessage(TemperatureSensorsUpdated.class, this::onTemperatureSensorsUpdated)
                .onMessage(WeatherSensorsUpdated.class, this::onWeatherSensorsUpdated)
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
        getContext().getLog().debug("Simulation temperature: {}°C", currentTemperature);
        publishTemperature();
        return this;
    }

    private Behavior<EnvironmentCommand> onWeatherSimulatorResponse(WeatherSimulatorResponse response) {
        if (activeSource != EnvironmentSource.SIMULATION) {
            return this;
        }
        currentWeather = response.condition();
        getContext().getLog().debug("Simulation weather: {}", currentWeather);
        publishWeather();
        return this;
    }

    private Behavior<EnvironmentCommand> onSwitchSource(SwitchSource command) {
        EnvironmentSource previousSource = this.activeSource;
        EnvironmentSource newSource = command.newSource();

        if (previousSource == newSource) {
            getContext().getLog().info("Environment source already set to {}", newSource);
            return this;
        }

        if (previousSource == EnvironmentSource.MQTT) {
            stopMqttSource();
        }

        this.activeSource = newSource;
        if (newSource == EnvironmentSource.MQTT) {
            startMqttSource();
        }

        if (newSource == EnvironmentSource.DISABLED) {
            currentTemperature = null;
            currentWeather = null;
        }

        getContext().getLog().info("Environment source switched: {} -> {}", previousSource, newSource);
        return this;
    }

    private Behavior<EnvironmentCommand> onSetTemperature(SetTemperature command) {
        Temperature validated = Temperature.celsius(command.temperature());
        if (activeSource == EnvironmentSource.SIMULATION || activeSource == EnvironmentSource.DISABLED) {
            getContext().getLog().info("Switching to MANUAL mode due to explicit temperature set");
            activeSource = EnvironmentSource.MANUAL;
        }
        currentTemperature = validated.value();
        getContext().getLog().info("Temperature set to {} (source: {})", validated, activeSource);
        publishTemperature();
        return this;
    }

    private Behavior<EnvironmentCommand> onSetWeather(SetWeather command) {
        if (activeSource == EnvironmentSource.SIMULATION || activeSource == EnvironmentSource.DISABLED) {
            getContext().getLog().info("Switching to MANUAL mode due to explicit weather set");
            activeSource = EnvironmentSource.MANUAL;
        }
        currentWeather = command.condition();
        getContext().getLog().info("Weather set to {} (source: {})", currentWeather, activeSource);
        publishWeather();
        return this;
    }

    private Behavior<EnvironmentCommand> onGetEnvironmentStatus(GetEnvironmentStatus command) {
        command.replyTo().tell(new EnvironmentStatus(Optional.ofNullable(currentTemperature), Optional.ofNullable(currentWeather), activeSource));
        return this;
    }

    private Behavior<EnvironmentCommand> onRegisterTemperatureSensor(RegisterTemperatureSensor command) {
        if (!temperatureSensors.contains(command.sensorRef())) {
            temperatureSensors.add(command.sensorRef());
            getContext().getLog().info("Temperature sensor registered (total: {})", temperatureSensors.size());
        }
        publishTemperature();
        return this;
    }

    private Behavior<EnvironmentCommand> onRegisterWeatherSensor(RegisterWeatherSensor command) {
        if (!weatherSensors.contains(command.sensorRef())) {
            weatherSensors.add(command.sensorRef());
            getContext().getLog().info("Weather sensor registered (total: {})", weatherSensors.size());
        }
        publishWeather();
        return this;
    }

    private Behavior<EnvironmentCommand> onTemperatureSensorsUpdated(TemperatureSensorsUpdated update) {
        this.temperatureSensors = new ArrayList<>(update.sensors());
        getContext().getLog().info("Temperature sensors discovered via Receptionist: {}", temperatureSensors.size());
        publishTemperature();
        return this;
    }

    private Behavior<EnvironmentCommand> onWeatherSensorsUpdated(WeatherSensorsUpdated update) {
        this.weatherSensors = new ArrayList<>(update.sensors());
        getContext().getLog().info("Weather sensors discovered via Receptionist: {}", weatherSensors.size());
        publishWeather();
        return this;
    }

    private void publishTemperature() {
        if (currentTemperature == null) {
            return;
        }
        TemperatureSensorNotification.EnvironmentTemperatureChanged message = new TemperatureSensorNotification.EnvironmentTemperatureChanged(currentTemperature);

        for (ActorRef<TemperatureSensorNotification> sensor : temperatureSensors) {
            sensor.tell(message);
        }
    }

    private void publishWeather() {
        if (currentWeather == null) {
            return;
        }
        WeatherSensorNotification.EnvironmentWeatherChanged message = new WeatherSensorNotification.EnvironmentWeatherChanged(currentWeather);

        for (ActorRef<WeatherSensorNotification> sensor : weatherSensors) {
            sensor.tell(message);
        }
    }

    private void publishCurrentValues() {
        publishTemperature();
        publishWeather();
    }

    private void startMqttSource() {
        if (mqttSource != null) {
            getContext().getLog().warn("MQTT source already running — stopping old one first");
            stopMqttSource();
        }
        mqttSource = getContext().spawn(MqttEnvironmentSource.create(getContext().getSelf()), "mqttEnvironmentSource");
        getContext().getLog().info("MQTT environment source started");
    }

    private void stopMqttSource() {
        if (mqttSource != null) {
            getContext().stop(mqttSource);
            mqttSource = null;
            getContext().getLog().info("MQTT environment source stopped");
        }
    }

    private EnvironmentActor onPostStop() {
        stopMqttSource();
        getContext().getLog().info("EnvironmentActor stopped");
        return this;
    }
}
