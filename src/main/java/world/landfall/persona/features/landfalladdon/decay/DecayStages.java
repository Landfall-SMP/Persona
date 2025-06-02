package world.landfall.persona.features.landfalladdon.decay;

public enum DecayStages {
    STABLE,
    MILD,
    MODERATE,
    HIGH,
    SEVERE;

    public String getDisplayName() {
        return switch (this) {
            case STABLE -> "Stable";
            case MILD -> "Mild";
            case MODERATE -> "Moderate";
            case HIGH -> "High";
            case SEVERE -> "Severe";
        };
    }
} 