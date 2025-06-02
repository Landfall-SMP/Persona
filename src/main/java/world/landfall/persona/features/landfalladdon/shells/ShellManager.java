package world.landfall.persona.features.landfalladdon.shells;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Objects;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ShellManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Shell> SHELLS_LIST = List.of(Shell.values()); // Immutable list
    private static final String UNKNOWN_ORIGIN = "UNKNOWN_ORIGIN";

    /**
     * Gets a random shell from the available shells based on the character's origin and defined weights.
     * Excludes NEUTRAL shell from weighted selection if other shells are available and have weight.
     * @param characterOrigin The origin of the character (e.g., "HUMAN", "ELF"). Can be null or blank.
     * @return A random Shell, never null. Returns Shell.NEUTRAL if no suitable shells are found or in case of error.
     * @throws IllegalStateException if the Shell enum has no values (should never happen)
     */
    public static Shell getRandomShell(String characterOrigin) {
        if (SHELLS_LIST.isEmpty()) {
            LOGGER.error("Shell enum has no values - this should never happen");
            throw new IllegalStateException("No shells available in Shell enum");
        }

        final String effectiveOrigin = sanitizeOrigin(characterOrigin);
        LOGGER.debug("Getting random shell for origin: {}", effectiveOrigin);

        try {
            List<Shell> weightedList = buildWeightedShellList(effectiveOrigin);
            
            if (weightedList.isEmpty()) {
                LOGGER.debug("No weighted shells found for origin {}, falling back to non-NEUTRAL shells", effectiveOrigin);
                return getFallbackShell();
            }

            Shell selectedShell = weightedList.get(ThreadLocalRandom.current().nextInt(weightedList.size()));
            LOGGER.debug("Selected shell {} for origin {}", selectedShell, effectiveOrigin);
            return selectedShell;
            
        } catch (Exception e) {
            LOGGER.error("Error selecting random shell for origin {}, falling back to NEUTRAL", effectiveOrigin, e);
            return Shell.NEUTRAL;
        }
    }

    /**
     * Sanitizes the origin input to prevent null/blank issues.
     */
    private static String sanitizeOrigin(String characterOrigin) {
        if (characterOrigin == null || characterOrigin.isBlank()) {
            return UNKNOWN_ORIGIN;
        }
        return characterOrigin.trim().toUpperCase();
    }

    /**
     * Builds a weighted list of shells for the given origin, excluding NEUTRAL if other options exist.
     */
    private static List<Shell> buildWeightedShellList(String effectiveOrigin) {
        Objects.requireNonNull(effectiveOrigin, "effectiveOrigin cannot be null");
        
        List<Shell> weightedList = new ArrayList<>();
        boolean hasNonNeutralWeights = false;

        // First pass: check if any non-NEUTRAL shells have weights
        for (Shell shell : SHELLS_LIST) {
            if (shell != Shell.NEUTRAL && shell.getWeight(effectiveOrigin) > 0) {
                hasNonNeutralWeights = true;
                break;
            }
        }

        // Second pass: build weighted list
        for (Shell shell : SHELLS_LIST) {
            int weight = shell.getWeight(effectiveOrigin);
            
            // Skip NEUTRAL if other shells have weights for this origin
            if (shell == Shell.NEUTRAL && hasNonNeutralWeights) {
                continue;
            }
            
            if (weight > 0) {
                // Add shell 'weight' number of times to the list
                for (int i = 0; i < weight; i++) {
                    weightedList.add(shell);
                }
            }
        }

        return weightedList;
    }

    /**
     * Fallback method when no weighted shells are available.
     * Tries to return a non-NEUTRAL shell if possible.
     */
    private static Shell getFallbackShell() {
        List<Shell> nonNeutralShells = SHELLS_LIST.stream()
            .filter(s -> s != Shell.NEUTRAL)
            .toList();
            
        if (!nonNeutralShells.isEmpty()) {
            Shell fallback = nonNeutralShells.get(ThreadLocalRandom.current().nextInt(nonNeutralShells.size()));
            LOGGER.debug("Using fallback shell: {}", fallback);
            return fallback;
        }
        
        LOGGER.debug("No non-NEUTRAL shells available, using NEUTRAL");
        return Shell.NEUTRAL;
    }

    /**
     * Gets a list of all available shells.
     * @return An unmodifiable list of all shells, never null.
     */
    public static List<Shell> getAllShells() {
        return SHELLS_LIST; // Already immutable
    }

    /**
     * Validates that a shell exists and is not null.
     * @param shell The shell to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidShell(Shell shell) {
        return shell != null && SHELLS_LIST.contains(shell);
    }
} 