package world.landfall.persona.features.aging;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import world.landfall.persona.Persona;
import world.landfall.persona.client.gui.input.CharacterCreationInputProvider;
import world.landfall.persona.config.ClientSyncedConfig;
import world.landfall.persona.config.Config;

/**
 * Input provider for the character aging system.
 * Allows players to specify their character's starting age.
 * This provider will only be active if aging is enabled in server config.
 */
public class AgingInputProvider implements CharacterCreationInputProvider {
    private static final ResourceLocation ID = ResourceLocation.parse(Persona.MODID + ":aging_input");
    private static final Component DISPLAY_NAME = Component.translatable("screen.persona.starting_age");
    private static final Component ERROR_FORMAT = Component.translatable("gui.persona.error.invalid_age_format");
    private static final Component ERROR_TOO_YOUNG = Component.translatable("gui.persona.error.age_too_young");
    private static final Component ERROR_TOO_OLD = Component.translatable("gui.persona.error.age_too_old");
    
    // Key to store the starting age in modData during character creation
    private static final String STARTING_AGE_KEY = "StartingAge";
    
    // This provider is meant to be registered, but will only be visible/active if the server has aging enabled. CharacterCreationScreen should check w the server before showing.
    
    @Override
    public ResourceLocation getId() {
        return ID;
    }
    
    @Override
    public Component getDisplayName() {
        return DISPLAY_NAME;
    }
    
    @Override
    public Component getPromptText() {
        double minAge = Config.MIN_CHARACTER_AGE.get();
        double maxAge = Config.MAX_CHARACTER_AGE.get();
        double defaultAge = Config.DEFAULT_CHARACTER_AGE.get();
        return Component.translatable("screen.persona.starting_age_prompt", 
                                    String.format("%.1f", minAge), 
                                    String.format("%.1f", maxAge),
                                    String.format("%.1f", defaultAge));
    }
    
    @Override
    public EditBox createEditBox(Font font, int x, int y, int width, int height) {
        EditBox ageBox = new EditBox(font, x, y, width, height, getPromptText());
        ageBox.setMaxLength(7); // Allow for "10000.0"
        ageBox.setValue(String.format("%.1f", Config.DEFAULT_CHARACTER_AGE.get())); // Default value
        
        // Allow digits, decimal point, and backspace
        ageBox.setFilter(s -> {
            if (s.isEmpty()) return true;
            try {
                Double.parseDouble(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        
        return ageBox;
    }
    
    @Override
    public boolean validateInput(String input) {
        if (input.isEmpty()) return true; // Empty is fine (will use default)
        
        try {
            double age = Double.parseDouble(input);
            double minAge = Config.MIN_CHARACTER_AGE.get();
            double maxAge = Config.MAX_CHARACTER_AGE.get();
            return age >= minAge && age <= maxAge;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    @Override
    public Component getValidationErrorMessage(String input) {
        if (input.isEmpty()) return Component.empty();
        
        try {
            double age = Double.parseDouble(input);
            double minAge = Config.MIN_CHARACTER_AGE.get();
            double maxAge = Config.MAX_CHARACTER_AGE.get();
            
            if (age < minAge) {
                return ERROR_TOO_YOUNG;
            } else if (age > maxAge) {
                return ERROR_TOO_OLD;
            }
            return Component.empty();
        } catch (NumberFormatException e) {
            return ERROR_FORMAT;
        }
    }
    
    @Override
    public void processInput(String input, CompoundTag tag) {
        double age;
        if (input.isEmpty()) {
            age = Config.DEFAULT_CHARACTER_AGE.get();
            Persona.LOGGER.debug("[Persona] No starting age provided, using default: {}", age);
        } else {
            try {
                age = Double.parseDouble(input);
                // Clamp to config limits (should already be validated, but just in case)
                age = Math.max(Config.MIN_CHARACTER_AGE.get(), Math.min(Config.MAX_CHARACTER_AGE.get(), age));
                Persona.LOGGER.info("[Persona] Setting starting age to {}", age);
            } catch (NumberFormatException e) {
                age = Config.DEFAULT_CHARACTER_AGE.get();
                Persona.LOGGER.warn("[Persona] Invalid age format ({}), using default: {}", input, age);
            }
        }
        tag.putDouble(STARTING_AGE_KEY, age);
    }
    
    @Override
    public boolean isRequired() {
        return false;
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
    
    /**
     * This input field should only be visible when aging is enabled on the server.
     * The registration happens on client-init, but visibility is controlled by server config.
     */
    @Override
    public boolean isVisible() {
        // This will be checked by the CharacterCreationScreen to determine whether to show this input.
        // It relies on the server config being synced to the client.
        return ClientSyncedConfig.isAgingSystemEnabled();
    }
} 