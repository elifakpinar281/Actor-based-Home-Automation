package at.fhv.sysarch.lab2.orderprocessor;

import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderServiceHandlerFactory;
import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;

import java.util.concurrent.CompletionStage;

public class OrderProcessorGuardian extends AbstractBehavior<OrderProcessorGuardian.Command> {
    public interface Command {}

    private static final String GRPC_HOST = "127.0.0.1";
    private static final int GRPC_PORT = 50051;
    private static final String BLOCKING_IO_DISPATCHER = "pekko.actor.blocking-io-dispatcher";

    public static Behavior<Command> create() {
        return Behaviors.setup(OrderProcessorGuardian::new);
    }

    private OrderProcessorGuardian(ActorContext<Command> context) {
        super(context);

        // JDBC-Calls sind synchron und können den Thread blockieren.
        // PersistenceActor läuft auf einem eigenen blocking-io-dispatcher, damit die JDBC-Calls nicht die Threads des default-dispatchers belegen
        // Die Konfiguration dafür ist in orderprocessor.conf
        ActorRef<PersistenceActor.Command> persistenceActor = context.spawn(
                PersistenceActor.create(),
                "persistenceActor",
                DispatcherSelector.fromConfig(BLOCKING_IO_DISPATCHER)
        );
        ActorRef<ValidationActor.Command> validationActor = context.spawn(ValidationActor.create(persistenceActor), "validationActor");
        startGrpcServer(validationActor);
    }

    private void startGrpcServer(ActorRef<ValidationActor.Command> validationActor) {
        OrderServiceActorImpl serviceImpl = new OrderServiceActorImpl(getContext().getSystem(), validationActor);

        CompletionStage<ServerBinding> binding = Http.get(getContext().getSystem())
                .newServerAt(GRPC_HOST, GRPC_PORT)
                .bind(OrderServiceHandlerFactory.create(serviceImpl, getContext().getSystem()));

        binding.whenComplete((serverBinding, error) -> {
            if (error != null) {
                getContext().getLog().error("Failed to bind gRPC server on {}:{} - {}", GRPC_HOST, GRPC_PORT, error.getMessage());
                getContext().getSystem().terminate();
            } else {
                getContext().getLog().info("gRPC OrderProcessor server bound to {}", serverBinding.localAddress());
            }
        });
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