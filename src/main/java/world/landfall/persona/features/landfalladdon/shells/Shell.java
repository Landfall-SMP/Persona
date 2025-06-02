package world.landfall.persona.features.landfalladdon.shells;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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

    private static final Logger LOGGER = LogUtils.getLogger();
    private final String description;
    private final Map<String, Integer> originWeights;
    public static final int DEFAULT_WEIGHT = 1; // Default weight if an origin is not listed for a shell
    public static final int MAX_WEIGHT = 1000; // Reasonable upper bound for weights
    public static final int MIN_WEIGHT = 0; // Minimum weight (inclusive)

    Shell(String description, Map<String, Integer> originWeights) {
        this.description = Objects.requireNonNull(description, "Shell description cannot be null");
        this.originWeights = validateAndCopyWeights(originWeights);
    }

    /**
     * Validates and creates a defensive copy of the origin weights map.
     */
    private static Map<String, Integer> validateAndCopyWeights(Map<String, Integer> weights) {
        if (weights == null) {
            LOGGER.warn("Null weights map provided, using empty map");
            return new HashMap<>();
        }

        Map<String, Integer> validatedWeights = new HashMap<>();
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            String origin = entry.getKey();
            Integer weight = entry.getValue();

            if (origin == null || origin.isBlank()) {
                LOGGER.warn("Skipping null or blank origin in weights map");
                continue;
            }

            if (weight == null) {
                LOGGER.warn("Null weight for origin {}, using 0", origin);
                weight = 0;
            }

            if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                LOGGER.warn("Weight {} for origin {} is out of bounds [{}, {}], clamping", 
                    weight, origin, MIN_WEIGHT, MAX_WEIGHT);
                weight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
            }

            validatedWeights.put(origin.trim().toUpperCase(), weight);
        }

        return Collections.unmodifiableMap(validatedWeights);
    }

    public String getDescription() {
        return description;
    }

    /**
     * Gets the weight of this shell for a given origin.
     * @param origin The origin string (e.g., "DIVINET_TOUCHED"). Case-insensitive, will be normalized.
     * @return The weight for the origin, or DEFAULT_WEIGHT if not specified (for non-NEUTRAL shells),
     *         or 0 for NEUTRAL if it has no specific weights and other shells are available.
     * @throws IllegalArgumentException if origin is null
     */
    public int getWeight(String origin) {
        if (origin == null) {
            throw new IllegalArgumentException("Origin cannot be null");
        }

        String normalizedOrigin = origin.trim().toUpperCase();
        if (normalizedOrigin.isEmpty()) {
            LOGGER.warn("Empty origin provided, using default weight behavior");
            return this == NEUTRAL ? 0 : DEFAULT_WEIGHT;
        }

        if (this == NEUTRAL) {
            // NEUTRAL should generally have 0 weight unless specifically given one for an origin,
            // or if it's the only shell available (which ShellManager handles).
            return originWeights.getOrDefault(normalizedOrigin, 0);
        }
        
        return originWeights.getOrDefault(normalizedOrigin, DEFAULT_WEIGHT);
    }

    /**
     * Gets all origins that have explicit weights defined for this shell.
     * @return An unmodifiable set of origin names, never null.
     */
    public java.util.Set<String> getDefinedOrigins() {
        return originWeights.keySet(); // Already unmodifiable due to Collections.unmodifiableMap
    }

    /**
     * Checks if this shell has an explicit weight defined for the given origin.
     * @param origin The origin to check, case-insensitive
     * @return true if an explicit weight is defined, false otherwise
     */
    public boolean hasExplicitWeight(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return originWeights.containsKey(origin.trim().toUpperCase());
    }

    /**
     * Gets a defensive copy of all origin weights for this shell.
     * @return A new map containing all origin weights, never null.
     */
    public Map<String, Integer> getAllWeights() {
        return new HashMap<>(originWeights);
    }

    /**
     * Validates that this shell's configuration is consistent.
     * @return true if valid, false if there are issues
     */
    public boolean isValid() {
        try {
            // Check description
            if (description == null || description.isBlank()) {
                LOGGER.error("Shell {} has null or blank description", this.name());
                return false;
            }

            // Check weights
            for (Map.Entry<String, Integer> entry : originWeights.entrySet()) {
                String origin = entry.getKey();
                Integer weight = entry.getValue();

                if (origin == null || origin.isBlank()) {
                    LOGGER.error("Shell {} has null or blank origin", this.name());
                    return false;
                }

                if (weight == null || weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                    LOGGER.error("Shell {} has invalid weight {} for origin {}", 
                        this.name(), weight, origin);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("Error validating shell {}", this.name(), e);
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("Shell{name=%s, description='%s', weights=%d}", 
            this.name(), description, originWeights.size());
    }
} 