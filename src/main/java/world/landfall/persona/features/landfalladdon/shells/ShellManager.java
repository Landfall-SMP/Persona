package world.landfall.persona.features.landfalladdon.shells;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class ShellManager {

    private static final List<Shell> SHELLS_LIST = Arrays.asList(Shell.values());
    private static final Random RANDOM = new Random();

    /**
     * Gets a random shell from the available shells based on the character's origin and defined weights.
     * Excludes NEUTRAL shell from weighted selection if other shells are available and have weight.
     * @param characterOrigin The origin of the character (e.g., "HUMAN", "ELF").
     * @return A random Shell, or Shell.NEUTRAL if no suitable shells are found or in case of error.
     */
    public static Shell getRandomShell(String characterOrigin) {
        if (SHELLS_LIST.isEmpty()) {
            return Shell.NEUTRAL; // Should not happen if Shell enum has values
        }

        String effectiveOrigin = (characterOrigin == null || characterOrigin.isBlank()) ? "UNKNOWN_ORIGIN" : characterOrigin;

        List<Shell> weightedList = new ArrayList<>();
        int totalWeight = 0;

        for (Shell shell : SHELLS_LIST) {
            if (shell == Shell.NEUTRAL && SHELLS_LIST.size() > 1) { // Only consider NEUTRAL if it's the only option or specifically weighted
                // If NEUTRAL has specific weights (unusual), it might be included by getWeight logic
                // Otherwise, we generally want to avoid rolling NEUTRAL if other shells are possible.
                // Let's ensure NEUTRAL is only chosen if it's the sole option or if all others have 0 weight.
            }

            int weight = shell.getWeight(effectiveOrigin);
            if (weight > 0) {
                if (shell == Shell.NEUTRAL && SHELLS_LIST.stream().anyMatch(s -> s != Shell.NEUTRAL && s.getWeight(effectiveOrigin) > 0)){
                    // Don't add NEUTRAL to weighted list if other shells have weights for this origin
                    continue;
                }
                for (int i = 0; i < weight; i++) {
                    weightedList.add(shell);
                }
                totalWeight += weight;
            }
        }

        if (weightedList.isEmpty() || totalWeight == 0) {
            // Fallback: if no shells have positive weight for this origin, or list is empty
            // Try to return a non-NEUTRAL shell if possible, otherwise NEUTRAL.
            List<Shell> nonNeutralShells = SHELLS_LIST.stream().filter(s -> s != Shell.NEUTRAL).toList();
            if (!nonNeutralShells.isEmpty()) {
                return nonNeutralShells.get(RANDOM.nextInt(nonNeutralShells.size()));
            }
            return Shell.NEUTRAL; // Last resort
        }

        return weightedList.get(RANDOM.nextInt(totalWeight));
    }

    /**
     * Gets a list of all available shells.
     * @return An unmodifiable list of all shells.
     */
    public static List<Shell> getAllShells() {
        return Collections.unmodifiableList(SHELLS_LIST); // Ensure it's unmodifiable if returned directly
    }
} 