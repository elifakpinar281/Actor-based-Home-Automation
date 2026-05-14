package at.fhv.sysarch.lab2.homeautomation.grpcdemo;

/*
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderServiceHandlerFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.concurrent.CompletionStage;

public class OrderProcessorGrpcServer {

    public static void main(String[] args) throws Exception {
        Config conf = ConfigFactory.parseString("pekko.http.server.preview.enable-http2 = on")
                .withFallback(ConfigFactory.load());
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "OrderProcessorServer", conf);
        new OrderProcessorGrpcServer(system).run();
    }

    final ActorSystem<?> system;

    public OrderProcessorGrpcServer(ActorSystem<?> system) {
        this.system = system;
    }

    public CompletionStage<ServerBinding> run() throws Exception {

        // Erstelle die Service Implementation
        Function<HttpRequest, CompletionStage<HttpResponse>> service =
                OrderServiceHandlerFactory.create(
                        new OrderProcessorServiceImpl(system),
                        system);

        // Starte den Server auf Port 50051
        CompletionStage<ServerBinding> bound =
                Http.get(system)
                        .newServerAt("127.0.0.1", 50051)
                        .bind(service);

        bound.thenAccept(binding ->
                System.out.println("gRPC OrderProcessor Server bound to: " + binding.localAddress())
        );

        return bound;
    }
}

 */