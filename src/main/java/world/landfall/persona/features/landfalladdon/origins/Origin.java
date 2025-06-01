package world.landfall.persona.features.landfalladdon.origins;

import java.util.Arrays;
import java.util.Optional;

public enum Origin {
    DIVINET_TOUCHED("Divinet-Touched", 
                    "Descendants of those genetically attuned to the Divinets. Resonate faintly with divine tech, giving them the lesser power of all combined Divinets. Lower Reboot Limit. Higher chance of negative traits but have abilities echoing the Divinets."),
    NULLBORN("Nullborn", 
             "Engineered or naturally born without Divinet-linked DNA. Immune to divine signal interference. High Reboot Limit. May not enter Divinet ruins without instability. Higher chance of positive traits."),
    MOONSPAWN("Moonspawn", 
              "Birthed in lunar colonies, designed for adaptation. Harmonized genome allows interaction with Divinet zones without side effects. Moderate Reboot Limit. No trait bias.");

    private final String displayName;
    private final String description;

    Origin(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static Optional<Origin> fromString(String name) {
        return Arrays.stream(values())
                .filter(origin -> origin.name().equalsIgnoreCase(name) || origin.displayName.equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return this.displayName;
    }
} 