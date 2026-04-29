package com.trafficsimulator.vehicle;

public enum VehicleType {
    NORMAL(0.75, 1.10, 2.50),
    LIGHT_TRUCK(0.55, 0.90, 2.00),
    HEAVY_TRUCK(0.35, 0.70, 1.50),
    BUS(0.45, 0.80, 1.80);

    private final double accelerationKmhPerTick;
    private final double brakeKmhPerTick;
    private final double hardBrakeKmhPerTick;

    VehicleType(double accelerationKmhPerTick, double brakeKmhPerTick, double hardBrakeKmhPerTick) {
        this.accelerationKmhPerTick = accelerationKmhPerTick;
        this.brakeKmhPerTick = brakeKmhPerTick;
        this.hardBrakeKmhPerTick = hardBrakeKmhPerTick;
    }

    public double getAccelerationKmhPerTick() {
        return accelerationKmhPerTick;
    }

    public double getBrakeKmhPerTick() {
        return brakeKmhPerTick;
    }

    public double getHardBrakeKmhPerTick() {
        return hardBrakeKmhPerTick;
    }
}
