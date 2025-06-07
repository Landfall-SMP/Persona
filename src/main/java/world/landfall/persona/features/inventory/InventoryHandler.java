package world.landfall.persona.features.inventory;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import world.landfall.persona.Persona;
import world.landfall.persona.registry.PersonaEvents;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.config.Config;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@EventBusSubscriber(modid = Persona.MODID)
public class InventoryHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation INVENTORY_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "inventory");
    
    // Per-player locks to prevent concurrent inventory operations
    private static final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    static {
        LOGGER.debug("InventoryHandler loaded for Persona.");
    }

    /**
     * Gets or creates a lock for the specified player to ensure thread-safe inventory operations.
     */
    private static ReentrantLock getPlayerLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    /**
     * Removes the lock for a player when they disconnect to prevent memory leaks.
     */
    public static void cleanupPlayerLock(UUID playerId) {
        playerLocks.remove(playerId);
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            cleanupPlayerLock(serverPlayer.getUUID());
            LOGGER.debug("[InventoryHandler] Cleaned up player lock for {}", serverPlayer.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onCreate(PersonaEvents.CharacterCreateEvent event) {
        if (!Config.ENABLE_INVENTORY_SYSTEM.get()) {
            return;
        }
        try {
            LOGGER.debug("[InventoryHandler] Create event for player: {}, character: {}",
                event.getPlayer().getName().getString(), event.getCharacterId());

            CharacterProfile profile = event.getProfile();
            if (profile != null) {
                profile.setModData(INVENTORY_KEY, new CompoundTag());
                LOGGER.debug("[InventoryHandler] Initialized empty inventory data for character: {}", event.getCharacterId());
            } else {
                LOGGER.warn("[InventoryHandler] CharacterProfile is null in CreateEvent for character: {}. Cannot initialize inventory data.", event.getCharacterId());
            }
        } catch (Exception e) {
            LOGGER.error("[InventoryHandler] Error in CharacterCreateEvent handler", e);
        }
    }

    @SubscribeEvent
    public static void onPreSwitch(PersonaEvents.CharacterPreSwitchEvent event) {
        if (!Config.ENABLE_INVENTORY_SYSTEM.get()) {
            event.getReady().complete(null);
            return;
        }
        ServerPlayer player;
        UUID playerId;

        try {
            if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
                LOGGER.warn("[InventoryHandler] Player is not a ServerPlayer, skipping inventory save.");
                event.getReady().complete(null);
                return;
            }
            player = serverPlayer;
            playerId = player.getUUID();
            UUID fromCharacterId = event.getFromCharacterId();

            LOGGER.debug("[InventoryHandler] PreSwitch event for player: {} (ID: {}), from character: {}",
                player.getName().getString(), playerId, fromCharacterId);

            // Use player-specific lock to prevent concurrent inventory operations
            ReentrantLock playerLock = getPlayerLock(playerId);
            playerLock.lock();
            try {
                if (fromCharacterId != null) {
                    PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
                    if (characterData == null) {
                        LOGGER.error("[InventoryHandler] PlayerCharacterData is null for player {}. Cannot save inventory.", playerId);
                        event.getReady().complete(null);
                        return;
                    }
                    CharacterProfile fromProfile = characterData.getCharacter(fromCharacterId);
                    if (fromProfile != null) {
                        CompoundTag inventoryTag = saveInventory(player);
                        fromProfile.setModData(INVENTORY_KEY, inventoryTag);
                        LOGGER.debug("[InventoryHandler] Saved inventory for character {} ({} items). Player: {}",
                            fromCharacterId, inventoryTag.getList("Items", 10).size(), playerId);
                    } else {
                        LOGGER.warn("[InventoryHandler] 'From' CharacterProfile is null for character: {}. Cannot save inventory.", fromCharacterId);
                    }
                } else {
                    LOGGER.debug("[InventoryHandler] No 'from' character ID, nothing to save for inventory.");
                }
            } finally {
                playerLock.unlock();
            }
        } catch (Exception e) {
            LOGGER.error("[InventoryHandler] Error in PreSwitch event handler for inventory", e);
        } finally {
            event.getReady().complete(null);
        }
    }

    @SubscribeEvent
    public static void onSwitch(PersonaEvents.CharacterSwitchEvent event) {
        if (!Config.ENABLE_INVENTORY_SYSTEM.get()) {
            return;
        }
        ServerPlayer player;
        UUID playerId;

        try {
            if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
                LOGGER.warn("[InventoryHandler] Player is not a ServerPlayer, skipping inventory load.");
                return;
            }
            player = serverPlayer;
            playerId = player.getUUID();
            UUID toCharacterId = event.getToCharacterId();

            LOGGER.debug("[InventoryHandler] Switch event for player: {} (ID: {}), to character: {}",
                player.getName().getString(), playerId, toCharacterId);

            // Use player-specific lock to prevent concurrent inventory operations
            ReentrantLock playerLock = getPlayerLock(playerId);
            playerLock.lock();
            try {
                if (toCharacterId != null) {
                    PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
                    if (characterData == null) {
                        LOGGER.error("[InventoryHandler] PlayerCharacterData is null for player {}. Cannot load inventory. Clearing inventory.", playerId);
                        clearInventorySafely(player);
                        return;
                    }
                    CharacterProfile toProfile = characterData.getCharacter(toCharacterId);
                    if (toProfile != null) {
                        CompoundTag inventoryTag = toProfile.getModData(INVENTORY_KEY);
                        if (inventoryTag != null && !inventoryTag.isEmpty()) {
                            loadInventory(player, inventoryTag);
                            LOGGER.debug("[InventoryHandler] Loaded inventory for character {} ({} items). Player: {}",
                                toCharacterId, inventoryTag.getList("Items", 10).size(), playerId);
                        } else {
                            LOGGER.debug("[InventoryHandler] No inventory data found for character {}, clearing inventory. Player: {}", toCharacterId, playerId);
                            clearInventorySafely(player);
                        }
                    } else {
                         LOGGER.warn("[InventoryHandler] 'To' CharacterProfile is null for character: {}. Cannot load inventory. Clearing inventory.", toCharacterId);
                         clearInventorySafely(player);
                    }
                } else {
                    LOGGER.warn("[InventoryHandler] 'To' character ID is null. Clearing inventory as a precaution.");
                    clearInventorySafely(player);
                }
            } finally {
                playerLock.unlock();
            }
        } catch (Exception e) {
            LOGGER.error("[InventoryHandler] Error in Switch event handler for inventory", e);
            if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
                 clearInventorySafely(serverPlayer);
                 LOGGER.error("[InventoryHandler] Cleared inventory for player {} due to error.", serverPlayer.getName().getString());
            }
        }
    }

    @SubscribeEvent
    public static void onDelete(PersonaEvents.CharacterDeleteEvent event) {
        if (!Config.ENABLE_INVENTORY_SYSTEM.get()) {
            return;
        }
        try {
            if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
                LOGGER.warn("[InventoryHandler] Player is not a ServerPlayer, skipping inventory transfer check.");
                return;
            }

            UUID characterId = event.getCharacterId();
            UUID playerId = serverPlayer.getUUID();
            LOGGER.debug("[InventoryHandler] Delete event for player: {}, character: {}",
                serverPlayer.getName().getString(), characterId);

            // Use player-specific lock to prevent concurrent inventory operations
            ReentrantLock playerLock = getPlayerLock(playerId);
            playerLock.lock();
            try {
                PlayerCharacterData characterData = serverPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA);
                if (characterData == null) {
                    LOGGER.error("[InventoryHandler] PlayerCharacterData is null for player {}. Cannot check character for inventory transfer.", playerId);
                    return;
                }

                CharacterProfile characterToDelete = characterData.getCharacter(characterId);
                if (characterToDelete == null) {
                    LOGGER.warn("[InventoryHandler] Character {} not found for player {}. Cannot check for inventory transfer.", characterId, playerId);
                    return;
                }

                // Continue processing regardless of character's deceased status to safeguard inventory.

                // Check if the character has any inventory items
                CompoundTag inventoryTag = characterToDelete.getModData(INVENTORY_KEY);
                if (inventoryTag == null || inventoryTag.isEmpty() || !inventoryTag.contains("Items")) {
                    LOGGER.debug("[InventoryHandler] Character {} has no inventory to transfer.", characterId);
                    return;
                }

                ListTag itemsList = inventoryTag.getList("Items", 10);
                if (itemsList.isEmpty()) {
                    LOGGER.debug("[InventoryHandler] Character {} has empty inventory, no transfer needed.", characterId);
                    return;
                }

                // Check if player's current inventory is empty
                if (!isInventoryEmpty(serverPlayer)) {
                    // Cancel the deletion and notify the player
                    event.setCanceled(true);
                    serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "gui.persona.error.inventory_not_empty_for_transfer", characterToDelete.getDisplayName()));
                    LOGGER.debug("[InventoryHandler] Cancelled deletion of character {} - player {} inventory is not empty.", 
                        characterId, serverPlayer.getName().getString());
                    return;
                }

                // Transfer the inventory to the player
                loadInventory(serverPlayer, inventoryTag);
                LOGGER.debug("[InventoryHandler] Transferred {} items from character {} to player {}",
                    itemsList.size(), characterToDelete.getDisplayName(), serverPlayer.getName().getString());
                
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.persona.success.inventory_transferred", itemsList.size(), characterToDelete.getDisplayName()));
            } finally {
                playerLock.unlock();
            }
        } catch (Exception e) {
            LOGGER.error("[InventoryHandler] Error in Delete event handler for inventory transfer", e);
        }
    }

    private static boolean isInventoryEmpty(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static CompoundTag saveInventory(ServerPlayer player) {
        CompoundTag inventoryTag = new CompoundTag();
        ListTag itemsList = new ListTag();
        int itemCount = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                itemCount++;
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                itemTag.put("Item", stack.save(player.registryAccess(), new CompoundTag()));
                itemsList.add(itemTag);
            }
        }
        inventoryTag.put("Items", itemsList);
        LOGGER.debug("[InventoryHandler] Saved {} inventory items for player {}", itemCount, player.getName().getString());
        return inventoryTag;
    }

    /**
     * Safely clears the player's inventory and broadcasts changes.
     * This method ensures the inventory is properly cleared and synchronized.
     */
    private static void clearInventorySafely(ServerPlayer player) {
        try {
            // Clear all inventory slots
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
            
            // Ensure changes are broadcast to the client
            player.inventoryMenu.broadcastChanges();
            LOGGER.debug("[InventoryHandler] Safely cleared inventory for player {}", player.getName().getString());
        } catch (Exception e) {
            LOGGER.error("[InventoryHandler] Error clearing inventory for player {}", player.getName().getString(), e);
            // Fallback to the original clear method
            player.getInventory().clearContent();
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void loadInventory(ServerPlayer player, CompoundTag inventoryTag) {
        // First, ensure the inventory is completely clear
        clearInventorySafely(player);
        
        ListTag itemsList = inventoryTag.getList("Items", 10); 
        int loadedCount = 0;

        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag itemTag = itemsList.getCompound(i);
            int slot = itemTag.getInt("Slot");
            ItemStack stack = ItemStack.parseOptional(player.registryAccess(), itemTag.getCompound("Item"));

            if (!stack.isEmpty() && slot >= 0 && slot < player.getInventory().getContainerSize()) {
                // Double-check the slot is empty before setting the item
                if (player.getInventory().getItem(slot).isEmpty()) {
                    player.getInventory().setItem(slot, stack);
                    loadedCount++;
                } else {
                    LOGGER.warn("[InventoryHandler] Slot {} was not empty when loading item {} for player {}. Skipping to prevent duplication.",
                        slot, stack.getDescriptionId(), player.getName().getString());
                }
            } else if (!stack.isEmpty()) {
                LOGGER.warn("[InventoryHandler] Invalid slot {} for item {} for player {}. Item not loaded.",
                    slot, stack.getDescriptionId(), player.getName().getString());
            }
        }
        
        // Ensure changes are broadcast to the client
        player.inventoryMenu.broadcastChanges();
        LOGGER.debug("[InventoryHandler] Loaded {} inventory items for player {}", loadedCount, player.getName().getString());
    }
} 