package at.fhv.sysarch.lab2.homeautomation.grpcdemo;
/*
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.*;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.grpc.GrpcClientSettings;

import java.util.concurrent.CompletionStage;

public class OrderProcessorGrpcClient {

    public static void main(String[] args) throws Exception {
        final ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "OrderProcessorClient");

        // Erstelle den gRPC Client
        OrderServiceClient client = OrderServiceClient.create(
                GrpcClientSettings.fromConfig("orderprocessing.OrderService", system),
                system
        );

        // Test Order
        System.out.println("Sending order...");
        OrderRequest request = OrderRequest.newBuilder()
                .setProductId("P001")
                .setQuantity(5)
                .setUnitPrice(2.50)
                .build();

        // Sende asynchron und handle Response
        CompletionStage<OrderResponse> replyCS = client.processOrder(request);

        replyCS.whenComplete((reply, error) -> {
            if (error == null) {
                System.out.println("\nReceipt received:");
                System.out.println("  Order ID: " + reply.getOrderId());
                System.out.println("  Success: " + reply.getSuccess());
                System.out.println("  Message: " + reply.getMessage());

                if (reply.hasReceipt()) {
                    OrderReceipt receipt = reply.getReceipt();
                    System.out.println("\nReceipt Details:");
                    System.out.println("  Product: " + receipt.getProductId());
                    System.out.println("  Quantity: " + receipt.getQuantity());
                    System.out.println("  Unit Price: " + receipt.getUnitPrice());
                    System.out.println("  Total Price: " + receipt.getTotalPrice());
                }

                // Shutdown system nach 2 Sekunden
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                system.terminate();
            } else {
                System.err.println("Error: " + error.getMessage());
                system.terminate();
            }
        });
    }
}

 */