package world.landfall.persona.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.registry.PersonaNetworking;
import world.landfall.persona.client.network.CharacterSyncManager;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

public class CharacterCreationScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 100;
    private static final int PADDING = 10;
    
    private final Screen parent;
    private EditBox nameBox;
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
        int guiLeft = (width - WIDTH) / 2;
        int guiTop = (height - HEIGHT) / 2;
        
        nameBox = new EditBox(font, guiLeft + PADDING, guiTop + PADDING, WIDTH - (PADDING * 2), 20, Component.empty());
        nameBox.setMaxLength(32);
        nameBox.setValue("");
        addRenderableWidget(nameBox);
        
        createButton = Button.builder(Component.translatable("button.persona.create"), button -> 
            createCharacter()
        ).bounds(guiLeft + PADDING, guiTop + HEIGHT - 30, (WIDTH - (PADDING * 3)) / 2, 20).build();
        addRenderableWidget(createButton);
        
        Button cancelButton = Button.builder(Component.translatable("button.persona.cancel"), button -> 
            handleCancel()
        ).bounds(guiLeft + WIDTH - ((WIDTH - (PADDING * 3)) / 2) - PADDING, guiTop + HEIGHT - 30, 
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
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            UIErrorHandler.showError("gui.persona.error.name_empty");
            return;
        }

        // Validate name format before sending to server
        if (!CharacterProfile.isValidName(name)) {
            UIErrorHandler.showError("command.persona.error.invalid_name");
            return;
        }

        // Disable button while waiting for server response
        createButton.active = false;
        PersonaNetworking.sendActionToServer(PersonaNetworking.Action.CREATE, name, true);
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
        
        int guiLeft = (width - WIDTH) / 2;
        int guiTop = (height - HEIGHT) / 2;
        
        graphics.drawCenteredString(font, title, width / 2, guiTop - 20, 0xFFFFFF);
        
        graphics.fill(guiLeft - 5, guiTop - 5, guiLeft + WIDTH + 5, guiTop + HEIGHT + 5, 0x80000000);
        
        if (font != null) {
            graphics.drawString(font, Component.translatable("screen.persona.character_name"), 
                    guiLeft + PADDING, guiTop + PADDING - 12, 0xFFFFFF);
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