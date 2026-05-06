package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.devices.sensors.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensors.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import at.fhv.sysarch.lab2.homeautomation.uihandler.DemoHttpServer;
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

    private  HomeAutomationController(ActorContext<Void> context) {
        super(context);
        ActorRef<EnvironmentActor.EnvironmentCommand> environment = getContext().spawn(EnvironmentActor.create(), "environment");
        ActorRef<EnvironmentActor.TemperatureSensorNotification> temperatureSensor = getContext().spawn(TemperatureSensor.create(environment), "temperatureSensor");

        ActorRef<EnvironmentActor.WeatherSensorNotification> weatherSensor = getContext().spawn(WeatherSensor.create(environment), "weatherSensor");
        final Http http = Http.get(context.getSystem());
        DemoHttpServer app = new DemoHttpServer(environment);
        final CompletionStage<ServerBinding> binding = http.newServerAt("localhost", 8084).bind(app.createRoute());
        ActorRef<AirCondition.AirConditionCommand> airCondition = getContext().spawn(AirCondition.create(UUID.randomUUID().toString()), "airCondition");
        getContext().getLog().info("HomeAutomation Application started - PRESS RETURN TO EXIT");

        try {
            System.in.read();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        binding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> getContext().getSystem().terminate());
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
