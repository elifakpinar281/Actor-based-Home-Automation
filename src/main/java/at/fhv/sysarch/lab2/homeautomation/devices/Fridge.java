package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Product;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Receipt;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.FridgeException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InsufficientSpaceException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InsufficientWeightCapacityException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidOrderException;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Fridge extends AbstractBehavior<Fridge.FridgeCommand> {

    public interface FridgeCommand { }

    // Anfragen (Request-Response)
    public record GetProducts(ActorRef<ProductsResponse> replyTo) implements FridgeCommand { }
    public record GetOrderHistory(ActorRef<OrderHistoryResponse> replyTo) implements FridgeCommand { }

    public record ConsumeProduct(String productId, int quantity) implements FridgeCommand { }
    public record OrderProducts(Map<String, Integer> items, ActorRef<OrderResponse> replyTo) implements FridgeCommand { }

    // Responses
    public record ProductsResponse(List<Product> products) { }
    public record OrderHistoryResponse(List<Order> orders) { }
    public record OrderResponse(boolean success, String message, Receipt receipt) { }

    public static Behavior<FridgeCommand> create(
            String identifier,
            int maxItems,
            double maxWeightKg,
            ActorRef<OrderProcessor.OrderProcessorCommand> orderProcessorActor) {
        return Behaviors.setup(context ->
                new Fridge(context, identifier, maxItems, maxWeightKg, orderProcessorActor)
        );
    }

    private final String identifier;
    private final int maxItems;
    private final double maxWeightKg;
    private final ActorRef<OrderProcessor.OrderProcessorCommand> orderProcessorActor;

    private final Map<String, Product> inventory = new HashMap<>();
    private final List<Order> orderHistory = new ArrayList<>();
    private int currentItemCount = 0;
    private double currentWeightKg = 0.0;

    public Fridge(
            ActorContext<FridgeCommand> context,
            String identifier,
            int maxItems,
            double maxWeightKg,
            ActorRef<OrderProcessor.OrderProcessorCommand> orderProcessorActor) {
        super(context);
        this.identifier = identifier;
        this.maxItems = maxItems;
        this.maxWeightKg = maxWeightKg;
        this.orderProcessorActor = orderProcessorActor;

        initializeSampleProducts();
        getContext().getLog().info("Fridge Actor '{}' started - Max: {} items, {} kg",
                identifier, maxItems, maxWeightKg);
    }

    private void initializeSampleProducts() {
        addProduct(new Product("P001", "Milk", 1.0, 2.50, 3));
        addProduct(new Product("P002", "Cheese", 0.5, 8.99, 2));
        addProduct(new Product("P003", "Butter", 0.25, 4.50, 1));
        addProduct(new Product("P004", "Eggs", 0.05, 0.20, 12));
    }

    private void addProduct(Product product) {
        inventory.put(product.getId(), product);
        currentItemCount += product.getQuantity();
        currentWeightKg += product.getTotalWeight();
    }

    @Override
    public Receive<FridgeCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetProducts.class, this::onGetProducts)
                .onMessage(GetOrderHistory.class, this::onGetOrderHistory)
                .onMessage(ConsumeProduct.class, this::onConsumeProduct)
                .onMessage(OrderProducts.class, this::onOrderProducts)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<FridgeCommand> onGetProducts(GetProducts msg) {
        List<Product> products = new ArrayList<>(inventory.values());
        msg.replyTo.tell(new ProductsResponse(products));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onGetOrderHistory(GetOrderHistory msg) {
        List<Order> history = new ArrayList<>(orderHistory);
        msg.replyTo.tell(new OrderHistoryResponse(history));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onConsumeProduct(ConsumeProduct msg) {
        Product product = inventory.get(msg.productId);

        if (product == null) {
            getContext().getLog().warn("Fridge '{}': Product {} not found", identifier, msg.productId);
            return Behaviors.same();
        }

        if (product.getQuantity() < msg.quantity) {
            getContext().getLog().warn("Fridge '{}': Not enough quantity of {}",
                    identifier, product.getName());
            return Behaviors.same();
        }

        // Konsumieren
        product.removeQuantity(msg.quantity);
        currentItemCount -= msg.quantity;
        currentWeightKg -= (product.getWeight() * msg.quantity);

        getContext().getLog().info("Fridge '{}': Consumed {} x {}",
                identifier, msg.quantity, product.getName());

        // Auto-order wenn Produkt aufgebraucht
        if (product.getQuantity() == 0) {
            autoOrderProduct(product.getId(), 5);  // Standard: 5 Stück nachbestellen
        }

        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderProducts(OrderProducts msg) {
        try {
            validateOrder(msg.items);

            Order order = new Order(msg.items);
            orderHistory.add(order);
            order.setStatus(Order.OrderStatus.PROCESSING);

            // gRPC zum externen OrderProcessor senden
            orderProcessorActor.tell(
                    new OrderProcessor.ProcessOrder(order, msg.items, msg.replyTo, this.identifier)
            );

            return Behaviors.same();

        } catch (FridgeException e) {
            getContext().getLog().error("Order validation failed: {}", e.getMessage());
            msg.replyTo.tell(new OrderResponse(false, e.getMessage(), null));
            return Behaviors.same();
        }
    }

    private void validateOrder(Map<String, Integer> items) throws FridgeException {
        // Prüfe ob alle Produkte existieren
        for (String productId : items.keySet()) {
            if (!inventory.containsKey(productId)) {
                throw new InvalidOrderException("Product " + productId + " not found");
            }

            if (items.get(productId) <= 0) {
                throw new InvalidOrderException("Quantity must be positive");
            }
        }

        // Berechne neue Gewichte und Mengen
        int totalNewItems = items.values().stream().mapToInt(Integer::intValue).sum();
        if (currentItemCount + totalNewItems > maxItems) {
            throw new InsufficientSpaceException(currentItemCount, maxItems, totalNewItems);
        }

        double totalNewWeight = 0.0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = inventory.get(entry.getKey());
            totalNewWeight += (p.getWeight() * entry.getValue());
        }

        if (currentWeightKg + totalNewWeight > maxWeightKg) {
            throw new InsufficientWeightCapacityException(currentWeightKg, maxWeightKg, totalNewWeight);
        }
    }

    private void autoOrderProduct(String productId, int quantity) {
        Map<String, Integer> items = new HashMap<>();
        items.put(productId, quantity);

        getContext().getLog().info("Fridge '{}': Auto-ordering {} x product {}",
                identifier, quantity, productId);

        // Dummy actor ref für auto-order responses
        ActorRef<OrderResponse> dummyRef = getContext().getSelf().unsafeUpcast();
        getContext().getSelf().tell(new OrderProducts(items, dummyRef));
    }

    public void completeOrder(Order order, Receipt receipt) {
        order.setStatus(Order.OrderStatus.COMPLETED);

        // Inventar aktualisieren
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            Product p = inventory.get(entry.getKey());
            if (p != null) {
                p.addQuantity(entry.getValue());
                currentItemCount += entry.getValue();
                currentWeightKg += (p.getWeight() * entry.getValue());
            }
        }

        getContext().getLog().info("Fridge '{}': Order {} completed - {}",
                identifier, order.getOrderId(), receipt);
    }

    private Fridge onPostStop() {
        getContext().getLog().info("Fridge Actor '{}' stopped", identifier);
        return this;
    }
}