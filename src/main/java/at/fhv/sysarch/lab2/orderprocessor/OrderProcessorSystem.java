package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class OrderProcessorSystem {
    private static final String CONFIG_RESOURCE = "orderprocessor";
    private static final String ACTOR_SYSTEM_NAME = "OrderProcessorSystem";
    private static final String SHUTDOWN_HOOK_THREAD_NAME = "orderprocessor-shutdown";

    private OrderProcessorSystem() {}

    public static void main(String[] args) {
        Config config = ConfigFactory
                .parseString("pekko.http.server.preview.enable-http2 = on")
                .withFallback(ConfigFactory.load(CONFIG_RESOURCE));

        ActorSystem<OrderProcessorGuardian.Command> system = ActorSystem.create(
                OrderProcessorGuardian.create(),
                ACTOR_SYSTEM_NAME,
                config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            system.log().info("Shutdown signal received - terminating OrderProcessor system");
            system.terminate();
        }, SHUTDOWN_HOOK_THREAD_NAME));
    }
}