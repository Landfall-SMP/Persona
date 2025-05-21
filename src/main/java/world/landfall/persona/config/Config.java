package world.landfall.persona.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_CHARACTERS_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue ENABLE_CHARACTER_DELETION;

    static {
        BUILDER.push("General Settings");
        
        MAX_CHARACTERS_PER_PLAYER = BUILDER
            .comment("Maximum number of characters allowed per player")
            .defineInRange("maxCharactersPerPlayer", 5, 1, 10);
            
        ENABLE_CHARACTER_DELETION = BUILDER
            .comment("Whether players can delete their characters")
            .define("enableCharacterDeletion", true);
            
        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
} 