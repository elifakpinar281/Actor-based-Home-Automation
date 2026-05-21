package at.fhv.sysarch.lab2.orderprocessor;

import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderServiceHandlerFactory;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class OrderProcessorGuardian extends AbstractBehavior<OrderProcessorGuardian.Command> {
    public interface Command {}
    private record GrpcBindingComplete(ServerBinding binding, Throwable error) implements Command {}

    private static final String GRPC_HOST = "127.0.0.1";
    private static final int GRPC_PORT = 50051;

    private ServerBinding serverBinding;

    public static Behavior<Command> create() {
        return Behaviors.setup(OrderProcessorGuardian::new);
    }

    private OrderProcessorGuardian(ActorContext<Command> context) {
        super(context);

        ActorRef<PersistenceActor.Command> persistenceActor = context.spawn(
                PersistenceActor.create(),
                "persistenceActor"
        );
        ActorRef<ValidationActor.Command> validationActor = context.spawn(
                ValidationActor.create(persistenceActor),
                "validationActor"
        );

        startGrpcServer(validationActor);
    }

    private void startGrpcServer(ActorRef<ValidationActor.Command> validationActor) {
        OrderServiceActorImpl serviceImpl =
                new OrderServiceActorImpl(getContext().getSystem(), validationActor);

        CompletionStage<ServerBinding> binding = Http.get(getContext().getSystem())
                .newServerAt(GRPC_HOST, GRPC_PORT)
                .bind(OrderServiceHandlerFactory.create(serviceImpl, getContext().getSystem()));

        getContext().pipeToSelf(binding, (result, error) ->
                new GrpcBindingComplete(result, error));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GrpcBindingComplete.class, this::onGrpcBindingComplete)
                .onSignal(PostStop.class, this::onPostStop)
                .build();
    }

    private Behavior<Command> onGrpcBindingComplete(GrpcBindingComplete msg) {
        if (msg.error() != null) {
            getContext().getLog().error(
                    "Failed to bind gRPC server on {}:{} - {}",
                    GRPC_HOST, GRPC_PORT, msg.error().getMessage());
            return Behaviors.stopped();
        }
        this.serverBinding = msg.binding();
        getContext().getLog().info(
                "gRPC OrderProcessor server bound to {}",
                msg.binding().localAddress());
        return this;
    }

    private Behavior<Command> onPostStop(PostStop signal) {
        if (serverBinding != null) {
            serverBinding.terminate(Duration.ofSeconds(5));
            getContext().getLog().info("gRPC server binding terminated");
        }
        getContext().getLog().info("OrderProcessorSystem stopped");
        return this;
    }
}