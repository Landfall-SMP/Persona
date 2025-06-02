package world.landfall.persona.features.landfalladdon.shells;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

public enum Shell {
    // Weights: <OriginName (Programmatic Enum Name), Weight>
    SWIFT("Slightly faster movement", 
        Map.of("DIVINET_TOUCHED", 5, "NULLBORN", 15, "MOONSPAWN", 10)),
    SLUGGISH("Slightly slower movement", 
        Map.of("DIVINET_TOUCHED", 15, "NULLBORN", 5, "MOONSPAWN", 10)),
    ROBUST("Slightly increased resilience", 
        Map.of("DIVINET_TOUCHED", 5, "NULLBORN", 15, "MOONSPAWN", 10)),
    FRAIL("Slightly decreased resilience", 
        Map.of("DIVINET_TOUCHED", 15, "NULLBORN", 5, "MOONSPAWN", 10)),
    KEEN_EYES("Slightly improved perception", 
        Map.of("DIVINET_TOUCHED", 5, "NULLBORN", 15, "MOONSPAWN", 10)),
    CLUMSY("Slightly more prone to mishaps", 
        Map.of("DIVINET_TOUCHED", 15, "NULLBORN", 5, "MOONSPAWN", 10)),
    NEUTRAL("No particular trait", 
        Collections.emptyMap()); // NEUTRAL has 0 weight effectively if other shells are weighted

    private final String description;
    private final Map<String, Integer> originWeights;
    public static final int DEFAULT_WEIGHT = 1; // Default weight if an origin is not listed for a shell, or shell has no specific weights for any origin.

    Shell(String description, Map<String, Integer> originWeights) {
        this.description = description;
        this.originWeights = new HashMap<>(originWeights);
    }

    public String getDescription() {
        return description;
    }

    /**
     * Gets the weight of this shell for a given origin.
     * @param origin The origin string (e.g., "DIVINET_TOUCHED"). Case-sensitive.
     * @return The weight for the origin, or DEFAULT_WEIGHT if not specified (for non-NEUTRAL shells),
     *         or 0 for NEUTRAL if it has no specific weights and other shells are available.
     */
    public int getWeight(String origin) {
        if (this == NEUTRAL) {
            // NEUTRAL should generally have 0 weight unless specifically given one for an origin,
            // or if it's the only shell available (which ShellManager handles).
            return originWeights.getOrDefault(origin, 0);
        }
        return originWeights.getOrDefault(origin, DEFAULT_WEIGHT);
    }
} 