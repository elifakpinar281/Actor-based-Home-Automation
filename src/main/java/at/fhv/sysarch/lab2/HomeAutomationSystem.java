package at.fhv.sysarch.lab2;

import at.fhv.sysarch.lab2.homeautomation.HomeAutomationController;
import org.apache.pekko.actor.typed.ActorSystem;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HomeAutomationSystem {

    public static void main(String[] args) {
        ActorSystem<Void> system = ActorSystem.create(HomeAutomationController.create(), "HomeAutomation");

        // Sauberes Herunterfahren (z.B. Strg+C).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            system.log().info("Shutdown signal received — terminating system");
            system.terminate();
            try {
                system.getWhenTerminated().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                system.log().warn("Shutdown did not complete within 5s - forcing exit");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                system.log().warn("Shutdown was interrupted");
            } catch (ExecutionException ex) {
                system.log().error("Termination failed", ex.getCause());
            }
        }, "homeautomation-shutdown"));
    }
}