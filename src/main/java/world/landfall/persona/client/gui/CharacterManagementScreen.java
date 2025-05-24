package world.landfall.persona.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import world.landfall.persona.client.event.CollectCharacterInfoEvent;
import world.landfall.persona.config.Config;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.registry.PersonaNetworking;
import world.landfall.persona.client.network.CharacterSyncManager;
import net.minecraft.ChatFormatting;
import world.landfall.persona.features.figura.event.ClientPersonaSwitchedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CharacterManagementScreen extends Screen {
    // UI Layout Constants
    private static final class Layout {
        static final int BUTTON_HEIGHT = 20;
        static final int LIST_ITEM_HEIGHT = 25;
        static final int MAX_VISIBLE_ITEMS = 5;
        static final int CREATE_BUTTON_SIZE = 20;
        static final int LIST_WIDTH = 300;
        static final int LIST_HEIGHT = (MAX_VISIBLE_ITEMS + 1) * LIST_ITEM_HEIGHT;
        
        // Button positioning
        static final int BUTTON_Y_OFFSET = 2;
        static final int SWITCH_BUTTON_X_WITH_DELETE = 239;
        static final int SWITCH_BUTTON_X_WITHOUT_DELETE = 264;
        static final int DELETE_BUTTON_X = 264;
        
        // Scrollbar
        static final int SCROLLBAR_WIDTH = 8;
        static final int SCROLLBAR_MARGIN = 3;
        static final int MIN_THUMB_HEIGHT = 15;
        
        // Panel
        static final int PANEL_PADDING = 5;
        static final int TITLE_Y_OFFSET = 20;
        static final int NAME_Y_OFFSET = 6;
    }
    
    // UI Colors
    private static final class Colors {
        static final int PANEL_BACKGROUND = 0x80000000;
        static final int SCROLLBAR_TRACK = 0x80333333;
        static final int SCROLLBAR_THUMB = 0xFFBBBBBB;
        static final int TEXT_COLOR = 0xFFFFFF;
        static final int ACTIVE_CHARACTER = 0x00FF00;
        static final int DECEASED_CHARACTER = 0x808080;
        static final int SWITCH_BUTTON = 0x66CCFF;
        static final int DELETE_BUTTON = 0xFF0000;
        static final int CREATE_BUTTON = 0x00FF00;
    }

    private final Player player;
    private int guiLeft;
    private int guiTop;
    private List<CharacterEntry> characterList = new ArrayList<>();
    private int scrollOffset = 0;
    private CharacterSyncManager syncManager;
    private final List<Button> switchButtons = new ArrayList<>();
    private final List<Button> deleteButtons = new ArrayList<>();
    
    public CharacterManagementScreen(Player player) {
        super(Component.translatable("screen.persona.character_management"));
        this.player = Objects.requireNonNull(player, "Player cannot be null");
        this.syncManager = new CharacterSyncManager(this::handleSyncComplete);
    }
    
    @Override
    protected void init() {
        super.init();
        // clear to make sure its current
        this.switchButtons.clear();
        this.deleteButtons.clear();
        
        guiLeft = (width - Layout.LIST_WIDTH) / 2;
        guiTop = (height - Layout.LIST_HEIGHT) / 2;
        
        // resync on open
        syncManager.startSync();
        updateCharacterList();
        
        // If there are no characters, go directly to character creation
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data != null && data.getCharacters().isEmpty() && minecraft != null) {
            minecraft.setScreen(new CharacterCreationScreen(this));
            return;
        }
        
        Button createButton = Button.builder(Component.literal("+"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(new CharacterCreationScreen(this));
            }
        }).bounds(guiLeft + Layout.LIST_WIDTH - Layout.CREATE_BUTTON_SIZE, guiTop + Layout.LIST_HEIGHT - Layout.CREATE_BUTTON_SIZE, 
                 Layout.CREATE_BUTTON_SIZE, Layout.CREATE_BUTTON_SIZE).build();
        createButton.setFGColor(Colors.CREATE_BUTTON); // Green color
        addRenderableWidget(createButton);
        
        // mk buttons for each slot
        for (int i = 0; i < Layout.MAX_VISIBLE_ITEMS; i++) {
            Button switchButton = createSwitchButton(0, 0, "");
            switchButton.setFGColor(Colors.SWITCH_BUTTON);
            switchButton.visible = false;
            switchButtons.add(switchButton);
            addRenderableWidget(switchButton);
            
            Button deleteButton = createDeleteButton(0, 0, "");
            deleteButton.setFGColor(Colors.DELETE_BUTTON);
            deleteButton.visible = false;
            deleteButtons.add(deleteButton);
            addRenderableWidget(deleteButton);
        }
    }
    
    private void updateCharacterList() {
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        
        List<CharacterEntry> newList = new ArrayList<>();
        data.getCharacters().forEach((id, profile) ->
            newList.add(new CharacterEntry(id, profile))
        );
        
        // Only update if the list has changed
        if (!newList.equals(characterList)) {
            characterList = newList;
            // reset scroll if less items now
            if (scrollOffset > Math.max(0, characterList.size() - Layout.MAX_VISIBLE_ITEMS)) {
                scrollOffset = Math.max(0, characterList.size() - Layout.MAX_VISIBLE_ITEMS);
            }
        }
    }
    
    @Override
    public void render(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        
        // dark panel, looks prettier
        graphics.fill(guiLeft - Layout.PANEL_PADDING, 
                     guiTop - Layout.PANEL_PADDING, 
                     guiLeft + Layout.LIST_WIDTH + Layout.PANEL_PADDING, 
                     guiTop + Layout.LIST_HEIGHT + Layout.PANEL_PADDING, 
                     Colors.PANEL_BACKGROUND);
        
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
        int visibleItems = Math.min(Layout.MAX_VISIBLE_ITEMS, characterList.size() - scrollOffset);
        
        for (int i = 0; i < visibleItems; i++) {
            CharacterEntry entry = characterList.get(i + scrollOffset);
            int y = listY + (i * Layout.LIST_ITEM_HEIGHT);
            boolean isActiveCharacter = activeCharacterId != null && activeCharacterId.equals(entry.id.toString());
            boolean isDeceased = entry.profile.isDeceased();
            
            // switch
            Button switchButton = switchButtons.get(i);
            switchButton.setX(Config.ENABLE_CHARACTER_DELETION.get() ? 
                            listX + Layout.SWITCH_BUTTON_X_WITH_DELETE : 
                            listX + Layout.SWITCH_BUTTON_X_WITHOUT_DELETE);
            switchButton.setY(y + Layout.BUTTON_Y_OFFSET);
            switchButton.setMessage(Component.literal("🔀"));
            switchButton.visible = !isActiveCharacter && !isDeceased;
            switchButton.active = !isActiveCharacter && !isDeceased;
            
            // delete
            if (Config.ENABLE_CHARACTER_DELETION.get()) {
                Button deleteButton = deleteButtons.get(i);
                deleteButton.setX(listX + Layout.DELETE_BUTTON_X);
                deleteButton.setY(y + Layout.BUTTON_Y_OFFSET);
                deleteButton.setMessage(Component.literal("X"));
                deleteButton.visible = !isActiveCharacter;
                deleteButton.active = !isActiveCharacter;
            }
        }
        
        // Render buttons
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // scrollbar if needed
        int scrollableAreaHeight = Layout.MAX_VISIBLE_ITEMS * Layout.LIST_ITEM_HEIGHT;
        if (characterList.size() > Layout.MAX_VISIBLE_ITEMS) {
            int scrollBarX = guiLeft + Layout.LIST_WIDTH - Layout.SCROLLBAR_WIDTH - Layout.SCROLLBAR_MARGIN;
            
            // scrollbar track
            graphics.fill(scrollBarX, guiTop, 
                         scrollBarX + Layout.SCROLLBAR_WIDTH, 
                         guiTop + scrollableAreaHeight, 
                         Colors.SCROLLBAR_TRACK);

            // calculate pos's
            float thumbHeightRatio = (float)Layout.MAX_VISIBLE_ITEMS / characterList.size();
            int scrollBarThumbHeight = (int)(scrollableAreaHeight * thumbHeightRatio);
            scrollBarThumbHeight = Math.max(Layout.MIN_THUMB_HEIGHT, scrollBarThumbHeight);

            int maxScroll = characterList.size() - Layout.MAX_VISIBLE_ITEMS;
            float scrollPercentage = (maxScroll == 0) ? 0 : (float)scrollOffset / maxScroll;
            int scrollBarThumbY = guiTop + (int)(scrollPercentage * (scrollableAreaHeight - scrollBarThumbHeight));

            // draw the scrollbar thumb
            graphics.fill(scrollBarX + 1, scrollBarThumbY + 1, 
                         scrollBarX + Layout.SCROLLBAR_WIDTH - 1, 
                         scrollBarThumbY + scrollBarThumbHeight - 1, 
                         Colors.SCROLLBAR_THUMB);
        }
        
        // text
        graphics.drawCenteredString(font, title, width / 2, guiTop - Layout.TITLE_Y_OFFSET, Colors.TEXT_COLOR);
        
        for (int i = 0; i < visibleItems; i++) {
            CharacterEntry entry = characterList.get(i + scrollOffset);
            int y = listY + (i * Layout.LIST_ITEM_HEIGHT);
            
            // Draw character name, green if selected
            int nameColor = (activeCharacterId != null && activeCharacterId.equals(entry.id.toString())) ? 
                          Colors.ACTIVE_CHARACTER : 
                          (entry.profile.isDeceased() ? Colors.DECEASED_CHARACTER : Colors.TEXT_COLOR);
            if (font != null) {
                MutableComponent characterDisplayName;
                if (entry.profile.isDeceased()) {
                    MutableComponent skullIcon = Component.literal("☠ ").withStyle(ChatFormatting.RED);
                    characterDisplayName = skullIcon.append(Component.literal(entry.profile.getDisplayName()));
                } else {
                    characterDisplayName = Component.literal(entry.profile.getDisplayName());
                }
                int currentX = listX;
                graphics.drawString(font, characterDisplayName, currentX, y + Layout.NAME_Y_OFFSET, nameColor);
                currentX += font.width(characterDisplayName) + 5; // Add some spacing after name

                // Fire event and draw additional info
                CollectCharacterInfoEvent event = new CollectCharacterInfoEvent(entry.profile);
                NeoForge.EVENT_BUS.post(event);

                for (Component infoComponent : event.getInfoComponents()) {
                    graphics.drawString(font, infoComponent, currentX, y + Layout.NAME_Y_OFFSET, nameColor); // Use same nameColor for now, can be customized
                    currentX += font.width(infoComponent) + 3; // Spacing between info components
                }
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
                if (entry.profile.isDeceased()) {
                    return;
                }
                PersonaNetworking.sendActionToServer(PersonaNetworking.Action.SWITCH, entry.id.toString(), true);
                if (minecraft != null) {
                    minecraft.getToasts().addToast(NotificationToast.success(
                        Component.translatable("command.persona.success.switch", entry.profile.getDisplayName())
                    ));
                    // Fire the ClientPersonaSwitchedEvent after successful switch
                    NeoForge.EVENT_BUS.post(new ClientPersonaSwitchedEvent(entry.profile.getDisplayName()));
                }
            }
        }).bounds(x, y, Layout.LIST_ITEM_HEIGHT, Layout.BUTTON_HEIGHT).build();
    }
    
    @SuppressWarnings("null")
    private Button createDeleteButton(int x, int y, String characterId) {
        return Button.builder(Component.literal("X"), button -> {
            if (minecraft != null) {
                int index = deleteButtons.indexOf(button);
                if (index >= 0 && index + scrollOffset < characterList.size()) {
                    CharacterEntry entry = characterList.get(index + scrollOffset);
                    minecraft.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                PersonaNetworking.sendActionToServer(PersonaNetworking.Action.DELETE, entry.id.toString(), true);
                                requestSync();
                            }
                            minecraft.setScreen(this);
                        },
                        Component.translatable("gui.persona.confirm_delete.title"),
                        Component.translatable("gui.persona.confirm_delete.message", entry.profile.getDisplayName())
                    ));
                }
            }
        }).bounds(x, y, Layout.LIST_ITEM_HEIGHT, Layout.BUTTON_HEIGHT).build();
    }
    
    private void requestSync() {
        syncManager.startSync();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Delegate click handling to widgets
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int maxScroll = Math.max(0, characterList.size() - Layout.MAX_VISIBLE_ITEMS);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Update character list periodically
        updateCharacterList();
        
        // Handle sync
        syncManager.tick();
    }
    
    private void handleSyncComplete(boolean success) {
        if (!success) {
            UIErrorHandler.showError("gui.persona.error.sync_failed");
        }
        updateCharacterList();
    }
    
    private record CharacterEntry(UUID id, CharacterProfile profile) {}
} 