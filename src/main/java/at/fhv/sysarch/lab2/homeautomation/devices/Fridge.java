package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.devices.sensor.SpaceSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeightSensor;
import at.fhv.sysarch.lab2.homeautomation.grpc.orderprocessing.OrderServiceClient;
import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.*;
import at.fhv.sysarch.lab2.homeautomation.shared.model.order.*;
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
import java.util.Optional;

public class Fridge extends AbstractBehavior<Fridge.FridgeCommand> {
    public interface FridgeCommand {}

    public record GetProducts(ActorRef<ProductsResponse> replyTo) implements FridgeCommand {}
    public record GetOrderHistory(ActorRef<OrderHistoryResponse> replyTo) implements FridgeCommand {}
    public record GetCapacity(ActorRef<CapacityResponse> replyTo) implements FridgeCommand {}
    public record ConsumeProduct(String productId, int quantity) implements FridgeCommand {}
    public record OrderProducts(Map<String, Integer> items, Optional<ActorRef<OrderResponse>> replyTo) implements FridgeCommand {
        public static OrderProducts fromUser(Map<String, Integer> items, ActorRef<OrderResponse> replyTo) {
            return new OrderProducts(items, Optional.of(replyTo));
        }
        public static OrderProducts autoOrder(Map<String, Integer> items) {
            return new OrderProducts(items, Optional.empty());
        }
    }

    public record OrderCompleted(Order order, Receipt receipt, Optional<ActorRef<OrderResponse>> replyTo) implements FridgeCommand {}
    public record OrderFailed(Order order, String reason, Optional<ActorRef<OrderResponse>> replyTo) implements FridgeCommand {}
    public record OrderProcessorCrashed(Order order, Optional<ActorRef<OrderResponse>> replyTo) implements FridgeCommand {}
    public record ProductsResponse(List<Product> products) {}
    public record OrderHistoryResponse(List<Order> orders) {}
    public record CapacityResponse(int currentItems, int maxItems, double currentWeight, double maxWeight) {}
    public record OrderResponse(boolean success, String message, Receipt receipt) {}

    public static final ServiceKey<FridgeCommand> SERVICE_KEY = ServiceKey.create(FridgeCommand.class, "fridge");

    public static Behavior<FridgeCommand> create(String identifier, int maxItems, double maxWeightKg, OrderServiceClient grpcClient) {
        return Behaviors.setup(context -> new Fridge(context, identifier, maxItems, maxWeightKg, FridgeInventory.defaultProducts(), grpcClient));
    }

    public static Behavior<FridgeCommand> create(String identifier, int maxItems, double maxWeightKg, OrderServiceClient grpcClient, List<Product> initialInventory) {
        return Behaviors.setup(context -> new Fridge(context, identifier, maxItems, maxWeightKg, initialInventory, grpcClient));
    }

    private final String identifier;
    private final int maxItems;
    private final double maxWeightKg;
    private final OrderServiceClient grpcClient;

    private final Map<String, Product> inventory = new HashMap<>();
    private final List<Order> orderHistory = new ArrayList<>();
    private final ActorRef<WeightSensor.WeightSensorCommand> weightSensor;
    private final ActorRef<SpaceSensor.SpaceSensorCommand> spaceSensor;
    private int pendingItemCount = 0;
    private double pendingWeightKg = 0.0;

    private Fridge(ActorContext<FridgeCommand> context, String identifier, int maxItems, double maxWeightKg, List<Product> initialInventory, OrderServiceClient grpcClient) {
        super(context);
        this.identifier = identifier;
        this.maxItems = maxItems;
        this.maxWeightKg = maxWeightKg;
        this.grpcClient = grpcClient;
        for (Product product : initialInventory) {
            inventory.put(product.id(), product);
        }
        this.weightSensor = getContext().spawn(WeightSensor.create(identifier + "-weight-sensor", storedWeightKg()), "weightSensor");
        this.spaceSensor = getContext().spawn(SpaceSensor.create(identifier + "-space-sensor", storedItemCount()), "spaceSensor");
        getContext().getLog().info("Fridge '{}' started - max: {} items, {} kg, initial: {} items / {} kg", identifier, maxItems, maxWeightKg, storedItemCount(), storedWeightKg());
    }

    private int storedItemCount() {
        int total = 0;
        for (Product product : inventory.values()) {
            total += product.quantity();
        }
        return total;
    }

    private double storedWeightKg() {
        double total = 0.0;
        for (Product product : inventory.values()) {
            total += product.totalWeight();
        }
        return total;
    }

    private int committedItemCount() {
        return storedItemCount() + pendingItemCount;
    }

    private double committedWeightKg() {
        return storedWeightKg() + pendingWeightKg;
    }

    private void notifySensors() {
        weightSensor.tell(new WeightSensor.WeightChanged(storedWeightKg()));
        spaceSensor.tell(new SpaceSensor.SpaceChanged(storedItemCount()));
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
                .onMessage(OrderProcessorCrashed.class, this::onOrderProcessorCrashed)
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
        msg.replyTo().tell(new CapacityResponse(storedItemCount(), maxItems, storedWeightKg(), maxWeightKg));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onConsumeProduct(ConsumeProduct msg) {
        Product product = inventory.get(msg.productId());
        if (product == null) {
            getContext().getLog().warn("Fridge '{}': cannot consume - product {} not found", identifier, msg.productId());
            return Behaviors.same();
        }
        if (product.quantity() < msg.quantity()) {
            getContext().getLog().warn("Fridge '{}': cannot consume {} x {} - only {} available", identifier, msg.quantity(), product.name(), product.quantity());
            return Behaviors.same();
        }
        Product updated = product.removeQuantity(msg.quantity());
        inventory.put(updated.id(), updated);
        notifySensors();
        getContext().getLog().info("Fridge '{}': consumed {} x {} (remaining: {})", identifier, msg.quantity(), product.name(), updated.quantity());
        if (updated.quantity() == 0) {
            triggerAutoOrder(updated.id());
        }
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderProducts(OrderProducts msg) {
        List<OrderLineItem> lineItems;
        int totalNewItems;
        double totalNewWeight;
        try {
            ValidationResult validation = validateAndBuildLineItems(msg.items());
            lineItems = validation.lineItems;
            totalNewItems = validation.totalItems;
            totalNewWeight = validation.totalWeight;
        } catch (FridgeException ex) {
            getContext().getLog().warn("Fridge '{}': order validation failed: {}", identifier, ex.getMessage());
            msg.replyTo().ifPresent(replyTo -> replyTo.tell(new OrderResponse(false, ex.getMessage(), null)));
            return Behaviors.same();
        }
        pendingItemCount += totalNewItems;
        pendingWeightKg += totalNewWeight;
        Order order = Order.create(lineItems).withStatus(OrderStatus.PROCESSING);
        orderHistory.add(order);
        ActorRef<OrderProcessor.OrderProcessorCommand> sessionProcessor = getContext().spawnAnonymous(OrderProcessor.create(grpcClient, getContext().getSelf()));
        getContext().watchWith(sessionProcessor, new OrderProcessorCrashed(order, msg.replyTo()));
        sessionProcessor.tell(new OrderProcessor.ProcessOrder(order, msg.replyTo()));
        getContext().getLog().info("Fridge '{}': dispatched order {} ({} positions, €{}) to external processor - reserved {} items / {} kg", identifier, order.orderId(), order.lineItems().size(), order.totalPrice(), totalNewItems, String.format("%.2f", totalNewWeight));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderCompleted(OrderCompleted msg) {
        releaseReservation(msg.order());
        replaceOrderInHistory(msg.order().completed(msg.receipt().receiptId()));
        for (OrderLineItem item : msg.order().lineItems()) {
            Product existing = inventory.get(item.productId());
            if (existing == null) {
                getContext().getLog().error("Fridge '{}': inventory entry for {} missing during order completion - skipping", identifier, item.productId());
                continue;
            }
            Product updated = existing.addQuantity(item.quantity());
            inventory.put(updated.id(), updated);
        }
        notifySensors();
        getContext().getLog().info("Fridge '{}': order {} completed - {}", identifier, msg.order().orderId(), msg.receipt());
        msg.replyTo().ifPresent(replyTo -> replyTo.tell(new OrderResponse(true, "Order processed successfully", msg.receipt())));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderFailed(OrderFailed msg) {
        releaseReservation(msg.order());
        replaceOrderInHistory(msg.order().failed());
        getContext().getLog().warn("Fridge '{}': order {} failed - {}", identifier, msg.order().orderId(), msg.reason());
        msg.replyTo().ifPresent(replyTo -> replyTo.tell(new OrderResponse(false, msg.reason(), null)));
        return Behaviors.same();
    }

    private void releaseReservation(Order order) {
        int items = 0;
        double weight = 0.0;
        for (OrderLineItem item : order.lineItems()) {
            items += item.quantity();
            weight += item.totalWeight();
        }
        pendingItemCount -= items;
        pendingWeightKg -= weight;
        if (pendingItemCount < 0) {
            pendingItemCount = 0;
        }
        if (pendingWeightKg < 0.0) {
            pendingWeightKg = 0.0;
        }
    }

    private void replaceOrderInHistory(Order updated) {
        for (int i = 0; i < orderHistory.size(); i++) {
            if (orderHistory.get(i).orderId().equals(updated.orderId())) {
                orderHistory.set(i, updated);
                return;
            }
        }
    }

    private record ValidationResult(List<OrderLineItem> lineItems, int totalItems, double totalWeight) {}

    private ValidationResult validateAndBuildLineItems(Map<String, Integer> items) throws FridgeException {
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item");
        }
        List<OrderLineItem> lineItems = new ArrayList<>(items.size());
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
            lineItems.add(new OrderLineItem(product.id(), product.name(), quantity, product.price(), product.weight()));
            totalNewItems += quantity;
            totalNewWeight += product.weight() * quantity;
        }

        if (committedItemCount() + totalNewItems > maxItems) {
            throw new InsufficientSpaceException(committedItemCount(), maxItems, totalNewItems);
        }
        if (committedWeightKg() + totalNewWeight > maxWeightKg) {
            throw new InsufficientWeightCapacityException(committedWeightKg(), maxWeightKg, totalNewWeight);
        }
        return new ValidationResult(lineItems, totalNewItems, totalNewWeight);
    }

    private void triggerAutoOrder(String productId) {
        Product product = inventory.get(productId);
        if (product == null) {
            getContext().getLog().warn("Fridge '{}': auto-order skipped - product {} no longer in inventory", identifier, productId);
            return;
        }
        int target = product.initialQuantity();
        int remainingSlots = maxItems - committedItemCount();
        double remainingWeightKg = maxWeightKg - committedWeightKg();
        double neededWeightKg = product.weight() * target;

        if (target > remainingSlots) {
            getContext().getLog().warn("Fridge '{}': auto-order skipped for '{}' - needs {} slots but only {} available", identifier, product.name(), target, remainingSlots);
            return;
        }
        if (neededWeightKg > remainingWeightKg) {
            getContext().getLog().warn("Fridge '{}': auto-order skipped for '{}' - needs {} kg but only {} kg remaining", identifier, product.name(), String.format("%.2f", neededWeightKg), String.format("%.2f", remainingWeightKg));
            return;
        }
        getContext().getLog().info("Fridge '{}': auto-ordering {} x '{}' (restoring to initial stock)", identifier, target, product.name());
        getContext().getSelf().tell(OrderProducts.autoOrder(Map.of(productId, target)));
    }

    private Behavior<FridgeCommand> onOrderProcessorCrashed(OrderProcessorCrashed msg) {
        boolean stillProcessing = orderHistory.stream().filter(o -> o.orderId().equals(msg.order().orderId()))
                .anyMatch(o -> o.status() == OrderStatus.PROCESSING);
        if (!stillProcessing) {
            return Behaviors.same();
        }
        releaseReservation(msg.order());
        replaceOrderInHistory(msg.order().failed());
        String reason = "OrderProcessor actor crashed unexpectedly";
        getContext().getLog().error("Fridge '{}': order {} lost – {}", identifier, msg.order().orderId(), reason);
        msg.replyTo().ifPresent(r -> r.tell(new OrderResponse(false, reason, null)));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onPostStop() {
        getContext().getLog().info("Fridge '{}' stopped", identifier);
        return this;
    }
}