package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.OrderStatus;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Product;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Receipt;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Fridge extends AbstractBehavior<Fridge.FridgeCommand> {

    public interface FridgeCommand {}

    public record GetProducts(ActorRef<ProductsResponse> replyTo) implements FridgeCommand {}
    public record GetOrderHistory(ActorRef<OrderHistoryResponse> replyTo) implements FridgeCommand {}
    public record GetCapacity(ActorRef<CapacityResponse> replyTo) implements FridgeCommand {}
    public record ConsumeProduct(String productId, int quantity) implements FridgeCommand {}
    public record OrderProducts(Map<String, Integer> items, ActorRef<OrderResponse> replyTo) implements FridgeCommand {}
    public record OrderCompleted(Order order, Receipt receipt, ActorRef<OrderResponse> replyTo) implements FridgeCommand {}
    public record OrderFailed(Order order, String reason, ActorRef<OrderResponse> replyTo) implements FridgeCommand {}

    public record ProductsResponse(List<Product> products) {}
    public record OrderHistoryResponse(List<Order> orders) {}
    public record CapacityResponse(int currentItems, int maxItems, double currentWeight, double maxWeight) {}
    public record OrderResponse(boolean success, String message, Receipt receipt) {}

    public static final ServiceKey<FridgeCommand> SERVICE_KEY = ServiceKey.create(FridgeCommand.class, "fridge");

    private static final int AUTO_REORDER_QUANTITY = 5;

    public static Behavior<FridgeCommand> create(String identifier, int maxItems, double maxWeightKg) {
        return Behaviors.setup(context -> new Fridge(context, identifier, maxItems, maxWeightKg, defaultInventory()));
    }

    public static Behavior<FridgeCommand> create(String identifier, int maxItems, double maxWeightKg, List<Product> initialInventory) {
        return Behaviors.setup(context -> new Fridge(context, identifier, maxItems, maxWeightKg, initialInventory));
    }

    private final String identifier;
    private final int maxItems;
    private final double maxWeightKg;

    private final Map<String, Product> inventory = new HashMap<>();
    private final List<Order> orderHistory = new ArrayList<>();
    private int currentItemCount = 0;
    private double currentWeightKg = 0.0;

    private Fridge(ActorContext<FridgeCommand> context, String identifier, int maxItems, double maxWeightKg, List<Product> initialInventory) {
        super(context);
        this.identifier = identifier;
        this.maxItems = maxItems;
        this.maxWeightKg = maxWeightKg;
        for (Product product : initialInventory) {
            addToInventory(product);
        }
        getContext().getLog().info("Fridge '{}' started — max: {} items, {} kg, initial: {} items / {} kg", identifier, maxItems, maxWeightKg, currentItemCount, currentWeightKg);
    }

    private static List<Product> defaultInventory() {
        return List.of(
                new Product("P001", "Milk",          1.03, 1.49, 2),
                new Product("P002", "Apples",        0.18, 0.40, 6),
                new Product("P003", "Sourdough",     0.55, 3.20, 0),
                new Product("P004", "Gruyère",       0.30, 5.80, 1),
                new Product("P005", "Eggs (6er)",    0.42, 2.10, 1),
                new Product("P006", "Chicken Breast", 0.50, 7.40, 0),
                new Product("P007", "Salmon Filet",  0.35, 8.90, 8),
                new Product("P008", "Mineral Water", 1.50, 0.99, 3),
                new Product("P009", "Carrots",       1.00, 1.80, 0)
        );
    }

    private void addToInventory(Product product) {
        inventory.put(product.id(), product);
        currentItemCount += product.quantity();
        currentWeightKg += product.totalWeight();
    }

    @Override
    public Receive<FridgeCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetProducts.class, this::onGetProducts)
                .onMessage(GetOrderHistory.class, this::onGetOrderHistory)
                .onMessage(GetCapacity.class, this::onGetCapacity)
                .onMessage(ConsumeProduct.class, this::onConsumeProduct)
                .onMessage(OrderProducts.class, this::onOrderProducts)
                .onMessage(OrderCompleted.class, this::onOrderCompleted)
                .onMessage(OrderFailed.class, this::onOrderFailed)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<FridgeCommand> onGetProducts(GetProducts msg) {
        msg.replyTo().tell(new ProductsResponse(new ArrayList<>(inventory.values())));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onGetOrderHistory(GetOrderHistory msg) {
        msg.replyTo().tell(new OrderHistoryResponse(new ArrayList<>(orderHistory)));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onGetCapacity(GetCapacity msg) {
        msg.replyTo().tell(new CapacityResponse(currentItemCount, maxItems, currentWeightKg, maxWeightKg));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onConsumeProduct(ConsumeProduct msg) {
        Product product = inventory.get(msg.productId());
        if (product == null) {
            getContext().getLog().warn("Fridge '{}': cannot consume — product {} not found", identifier, msg.productId());
            return Behaviors.same();
        }
        if (product.quantity() < msg.quantity()) {
            getContext().getLog().warn("Fridge '{}': cannot consume {} x {} — only {} available", identifier, msg.quantity(), product.name(), product.quantity());
            return Behaviors.same();
        }

        Product updated = product.removeQuantity(msg.quantity());
        inventory.put(updated.id(), updated);
        currentItemCount -= msg.quantity();
        currentWeightKg -= product.weight() * msg.quantity();

        getContext().getLog().info("Fridge '{}': consumed {} x {} (remaining: {})", identifier, msg.quantity(), product.name(), updated.quantity());

        if (updated.quantity() == 0) {
            triggerAutoOrder(updated.id());
        }
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderProducts(OrderProducts msg) {
        try {
            validateOrder(msg.items());
        } catch (FridgeException ex) {
            getContext().getLog().warn("Fridge '{}': order validation failed: {}", identifier, ex.getMessage());
            if (msg.replyTo() != null) {
                msg.replyTo().tell(new OrderResponse(false, ex.getMessage(), null));
            }
            return Behaviors.same();
        }

        double totalPrice = computeTotalPrice(msg.items());
        Order order = Order.create(msg.items(), totalPrice).withStatus(OrderStatus.PROCESSING);
        orderHistory.add(order);

        Map<String, Double> unitPrices = new HashMap<>();
        for (String productId : msg.items().keySet()) {
            unitPrices.put(productId, inventory.get(productId).price());
        }

        ActorRef<OrderProcessor.OrderProcessorCommand> sessionProcessor = getContext().spawnAnonymous(OrderProcessor.create(getContext().getSelf()));
        sessionProcessor.tell(new OrderProcessor.ProcessOrder(order, unitPrices, msg.replyTo()));

        getContext().getLog().info("Fridge '{}': dispatched order {} ({} items, €{}) to external processor", identifier, order.orderId(), msg.items().size(), totalPrice);
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderCompleted(OrderCompleted msg) {
        replaceOrderInHistory(msg.order().completed(msg.receipt().receiptId()));

        for (Map.Entry<String, Integer> entry : msg.order().items().entrySet()) {
            Product product = inventory.get(entry.getKey());
            if (product != null) {
                Product updated = product.addQuantity(entry.getValue());
                inventory.put(updated.id(), updated);
                currentItemCount += entry.getValue();
                currentWeightKg += product.weight() * entry.getValue();
            }
        }

        getContext().getLog().info("Fridge '{}': order {} completed — {}", identifier, msg.order().orderId(), msg.receipt());

        if (msg.replyTo() != null) {
            msg.replyTo().tell(new OrderResponse(true, "Order processed successfully", msg.receipt()));
        }
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderFailed(OrderFailed msg) {
        replaceOrderInHistory(msg.order().failed());
        getContext().getLog().warn("Fridge '{}': order {} failed — {}",
                identifier, msg.order().orderId(), msg.reason());

        if (msg.replyTo() != null) {
            msg.replyTo().tell(new OrderResponse(false, msg.reason(), null));
        }
        return Behaviors.same();
    }

    private void replaceOrderInHistory(Order updated) {
        for (int i = 0; i < orderHistory.size(); i++) {
            if (orderHistory.get(i).orderId().equals(updated.orderId())) {
                orderHistory.set(i, updated);
                return;
            }
        }
    }

    private void validateOrder(Map<String, Integer> items) throws FridgeException {
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item");
        }

        int totalNewItems = 0;
        double totalNewWeight = 0.0;

        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String productId = entry.getKey();
            Integer quantity = entry.getValue();

            if (quantity == null || quantity <= 0) {
                throw new InvalidOrderException("Quantity must be positive for product " + productId);
            }
            Product product = inventory.get(productId);
            if (product == null) {
                throw new ProductNotAvailableException(productId, quantity, 0);
            }
            totalNewItems += quantity;
            totalNewWeight += product.weight() * quantity;
        }

        if (currentItemCount + totalNewItems > maxItems) {
            throw new InsufficientSpaceException(currentItemCount, maxItems, totalNewItems);
        }
        if (currentWeightKg + totalNewWeight > maxWeightKg) {
            throw new InsufficientWeightCapacityException(currentWeightKg, maxWeightKg, totalNewWeight);
        }
    }

    private double computeTotalPrice(Map<String, Integer> items) {
        double total = 0.0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            total += inventory.get(entry.getKey()).price() * entry.getValue();
        }
        return total;
    }

    private void triggerAutoOrder(String productId) {
        Map<String, Integer> items = Map.of(productId, AUTO_REORDER_QUANTITY);
        getContext().getLog().info("Fridge '{}': auto-ordering {} x {} (out of stock)", identifier, AUTO_REORDER_QUANTITY, productId);
        getContext().getSelf().tell(new OrderProducts(items, null));
    }

    private Behavior<FridgeCommand> onPostStop() {
        getContext().getLog().info("Fridge '{}' stopped", identifier);
        return this;
    }
}