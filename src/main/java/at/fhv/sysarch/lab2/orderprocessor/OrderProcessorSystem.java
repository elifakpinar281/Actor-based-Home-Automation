package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class OrderProcessorSystem {
    public static void main(String[] args) {
        Config config = ConfigFactory.parseString("pekko.http.server.preview.enable-http2 = on")
                .withFallback(ConfigFactory.load("orderprocessor"));

        ActorSystem<OrderProcessorGuardian.Command> system = ActorSystem.create(OrderProcessorGuardian.create(), "OrderProcessorSystem", config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            system.log().info("Shutdown signal received - terminating OrderProcessor system");
            system.terminate();
        }, "orderprocessor-shutdown"));
    }
}
