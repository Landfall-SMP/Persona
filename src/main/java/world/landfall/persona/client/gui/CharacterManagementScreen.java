package world.landfall.persona.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import world.landfall.persona.config.Config;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.registry.PersonaNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CharacterManagementScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_ITEM_HEIGHT = 25;
    private static final int MAX_VISIBLE_ITEMS = 5;
    private static final int CREATE_BUTTON_SIZE = 20;
    private static final int LIST_WIDTH = 300;
    private static final int LIST_HEIGHT = (MAX_VISIBLE_ITEMS + 1) * LIST_ITEM_HEIGHT;
    
    private final Player player;
    private int guiLeft;
    private int guiTop;
    private List<CharacterEntry> characterList = new ArrayList<>();
    private int scrollOffset = 0;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 100;
    
    // store buttons as fields
    private final List<Button> switchButtons = new ArrayList<>();
    private final List<Button> deleteButtons = new ArrayList<>();
    
    public CharacterManagementScreen(Player player) {
        super(Component.translatable("screen.persona.character_management"));
        this.player = Objects.requireNonNull(player, "Player cannot be null");
    }
    
    @Override
    protected void init() {
        super.init();
        // clear to make sure its current
        this.switchButtons.clear();
        this.deleteButtons.clear();
        
        guiLeft = (width - LIST_WIDTH) / 2;
        guiTop = (height - LIST_HEIGHT) / 2;
        
        // resync on open
        PersonaNetworking.requestCharacterSync();
        updateCharacterList();
        
        Button createButton = Button.builder(Component.literal("+"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(new CharacterCreationScreen(this));
            }
        }).bounds(guiLeft + LIST_WIDTH - CREATE_BUTTON_SIZE, guiTop + LIST_HEIGHT - CREATE_BUTTON_SIZE, 
                 CREATE_BUTTON_SIZE, CREATE_BUTTON_SIZE).build();
        createButton.setFGColor(0x00FF00); // Green color
        addRenderableWidget(createButton);
        
        // mk buttons for each slot
        for (int i = 0; i < MAX_VISIBLE_ITEMS; i++) {
            Button switchButton = createSwitchButton(0, 0, "");
            switchButton.setFGColor(0x66CCFF);
            switchButton.visible = false;
            switchButtons.add(switchButton);
            addRenderableWidget(switchButton);
            
            Button deleteButton = createDeleteButton(0, 0, "");
            deleteButton.setFGColor(0xFF0000);
            deleteButton.visible = false;
            deleteButtons.add(deleteButton);
            addRenderableWidget(deleteButton);
        }
    }
    
    private void updateCharacterList() {
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        
        List<CharacterEntry> newList = new ArrayList<>();
        data.getCharacters().forEach((id, character) -> 
            newList.add(new CharacterEntry(id.toString(), character.getDisplayName()))
        );
        
        // Only update if the list has changed
        if (!newList.equals(characterList)) {
            characterList = newList;
            // reset scroll if less items now
            if (scrollOffset > Math.max(0, characterList.size() - MAX_VISIBLE_ITEMS)) {
                scrollOffset = Math.max(0, characterList.size() - MAX_VISIBLE_ITEMS);
            }
        }
    }
    
    @Override
    public void render(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        
        // dark panel, looks prettier
        graphics.fill(guiLeft - 5, guiTop - 5, guiLeft + LIST_WIDTH + 5, guiTop + LIST_HEIGHT + 5, 0x80000000);
        
        // Get active char
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        String activeCharacterId = null;
        if (data != null && data.getActiveCharacterId() != null) {
            activeCharacterId = data.getActiveCharacterId().toString();
        }
        
        // Hide all buttons first
        switchButtons.forEach(b -> b.visible = false);
        deleteButtons.forEach(b -> b.visible = false);
        
        // update button positions and visibility
        int listX = guiLeft;
        int listY = guiTop;
        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, characterList.size() - scrollOffset);
        
        for (int i = 0; i < visibleItems; i++) {
            CharacterEntry entry = characterList.get(i + scrollOffset);
            int y = listY + (i * LIST_ITEM_HEIGHT);
            boolean isActiveCharacter = activeCharacterId != null && activeCharacterId.equals(entry.id);
            
            // switch
            Button switchButton = switchButtons.get(i);
            switchButton.setX(listX + 239);
            switchButton.setY(y + 2);
            switchButton.setMessage(Component.literal("🔀"));
            switchButton.visible = !isActiveCharacter;
            switchButton.active = !isActiveCharacter;
            
            // delete
            if (Config.ENABLE_CHARACTER_DELETION.get()) {
                Button deleteButton = deleteButtons.get(i);
                deleteButton.setX(listX + 264);
                deleteButton.setY(y + 2);
                deleteButton.setMessage(Component.literal("X"));
                deleteButton.visible = !isActiveCharacter;
                deleteButton.active = !isActiveCharacter;
            }
        }
        
        // Render buttons
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // scrollbar if needed
        int scrollableAreaHeight = MAX_VISIBLE_ITEMS * LIST_ITEM_HEIGHT;
        if (characterList.size() > MAX_VISIBLE_ITEMS) {
            int scrollBarWidth = 8;
            int scrollBarX = guiLeft + LIST_WIDTH - scrollBarWidth - 3; // Inside panel, right side
            
            // scrollbar track
            graphics.fill(scrollBarX, guiTop, scrollBarX + scrollBarWidth, guiTop + scrollableAreaHeight, 0x80333333); 

            // calculate pos's
            float thumbHeightRatio = (float)MAX_VISIBLE_ITEMS / characterList.size();
            int scrollBarThumbHeight = (int)(scrollableAreaHeight * thumbHeightRatio);
            scrollBarThumbHeight = Math.max(15, scrollBarThumbHeight); // Minimum thumb height

            int maxScroll = characterList.size() - MAX_VISIBLE_ITEMS;
            float scrollPercentage = (maxScroll == 0) ? 0 : (float)scrollOffset / maxScroll;
            int scrollBarThumbY = guiTop + (int)(scrollPercentage * (scrollableAreaHeight - scrollBarThumbHeight));

            // aaand draw the lovely scrollbar
            graphics.fill(scrollBarX + 1, scrollBarThumbY + 1, 
                          scrollBarX + scrollBarWidth - 1, scrollBarThumbY + scrollBarThumbHeight - 1, 
                          0xFFBBBBBB);
        }
        
        // text
        graphics.drawCenteredString(font, title, width / 2, guiTop - 20, 0xFFFFFF);
        
        for (int i = 0; i < visibleItems; i++) {
            CharacterEntry entry = characterList.get(i + scrollOffset);
            int y = listY + (i * LIST_ITEM_HEIGHT);
            
            // Draw character name, green if selected
            int nameColor = (activeCharacterId != null && activeCharacterId.equals(entry.id)) ? 0x00FF00 : 0xFFFFFF;
            if (font != null) {
                graphics.drawString(font, entry.name, listX, y + 6, nameColor);
            }
        }
    }
    
    @Override
    public void renderBackground(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // fought too hard with the blur for way too long, just sliding this here for now
    }
    
    private Button createSwitchButton(int x, int y, String characterName) {
        return Button.builder(Component.literal("🔀"), button -> {
            int index = switchButtons.indexOf(button);
            if (index >= 0 && index + scrollOffset < characterList.size()) {
                CharacterEntry entry = characterList.get(index + scrollOffset);
                PersonaNetworking.sendActionToServer(PersonaNetworking.Action.SWITCH, entry.name);
                requestSync();
            }
        }).bounds(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build();
    }
    
    @SuppressWarnings("null")
    private Button createDeleteButton(int x, int y, String characterName) {
        return Button.builder(Component.literal("X"), button -> {
            int index = deleteButtons.indexOf(button);
            if (index >= 0 && index + scrollOffset < characterList.size()) {
                CharacterEntry entryToDelete = characterList.get(index + scrollOffset);
                
                // Open ConfirmScreen
                if (minecraft != null) {
                    minecraft.setScreen(new ConfirmScreen(
                        (confirmed) -> {
                            if (confirmed) {
                                PersonaNetworking.sendActionToServer(PersonaNetworking.Action.DELETE, entryToDelete.name);
                                requestSync();
                            }
                            minecraft.setScreen(CharacterManagementScreen.this); 
                        },
                        Component.literal("Confirm Deletion"), // TODO: translatable
                        Component.literal("Are you sure you want to delete character '" + entryToDelete.name + "'?") // TODO: translatable
                    ));
                }
            }
        }).bounds(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build();
    }
    
    private void requestSync() {
        PersonaNetworking.requestCharacterSync();
        lastUpdateTime = System.currentTimeMillis();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Delegate click handling to widgets
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int maxScroll = Math.max(0, characterList.size() - MAX_VISIBLE_ITEMS);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    @Override
    public void tick() {
        super.tick();
        // keep list updated
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            updateCharacterList();
            lastUpdateTime = currentTime;
        }
    }
    
    private record CharacterEntry(String id, String name) {}
} 