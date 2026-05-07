package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class InsufficientWeightCapacityException extends FridgeException {
    public InsufficientWeightCapacityException(double currentWeight, double maxWeight, double attemptedAddition) {
        super(
                String.format(
                        "Insufficient weight capacity in fridge. Current: %.2f kg, Max: %.2f kg, Attempted addition: %.2f kg",
                        currentWeight, maxWeight, attemptedAddition
                ),
                ErrorCode.FRIDGE002
        );
    }
}