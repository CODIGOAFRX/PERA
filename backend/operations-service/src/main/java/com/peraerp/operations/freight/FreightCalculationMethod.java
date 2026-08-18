package com.peraerp.operations.freight;

public enum FreightCalculationMethod {
    FIXED,
    PER_KG,
    PER_M3,
    PER_KM,
    FIXED_PLUS_PER_KG,
    FIXED_PLUS_PER_M3,
    FIXED_PLUS_PER_KM;

    public boolean requiresFixedAmount() {
        return this == FIXED || name().startsWith("FIXED_PLUS_");
    }

    public boolean requiresUnitAmount() {
        return this != FIXED;
    }

    public Metric metric() {
        return switch (this) {
            case FIXED -> Metric.NONE;
            case PER_KG, FIXED_PLUS_PER_KG -> Metric.WEIGHT_KG;
            case PER_M3, FIXED_PLUS_PER_M3 -> Metric.VOLUME_M3;
            case PER_KM, FIXED_PLUS_PER_KM -> Metric.DISTANCE_KM;
        };
    }

    public enum Metric {
        NONE,
        WEIGHT_KG,
        VOLUME_M3,
        DISTANCE_KM
    }
}
