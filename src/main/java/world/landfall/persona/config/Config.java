package world.landfall.persona.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_CHARACTERS_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue ENABLE_CHARACTER_DELETION;
    public static final ModConfigSpec.ConfigValue<String> NAME_VALIDATION_REGEX;

    static {
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
        
        SPEC = BUILDER.build();
    }
} 