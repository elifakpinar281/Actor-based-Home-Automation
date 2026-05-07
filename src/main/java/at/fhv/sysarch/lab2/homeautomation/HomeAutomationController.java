package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.devices.sensors.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensors.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
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

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class HomeAutomationController extends AbstractBehavior<Void> {
    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private HomeAutomationController(ActorContext<Void> context) {
        super(context);

        // Environment
        ActorRef<EnvironmentActor.EnvironmentCommand> environment = getContext().spawn(EnvironmentActor.create(), "environment");

        // Sensoren
        ActorRef<EnvironmentActor.TemperatureSensorNotification> temperatureSensor = getContext().spawn(TemperatureSensor.create(environment), "temperatureSensor");

        ActorRef<EnvironmentActor.WeatherSensorNotification> weatherSensor = getContext().spawn(WeatherSensor.create(environment), "weatherSensor");

        // Devices
        ActorRef<AirCondition.AirConditionCommand> airCondition =
                getContext().spawn(AirCondition.create("AC-01"), "airCondition");

        ActorRef<Blinds.BlindsCommand> blinds =
                getContext().spawn(Blinds.create("BLINDS-01"), "blinds");

        ActorRef<OrderProcessor.OrderProcessorCommand> orderProcessor =
                getContext().spawn(OrderProcessor.create(), "orderProcessor");

        ActorRef<Fridge.FridgeCommand> fridge =
                getContext().spawn(
                        Fridge.create("FRIDGE-01", 100, 50.0, orderProcessor),
                        "fridge"
                );

        ActorRef<MediaStation.MediaStationCommand> mediaStation =
                getContext().spawn(MediaStation.create("MEDIA-01"), "mediaStation");

        // REST API
        final Http http = Http.get(context.getSystem());
        HttpServer app = new HttpServer(environment, fridge, mediaStation, blinds, context.getSystem());
        final CompletionStage<ServerBinding> binding =
                http.newServerAt("localhost", 8084).bind(app.createRoute());

        getContext().getLog().info("HomeAutomation Application started - PRESS RETURN TO EXIT");

        try {
            System.in.read();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        binding.thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> getContext().getSystem().terminate());
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private HomeAutomationController onPostStop() {
        getContext().getLog().info("HomeAutomation Application stopped");
        return this;
    }
}
