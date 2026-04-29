package com.trafficsimulator.vehicle;

public enum VehicleType {
    NORMAL(0.22, 0.35, 0.90),
    LIGHT_TRUCK(0.16, 0.25, 0.70),
    HEAVY_TRUCK(0.12, 0.18, 0.55),
    BUS(0.14, 0.20, 0.60);

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
