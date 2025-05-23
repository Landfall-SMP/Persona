package world.landfall.persona.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.registry.PersonaNetworking;
import world.landfall.persona.client.network.CharacterSyncManager;
import world.landfall.persona.client.gui.input.CharacterCreationInputProvider;
import world.landfall.persona.client.gui.input.CharacterCreationInputRegistry;

import java.util.*;
import javax.annotation.Nonnull;

public class CharacterCreationScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int BASE_HEIGHT = 100;
    private static final int INPUT_FIELD_HEIGHT = 20;
    private static final int INPUT_FIELD_SPACING = 10; // Space below label
    private static final int LABEL_HEIGHT = 10; // Approx height for label
    private static final int SECTION_SPACING = 15; // Space between input sections
    private static final int PADDING = 10;
    
    private final Screen parent;
    private EditBox nameBox; // Character name is always required
    private final Map<ResourceLocation, EditBox> inputFields = new HashMap<>(); // Dynamic fields from providers
    private Button createButton;
    private final CharacterSyncManager syncManager;
    
    public CharacterCreationScreen(Screen parent) {
        super(Component.translatable("screen.persona.create_character"));
        this.parent = Objects.requireNonNull(parent, "Parent screen cannot be null");
        this.syncManager = new CharacterSyncManager(this::handleSyncComplete);
    }
    
    @Override
    protected void init() {
        super.init();
        inputFields.clear();
        
        // Get all registered input providers
        List<CharacterCreationInputProvider> providers = CharacterCreationInputRegistry.getAll();
        List<CharacterCreationInputProvider> visibleProviders = providers.stream().filter(CharacterCreationInputProvider::isVisible).toList();
        
        // Calculate dynamic height based on number of VISIBLE input fields
        int totalHeight = BASE_HEIGHT + (visibleProviders.size() * (INPUT_FIELD_HEIGHT + INPUT_FIELD_SPACING + LABEL_HEIGHT + SECTION_SPACING));
        int guiLeft = (width - WIDTH) / 2;
        int guiTop = (height - totalHeight) / 2;
        
        // Always add the name field first
        int currentY = guiTop + PADDING + LABEL_HEIGHT; // Leave space for the label (drawn in render)
        
        // Name Box
        nameBox = new EditBox(font, guiLeft + PADDING, currentY, WIDTH - (PADDING * 2), INPUT_FIELD_HEIGHT, 
                Component.translatable("screen.persona.character_name_prompt"));
        nameBox.setMaxLength(32);
        nameBox.setValue("");
        addRenderableWidget(nameBox);
        currentY += INPUT_FIELD_HEIGHT + SECTION_SPACING + LABEL_HEIGHT; // Add space for next label
        
        // Add all VISIBLE dynamic input fields
        for (CharacterCreationInputProvider provider : visibleProviders) { // Iterate over visibleProviders
            // Add input field (labels are drawn in render)
            EditBox inputField = provider.createEditBox(font, guiLeft + PADDING, currentY, WIDTH - (PADDING * 2), INPUT_FIELD_HEIGHT);
            addRenderableWidget(inputField);
            inputFields.put(provider.getId(), inputField);
            
            currentY += INPUT_FIELD_HEIGHT + SECTION_SPACING + LABEL_HEIGHT;
        }
        
        // Add buttons at the bottom
        int buttonY = guiTop + totalHeight - 30 - PADDING;
        createButton = Button.builder(Component.translatable("button.persona.create"), button -> 
            createCharacter()
        ).bounds(guiLeft + PADDING, buttonY, (WIDTH - (PADDING * 3)) / 2, 20).build();
        addRenderableWidget(createButton);
        
        Button cancelButton = Button.builder(Component.translatable("button.persona.cancel"), button -> 
            handleCancel()
        ).bounds(guiLeft + WIDTH - ((WIDTH - (PADDING * 3)) / 2) - PADDING, buttonY, 
                (WIDTH - (PADDING * 3)) / 2, 20).build();
        addRenderableWidget(cancelButton);
    }
    
    @SuppressWarnings("null")
    private void handleCancel() {
        if (minecraft == null) return;
        
        if (parent instanceof CharacterManagementScreen) {
            Optional<PlayerCharacterData> data = Optional.ofNullable(minecraft.player)
                .map(player -> player.getData(PlayerCharacterCapability.CHARACTER_DATA));
            
            if (data.map(d -> d.getCharacters().isEmpty()).orElse(false)) {
                minecraft.setScreen(null);
                return;
            }
        }
        minecraft.setScreen(parent);
    }
    
    @Override
    public void tick() {
        super.tick();
        if (syncManager.isSyncing()) {
            createButton.active = false;  // Disable button while syncing
        }
        syncManager.tick();
    }
    
    private void createCharacter() {
        // Validate the character name
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            UIErrorHandler.showError("gui.persona.error.name_empty");
            return;
        }

        if (!CharacterProfile.isValidName(name)) {
            UIErrorHandler.showError("command.persona.error.invalid_name");
            return;
        }

        // Create a container for all modData from input providers
        Map<ResourceLocation, CompoundTag> modDataMap = new HashMap<>();
        
        // Validate all input fields from providers
        for (CharacterCreationInputProvider provider : CharacterCreationInputRegistry.getAll()) {
            ResourceLocation providerId = provider.getId();
            EditBox inputField = inputFields.get(providerId);
            
            if (inputField == null) continue;
            
            String input = inputField.getValue().trim();
            
            // Check if required field is empty
            if (provider.isRequired() && input.isEmpty()) {
                UIErrorHandler.showError("gui.persona.error.required_field_empty", provider.getDisplayName());
                return;
            }
            
            // Skip validation for empty optional fields
            if (input.isEmpty()) continue;
            
            // Validate the input
            if (!provider.validateInput(input)) {
                UIErrorHandler.showError(provider.getValidationErrorMessage(input).getString());
                return;
            }
            
            // Process the input into modData
            CompoundTag tag = new CompoundTag();
            provider.processInput(input, tag);
            modDataMap.put(providerId, tag);
        }
        
        // All validation passed, disable button and send to server
        createButton.active = false;
        
        // Send action to server with modData
        PersonaNetworking.sendCreateWithModData(name, modDataMap, true);
    }
    
    // Called by PersonaNetworking when server confirms successful creation
    public void handleSuccessfulCreation() {
        syncManager.startSync();
    }

    // Called by PersonaNetworking when server reports creation failure
    public void handleFailedCreation(String errorKey, Object... args) {
        createButton.active = true;
        UIErrorHandler.showError(errorKey, args);
    }
    
    @SuppressWarnings("null")
    private void handleSyncComplete(boolean success) {
        if (minecraft != null) {
            if (!success) {
                createButton.active = true;
                UIErrorHandler.showError("gui.persona.error.sync_failed");
            } else {
                minecraft.setScreen(parent);
            }
        }
    }
    
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Objects.requireNonNull(graphics, "GuiGraphics cannot be null");
        
        renderBackground(graphics, mouseX, mouseY, partialTick);
        
        // Get all providers to calculate dynamic height
        List<CharacterCreationInputProvider> providers = CharacterCreationInputRegistry.getAll();
        List<CharacterCreationInputProvider> visibleProviders = providers.stream().filter(CharacterCreationInputProvider::isVisible).toList();
        
        // Calculate dynamic height based on number of VISIBLE input fields
        int totalHeight = BASE_HEIGHT + (visibleProviders.size() * (INPUT_FIELD_HEIGHT + INPUT_FIELD_SPACING + LABEL_HEIGHT + SECTION_SPACING));
        
        int guiLeft = (width - WIDTH) / 2;
        int guiTop = (height - totalHeight) / 2;
        
        graphics.drawCenteredString(font, title, width / 2, guiTop - 20, 0xFFFFFF);
        
        // Background panel sized for dynamic content
        graphics.fill(guiLeft - 5, guiTop - 5, guiLeft + WIDTH + 5, guiTop + totalHeight + 5, 0x80000000);
        
        if (font != null) {
            // Label for Name Box
            graphics.drawString(font, Component.translatable("screen.persona.character_name"), 
                    guiLeft + PADDING, guiTop + PADDING, 0xFFFFFF);
            
            // Draw labels for all VISIBLE dynamic input fields
            int currentY = guiTop + PADDING + LABEL_HEIGHT + INPUT_FIELD_HEIGHT + SECTION_SPACING;
            for (CharacterCreationInputProvider provider : visibleProviders) { // Iterate over visibleProviders
                graphics.drawString(font, provider.getDisplayName(), 
                        guiLeft + PADDING, currentY, 0xFFFFFF);
                currentY += LABEL_HEIGHT + INPUT_FIELD_HEIGHT + SECTION_SPACING;
            }
        }
        
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // Enter key
            createCharacter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
} 