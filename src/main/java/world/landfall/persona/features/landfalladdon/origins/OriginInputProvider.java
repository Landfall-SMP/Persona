package world.landfall.persona.features.landfalladdon.origins;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import world.landfall.persona.Persona;
import world.landfall.persona.client.gui.input.CharacterCreationInputProvider;

/**
 * Input provider for the Origin selection system.
 * Allows players to choose their character's Origin during creation.
 * Creates a clickable field that cycles through the three available Origins.
 */
public class OriginInputProvider implements CharacterCreationInputProvider {
    private static final ResourceLocation ID = ResourceLocation.parse(Persona.MODID + ":origin_input");
    private static final Component DISPLAY_NAME = Component.translatable("screen.persona.origin");
    private static final Component PROMPT_TEXT = Component.translatable("screen.persona.origin_prompt");
    private static final Component ERROR_NO_SELECTION = Component.translatable("gui.persona.error.no_origin_selected");
    
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
        return PROMPT_TEXT;
    }
    
    @Override
    public EditBox createEditBox(Font font, int x, int y, int width, int height) {
        // Create a simple EditBox that we'll customize with a click handler
        OriginSelectionBox originBox = new OriginSelectionBox(font, x, y, width, height, getPromptText());
        originBox.setMaxLength(50);
        originBox.setEditable(false); // Make it read-only, only clickable
        return originBox;
    }
    
    @Override
    public boolean validateInput(String input) {
        // For OriginSelectionBox, we need to check if an origin is selected
        // The input parameter isn't reliable since it contains display text
        // This will be handled by the CharacterCreationScreen checking if the box has a selection
        return !input.isEmpty() && !input.equals("Click to select Origin");
    }
    
    @Override
    public Component getValidationErrorMessage(String input) {
        return ERROR_NO_SELECTION;
    }
    
    @Override
    public void processInput(String input, CompoundTag tag) {
        // We can't rely on the input string since it contains display text
        // The CharacterCreationScreen will need to handle this differently
        // For now, we'll try to extract the origin from the input
        if (!input.isEmpty() && !input.equals("Click to select Origin")) {
            // Try to find the origin by display name
            for (Origin origin : Origin.values()) {
                if (input.startsWith(origin.getDisplayName())) {
                    tag.putString("selectedOrigin", origin.name());
                    Persona.LOGGER.debug("[Persona] Setting character origin to: {}", origin.getDisplayName());
                    return;
                }
            }
            Persona.LOGGER.warn("[Persona] Could not determine origin from input: {}", input);
        }
    }
    
    @Override
    public boolean isRequired() {
        return true; // Origin selection is mandatory
    }
    
    @Override
    public int getPriority() {
        return 5; // Show before aging but after name
    }
    
    @Override
    public boolean isVisible() {
        return true; // Always visible for landfall addon
    }
    
    /**
     * Custom EditBox that cycles through Origins when clicked
     */
    public static class OriginSelectionBox extends EditBox {
        private Origin currentOrigin = null;
        
        public OriginSelectionBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            setValue("Click to select Origin");
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Check if the click is within our bounds
            if (button == 0 && mouseX >= this.getX() && mouseX < this.getX() + this.width && 
                mouseY >= this.getY() && mouseY < this.getY() + this.height) {
                cycleOrigin();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        private void cycleOrigin() {
            Origin[] origins = Origin.values();
            if (currentOrigin == null) {
                currentOrigin = origins[0];
            } else {
                int currentIndex = currentOrigin.ordinal();
                currentOrigin = origins[(currentIndex + 1) % origins.length];
            }
            
            // Update the displayed text to show the display name
            setValue(currentOrigin.getDisplayName() + " (Click to change)");
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(currentOrigin.getDescription())));
        }
        
        public Origin getCurrentOrigin() {
            return currentOrigin;
        }
    }
} 