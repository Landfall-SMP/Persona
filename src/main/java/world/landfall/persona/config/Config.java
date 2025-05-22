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
        
        SPEC = BUILDER.build();
    }
} 