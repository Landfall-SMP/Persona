package world.landfall.persona.client.gui.input;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;

/**
 * Interface for providers that can add input fields to the character creation screen.
 * Each provider can register one or more input fields, and will receive callbacks
 * when those inputs are submitted.
 */
public interface CharacterCreationInputProvider {
    /**
     * Gets the unique ID for this input provider.
     * @return A ResourceLocation uniquely identifying this provider
     */
    ResourceLocation getId();
    
    /**
     * Gets the display name shown as a label for this input field.
     * @return The Component to display as a label
     */
    Component getDisplayName();
    
    /**
     * Gets the placeholder/prompt text shown in the input field.
     * @return The Component to display as a placeholder
     */
    Component getPromptText();
    
    /**
     * Creates and configures an EditBox for this input.
     * @param font The font to use
     * @param x The x coordinate
     * @param y The y coordinate
     * @param width The width of the input field
     * @param height The height of the input field
     * @return A configured EditBox
     */
    EditBox createEditBox(Font font, int x, int y, int width, int height);
    
    /**
     * Called when character creation is submitted to validate the input.
     * @param input The current text in the input field
     * @return true if the input is valid, false otherwise
     */
    boolean validateInput(String input);
    
    /**
     * Gets an error message to display if validation fails.
     * @param input The input that failed validation
     * @return A Component containing the error message
     */
    Component getValidationErrorMessage(String input);
    
    /**
     * Processes the input value and returns data to be stored in the character's modData.
     * @param input The value from the input field
     * @param tag The CompoundTag to populate with data
     */
    void processInput(String input, CompoundTag tag);
    
    /**
     * Whether this input field is required. If true, the field cannot be left empty.
     * @return true if input is required, false if optional
     */
    default boolean isRequired() {
        return false;
    }
    
    /**
     * Priority determines the order of input fields. Lower values appear first.
     * @return the display priority
     */
    default int getPriority() {
        return 100; // Default priority
    }
    
    /**
     * Whether this input field should be visible in the character creation screen.
     * This can be used to conditionally show/hide inputs based on mod configuration.
     * @return true if the input should be visible, false otherwise
     */
    default boolean isVisible() {
        return true; // By default, inputs are visible
    }
} 