package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;

import java.util.concurrent.CompletionStage;

public class OrderProcessorGuardian extends AbstractBehavior<OrderProcessorGuardian.Command> {
    public interface Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(OrderProcessorGuardian::new);
    }

    private OrderProcessorGuardian(ActorContext<Command> context) {
        super(context);

        // Interne Actors des externen Systems
        ActorRef<PersistenceActor.Command> persistenceActor =
                context.spawn(PersistenceActor.create(), "persistenceActor");

        ActorRef<ValidationActor.Command> validationActor =
                context.spawn(ValidationActor.create(persistenceActor), "validationActor");

        // gRPC Server starten
        OrderServiceActorImpl serviceImpl =
                new OrderServiceActorImpl(context.getSystem(), validationActor);

        CompletionStage<ServerBinding> bound = Http.get(context.getSystem())
                .newServerAt("127.0.0.1", 50051)
                .bind(
                        at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing
                                .OrderServiceHandlerFactory.create(serviceImpl, context.getSystem())
                );

        bound.thenAccept(binding ->
                context.getLog().info("gRPC OrderProcessor bound to: {}", binding.localAddress())
        );
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> {
                    getContext().getLog().info("OrderProcessorSystem stopped");
                    return this;
                })
                .build();
    }
}