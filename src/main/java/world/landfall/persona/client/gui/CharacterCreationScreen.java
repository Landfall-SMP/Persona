package world.landfall.persona.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import world.landfall.persona.registry.PersonaNetworking;

public class CharacterCreationScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 100;
    private static final int PADDING = 10;
    
    private final Screen parent;
    private EditBox nameBox;
    
    public CharacterCreationScreen(Screen parent) {
        super(Component.translatable("screen.persona.create_character"));
        this.parent = parent;
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
        
        Button createButton = Button.builder(Component.translatable("button.persona.create"), button -> 
            createCharacter()
        ).bounds(guiLeft + PADDING, guiTop + HEIGHT - 30, (WIDTH - (PADDING * 3)) / 2, 20).build();
        addRenderableWidget(createButton);
        
        Button cancelButton = Button.builder(Component.translatable("button.persona.cancel"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }).bounds(guiLeft + WIDTH - ((WIDTH - (PADDING * 3)) / 2) - PADDING, guiTop + HEIGHT - 30, 
                (WIDTH - (PADDING * 3)) / 2, 20).build();
        addRenderableWidget(cancelButton);
    }
    
    @SuppressWarnings("null")
    private void createCharacter() {
        String name = nameBox.getValue().trim();
        if (!name.isEmpty() && minecraft != null) {
            PersonaNetworking.sendActionToServer(PersonaNetworking.Action.CREATE, name);
            PersonaNetworking.requestCharacterSync();
            minecraft.setScreen(parent);
        }
    }
    
    @Override
    public void renderBackground(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void render(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        
        int guiLeft = (width - WIDTH) / 2;
        int guiTop = (height - HEIGHT) / 2;
        
        graphics.drawCenteredString(font, title, width / 2, guiTop - 20, 0xFFFFFF);
        
        graphics.fill(guiLeft - 5, guiTop - 5, guiLeft + WIDTH + 5, guiTop + HEIGHT + 5, 0x80000000);
        
        graphics.drawString(font, Component.translatable("screen.persona.character_name"), 
                guiLeft + PADDING, guiTop + PADDING - 12, 0xFFFFFF);
        
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