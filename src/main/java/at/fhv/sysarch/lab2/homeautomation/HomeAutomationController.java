package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.environment.TemperatureEnvironment;
import at.fhv.sysarch.lab2.homeautomation.environment.MqttEnvironmentClient;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderServiceClient;
import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.MqttConnectionException;
import at.fhv.sysarch.lab2.homeautomation.uihandler.HttpServer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;

import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.receptionist.Receptionist;


public class HomeAutomationController extends AbstractBehavior<Void> {
    private static final String HTTP_HOST = "localhost";
    private static final int HTTP_PORT = 8084;

    private static final int FRIDGE_MAX_ITEMS = 80;
    private static final double FRIDGE_MAX_WEIGHT_KG = 25.0;

    private MqttEnvironmentClient mqttClient;

    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private HomeAutomationController(ActorContext<Void> context) {
        super(context);

        // gRPC-Client für das externe Order-Processor-System wird einmalig erstellt
        // über Fridge an die Per-Session-Children weitergegeben.
        OrderServiceClient orderProcessorClient = OrderServiceClient.create(
                GrpcClientSettings.fromConfig("orderprocessing.OrderService", context.getSystem()),
                context.getSystem()
        );

        ActorRef<AirCondition.AirConditionCommand> airCondition =
                context.spawn(AirCondition.create("AC-01"), "airCondition");
        ActorRef<Blinds.BlindsCommand> blinds =
                context.spawn(Blinds.create("BLINDS-01"), "blinds");
        ActorRef<Fridge.FridgeCommand> fridge =
                context.spawn(Fridge.create("FRIDGE-01", FRIDGE_MAX_ITEMS, FRIDGE_MAX_WEIGHT_KG, orderProcessorClient), "fridge");
        ActorRef<MediaStation.MediaStationCommand> mediaStation =
                context.spawn(MediaStation.create("MEDIA-01"), "mediaStation");

        // Sensoren bekommen ihre Actuators über den Receptionist
        ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor =
                context.spawn(TemperatureSensor.create(), "temperatureSensor");
        ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor =
                context.spawn(WeatherSensor.create(), "weatherSensor");

        ActorRef<EnvironmentCoordinator.Command> environmentCoordinator =
                context.spawn(EnvironmentCoordinator.create(), "environmentCoordinator");

        context.spawn(TemperatureEnvironment.create(environmentCoordinator), "temperatureEnvironment");
        context.spawn(WeatherEnvironment.create(environmentCoordinator), "weatherEnvironment");

        var receptionist = context.getSystem().receptionist();
        receptionist.tell(Receptionist.register(AirCondition.SERVICE_KEY, airCondition));
        receptionist.tell(Receptionist.register(Blinds.SERVICE_KEY, blinds));
        receptionist.tell(Receptionist.register(MediaStation.SERVICE_KEY, mediaStation));
        receptionist.tell(Receptionist.register(Fridge.SERVICE_KEY, fridge));
        receptionist.tell(Receptionist.register(EnvironmentCoordinator.SERVICE_KEY, environmentCoordinator));
        receptionist.tell(Receptionist.register(TemperatureSensor.SERVICE_KEY, temperatureSensor));
        receptionist.tell(Receptionist.register(WeatherSensor.SERVICE_KEY, weatherSensor));

        connectMqttClient(environmentCoordinator);
        startHttpServer(environmentCoordinator, fridge, mediaStation, blinds, airCondition);

        getContext().getLog().info("HomeAutomation application started — HTTP API on http://{}:{}", HTTP_HOST, HTTP_PORT);
    }

    private void connectMqttClient(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator) {
        MqttEnvironmentClient client = new MqttEnvironmentClient(environmentCoordinator);
        this.mqttClient = client;
        try {
            client.connect();
            getContext().getLog().info("MQTT connected successfully");
        } catch (MqttConnectionException ex) {
            getContext().getLog().warn("MQTT not available at startup - reconnect scheduler is active and will retry: {}", ex.getMessage());
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
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
            } catch (MqttConnectionException ex) {
                getContext().getLog().warn("MQTT disconnect failed: {}", ex.getMessage());
            }
        }
        getContext().getLog().info("HomeAutomation application stopped");
        return this;
    }
}