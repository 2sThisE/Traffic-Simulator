package com.trafficsimulator.util;

/**
 * Utility class for converting between real-world units (meters, km/h) 
 * and simulation units (pixels, ticks).
 */
public class UnitConverter {
    
    // Default ratio: 1 meter = 20 pixels
    public static final double PIXELS_PER_METER = 20.0;
    
    // Tick interval from GlobalTimer (0.1s)
    public static final double TICK_INTERVAL_SECONDS = GlobalTimer.TICKLATE;

    /**
     * Converts meters to pixels.
     */
    public static double toPixel(double meter) {
        return meter * PIXELS_PER_METER;
    }

    /**
     * Converts pixels to meters.
     */
    public static double toMeter(double pixel) {
        return pixel / PIXELS_PER_METER;
    }

    /**
     * Converts km/h to m/s.
     */
    public static double kmhToMs(double kmh) {
        return kmh / 3.6;
    }

    /**
     * Converts m/s to km/h.
     */
    public static double msToKmh(double ms) {
        return ms * 3.6;
    }

    /**
     * Converts velocity (m/s) to movement per tick (pixels/tick).
     */
    public static double toPixelPerTick(double ms) {
        // (meters / second) * (pixels / meter) * (seconds / tick)
        return ms * PIXELS_PER_METER * TICK_INTERVAL_SECONDS;
    }

    /**
     * Converts movement per tick (pixels/tick) back to velocity (km/h).
     */
    public static double toKmh(double pixelPerTick) {
        double ms = pixelPerTick / (PIXELS_PER_METER * TICK_INTERVAL_SECONDS);
        return msToKmh(ms);
    }
}
