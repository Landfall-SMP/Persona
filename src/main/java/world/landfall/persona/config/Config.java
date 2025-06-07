package world.landfall.persona.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // General Settings
    public static final ModConfigSpec.IntValue MAX_CHARACTERS_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue ENABLE_CHARACTER_DELETION;
    public static final ModConfigSpec.ConfigValue<String> NAME_VALIDATION_REGEX;

    // Core Features - Display Name System
    public static final ModConfigSpec.BooleanValue ENABLE_NAME_SYSTEM;
    public static final ModConfigSpec.BooleanValue SHOW_USERNAME_IN_TABLIST;
    public static final ModConfigSpec.ConfigValue<String> TABLIST_NAME_COLOR;

    // Character Features
    public static final ModConfigSpec.BooleanValue ENABLE_INVENTORY_SYSTEM;
    public static final ModConfigSpec.BooleanValue ENABLE_LOCATION_SYSTEM;
    public static final ModConfigSpec.BooleanValue ENABLE_LANDFALL_ADDONS;

    // Aging System Settings
    public static final ModConfigSpec.BooleanValue ENABLE_AGING_SYSTEM;
    public static final ModConfigSpec.DoubleValue TIME_PASSING_RATIO;
    public static final ModConfigSpec.DoubleValue MIN_CHARACTER_AGE;
    public static final ModConfigSpec.DoubleValue MAX_CHARACTER_AGE;
    public static final ModConfigSpec.DoubleValue DEFAULT_CHARACTER_AGE;

    static {
        // General Settings Section
        BUILDER.push("General Settings");
        
        MAX_CHARACTERS_PER_PLAYER = BUILDER
            .comment("Maximum number of characters allowed per player")
            .defineInRange("maxCharactersPerPlayer", 5, 1, 10);
            
        ENABLE_CHARACTER_DELETION = BUILDER
            .comment("Whether players can delete their characters")
            .define("enableCharacterDeletion", true);

        NAME_VALIDATION_REGEX = BUILDER
            .comment("Regex pattern for validating character names. Default allows 3-32 characters, using letters, numbers, spaces, underscores, and hyphens.")
            .define("nameValidationRegex", "^[a-zA-Z0-9_\\- ]{3,32}$");

        BUILDER.pop();

        // Display Name System Settings
        BUILDER.push("Display Name System");

        ENABLE_NAME_SYSTEM = BUILDER
            .comment("Master toggle for the display name system. If false, character names will not be displayed anywhere.")
            .define("enableNameSystem", true);

        SHOW_USERNAME_IN_TABLIST = BUILDER
            .comment("Whether to show the player's username alongside their character name in the tab list")
            .define("showUsernameInTablist", true);

        TABLIST_NAME_COLOR = BUILDER
            .comment("Color code for highlighting character names in the tab list (e.g., 'e' for yellow, 'c' for light red.) Only used if showUsernameInTablist is true.")
            .define("tablistNameColor", "e");

        BUILDER.pop(); // End Display Name System
        
        // Character Features
        BUILDER.push("Character Features");

        ENABLE_INVENTORY_SYSTEM = BUILDER
            .comment("Master toggle for the character inventory system.",
                    "If false, characters won't have separate inventories.",
                    "WARNING: Disabling this with existing characters may cause inventory loss!")
            .define("enableInventorySystem", true);

        ENABLE_LOCATION_SYSTEM = BUILDER
            .comment("Master toggle for the character location system.",
                    "If false, characters won't remember their last location.",
                    "WARNING: Disabling this with existing characters may cause location data loss!")
            .define("enableLocationSystem", true);

        ENABLE_LANDFALL_ADDONS = BUILDER
            .comment("Master toggle for Landfall-specific addon features.",
                    "This includes Origins and other Landfall-specific content.")
            .define("enableLandfallAddons", false);

        BUILDER.pop(); // End Character Features
        
        // Aging System Settings
        BUILDER.push("Aging System");

        ENABLE_AGING_SYSTEM = BUILDER
            .comment("Master toggle for the character aging system. If false, characters won't age and the age input field won't appear.")
            .define("enableAgingSystem", true);

        TIME_PASSING_RATIO = BUILDER
            .comment("Determines how many real-life days equal one game year for characters.",
                    "For example, a value of 1.0 means 1 real-life day = 1 game year.",
                    "A value of 24.0 means 24 real-life days = 1 game year.")
            .defineInRange("timePassingRatio", 24.0, 0.1, 1000.0);

        MIN_CHARACTER_AGE = BUILDER
            .comment("Minimum allowed age (in game years) for character creation.",
                    "Characters cannot be created younger than this.")
            .defineInRange("minCharacterAge", 16.0, 0.0, 1000.0);

        MAX_CHARACTER_AGE = BUILDER
            .comment("Maximum allowed age (in game years) for character creation.",
                    "Characters cannot be created older than this.")
            .defineInRange("maxCharacterAge", 100.0, 1.0, 10000.0);

        DEFAULT_CHARACTER_AGE = BUILDER
            .comment("Default age (in game years) for new characters when no age is specified.",
                    "Must be between minCharacterAge and maxCharacterAge.")
            .defineInRange("defaultCharacterAge", 20.0, 0.0, 10000.0);

        BUILDER.pop(); // End Aging System
        
        SPEC = BUILDER.build();
    }
} 