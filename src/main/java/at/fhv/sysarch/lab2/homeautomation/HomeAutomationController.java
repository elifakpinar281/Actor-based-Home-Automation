package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.environment.TemperatureEnvironment;
import at.fhv.sysarch.lab2.homeautomation.environment.MqttEnvironmentClient;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.MqttConnectionException;
import at.fhv.sysarch.lab2.homeautomation.uihandler.HttpServer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;

import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.receptionist.Receptionist;


public class HomeAutomationController extends AbstractBehavior<Void> {
    private static final String HTTP_HOST = "localhost";
    private static final int HTTP_PORT = 8084;

    private static final int FRIDGE_MAX_ITEMS = 80;
    private static final double FRIDGE_MAX_WEIGHT_KG = 25.0;

    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private HomeAutomationController(ActorContext<Void> context) {
        super(context);

        ActorRef<AirCondition.AirConditionCommand> airCondition =
                context.spawn(AirCondition.create("AC-01"), "airCondition");
        ActorRef<Blinds.BlindsCommand> blinds =
                context.spawn(Blinds.create("BLINDS-01"), "blinds");
        ActorRef<Fridge.FridgeCommand> fridge =
                context.spawn(Fridge.create("FRIDGE-01", FRIDGE_MAX_ITEMS, FRIDGE_MAX_WEIGHT_KG), "fridge");
        ActorRef<MediaStation.MediaStationCommand> mediaStation =
                context.spawn(MediaStation.create("MEDIA-01", blinds), "mediaStation");

        ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor =
                context.spawn(TemperatureSensor.create(airCondition), "temperatureSensor");
        ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor =
                context.spawn(WeatherSensor.create(blinds), "weatherSensor");

        ActorRef<EnvironmentCoordinator.Command> environmentCoordinator =
                context.spawn(EnvironmentCoordinator.create(), "environmentCoordinator");

        context.spawn(TemperatureEnvironment.create(environmentCoordinator), "temperatureEnvironment");
        context.spawn(WeatherEnvironment.create(environmentCoordinator), "weatherEnvironment");

        registerWithReceptionist(airCondition, blinds, mediaStation, fridge, environmentCoordinator,
                temperatureSensor, weatherSensor);

        connectMqttClient(environmentCoordinator);
        startHttpServer(environmentCoordinator, fridge, mediaStation, blinds, airCondition);

        getContext().getLog().info("HomeAutomation application started — HTTP API on http://{}:{}", HTTP_HOST, HTTP_PORT);
    }

    private void registerWithReceptionist(ActorRef<AirCondition.AirConditionCommand> airCondition, ActorRef<Blinds.BlindsCommand> blinds, ActorRef<MediaStation.MediaStationCommand> mediaStation,
                                          ActorRef<Fridge.FridgeCommand> fridge, ActorRef<EnvironmentCoordinator.Command> environmentCoordinator, ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor,
                                          ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor) {
        var receptionist = getContext().getSystem().receptionist();
        receptionist.tell(Receptionist.register(AirCondition.SERVICE_KEY, airCondition));
        receptionist.tell(Receptionist.register(Blinds.SERVICE_KEY, blinds));
        receptionist.tell(Receptionist.register(MediaStation.SERVICE_KEY, mediaStation));
        receptionist.tell(Receptionist.register(Fridge.SERVICE_KEY, fridge));
        receptionist.tell(Receptionist.register(EnvironmentCoordinator.SERVICE_KEY, environmentCoordinator));
        receptionist.tell(Receptionist.register(TemperatureSensor.SERVICE_KEY, temperatureSensor));
        receptionist.tell(Receptionist.register(WeatherSensor.SERVICE_KEY, weatherSensor));
    }

    private void connectMqttClient(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator) {
        try {
            MqttEnvironmentClient mqttClient = new MqttEnvironmentClient(environmentCoordinator);
            mqttClient.connect();
            getContext().getLog().info("MQTT connected successfully");
        } catch (MqttConnectionException ex) {
            getContext().getLog().warn("MQTT not available (system will run without external weather): {}", ex.getMessage());
        }
    }

    private void startHttpServer(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator, ActorRef<Fridge.FridgeCommand> fridge,
                                 ActorRef<MediaStation.MediaStationCommand> mediaStation, ActorRef<Blinds.BlindsCommand> blinds,
                                 ActorRef<AirCondition.AirConditionCommand> airCondition) {
        HttpServer app = new HttpServer(environmentCoordinator, fridge, mediaStation, blinds, airCondition, getContext().getSystem());
        CompletionStage<ServerBinding> binding = Http.get(getContext().getSystem())
                .newServerAt(HTTP_HOST, HTTP_PORT)
                .bind(app.createRoute());

        binding.whenComplete((serverBinding, error) -> {
            if (error != null) {
                getContext().getLog().error("HTTP server failed to bind on {}:{} - {}", HTTP_HOST, HTTP_PORT, error.getMessage());
                getContext().getSystem().terminate();
            }
        });
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private HomeAutomationController onPostStop() {
        getContext().getLog().info("HomeAutomation application stopped");
        return this;
    }
}