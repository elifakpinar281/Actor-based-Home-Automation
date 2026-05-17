package at.fhv.sysarch.lab2;

import at.fhv.sysarch.lab2.homeautomation.HomeAutomationController;
import org.apache.pekko.actor.typed.ActorSystem;

public class HomeAutomationSystem {

    public static void main(String[] args) {
        ActorSystem<Void> system = ActorSystem.create(HomeAutomationController.create(), "HomeAutomation");

        // Sauberes Herunterfahren (z.B. Strg+C).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            system.log().info("Shutdown signal received — terminating system");
            system.terminate();
        }, "homeautomation-shutdown"));
    }
}