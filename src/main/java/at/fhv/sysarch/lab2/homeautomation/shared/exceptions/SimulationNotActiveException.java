package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class SimulationNotActiveException extends DomainException {
    public SimulationNotActiveException() {
        super("Cannot process simulated values because the simulation is currently disabled.", ErrorCode.ENV003);
    }
}
