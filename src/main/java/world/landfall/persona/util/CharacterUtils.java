package world.landfall.persona.util;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.registry.PersonaNetworking;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing character lifecycle operations including death handling and auto-switching.
 */
public final class CharacterUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Track players who need auto-switching after respawn - thread-safe map */
    private static final Map<UUID, UUID> pendingAutoSwitches = new ConcurrentHashMap<>();

    // Private constructor to prevent instantiation
    private CharacterUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Sets a character's deceased status and handles automatic switching if needed.
     * This method should be used by addons instead of calling setDeceased directly.
     * 
     * @param player The server player who owns the character, must not be null
     * @param characterId The UUID of the character to modify, must not be null
     * @param deceased Whether the character should be marked as deceased
     * @return true if the operation was successful, false otherwise
     * @throws IllegalArgumentException if player or characterId is null
     */
    public static boolean setCharacterDeceased(@Nonnull ServerPlayer player, @Nonnull UUID characterId, boolean deceased) {
        return setCharacterDeceased(player, characterId, deceased, false);
    }

    /**
     * Sets a character's deceased status and handles automatic switching if needed.
     * 
     * @param player The server player who owns the character, must not be null
     * @param characterId The UUID of the character to modify, must not be null
     * @param deceased Whether the character should be marked as deceased
     * @param delayAutoSwitch If true, auto-switch will be delayed until after respawn
     * @return true if the operation was successful, false otherwise
     * @throws IllegalArgumentException if player or characterId is null
     */
    public static boolean setCharacterDeceased(@Nonnull ServerPlayer player, @Nonnull UUID characterId, 
                                             boolean deceased, boolean delayAutoSwitch) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(characterId, "Character ID cannot be null");

        try {
            PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (characterData == null) {
                LOGGER.warn("[CharacterUtils] No character data found for player {}", player.getName().getString());
                return false;
            }

            CharacterProfile profile = characterData.getCharacter(characterId);
            if (profile == null) {
                LOGGER.warn("[CharacterUtils] Character {} not found for player {}", characterId, player.getName().getString());
                return false;
            }

            // Set the deceased status
            profile.setDeceased(deceased);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CharacterUtils] Set deceased status for character {} ({}) to {} for player {}", 
                    profile.getDisplayName(), characterId, deceased, player.getName().getString());
            }

            // Handle automatic switching if the active character was marked as deceased
            if (deceased && characterId.equals(characterData.getActiveCharacterId())) {
                if (delayAutoSwitch) {
                    // Schedule auto-switch for after respawn
                    UUID previousPending = pendingAutoSwitches.put(player.getUUID(), characterId);
                    if (previousPending != null && LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[CharacterUtils] Replaced pending auto-switch for player {} from {} to {}", 
                            player.getName().getString(), previousPending, characterId);
                    }
                    LOGGER.info("[CharacterUtils] Scheduled delayed auto-switch for player {} after character {} death", 
                        player.getName().getString(), profile.getDisplayName());
                } else {
                    // Immediate auto-switch (existing behavior)
                    handleDeceasedActiveCharacter(player, characterData, profile);
                }
            }

            return true;
            
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error setting deceased status for character {} (player: {}): {}", 
                characterId, player.getName().getString(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sets a character's deceased status by name and handles automatic switching if needed.
     * 
     * @param player The server player who owns the character, must not be null
     * @param characterName The name of the character to modify, must not be null or empty
     * @param deceased Whether the character should be marked as deceased
     * @return true if the operation was successful, false otherwise
     * @throws IllegalArgumentException if player or characterName is null
     */
    public static boolean setCharacterDeceasedByName(@Nonnull ServerPlayer player, @Nonnull String characterName, boolean deceased) {
        return setCharacterDeceasedByName(player, characterName, deceased, false);
    }

    /**
     * Sets a character's deceased status by name and handles automatic switching if needed.
     * 
     * @param player The server player who owns the character, must not be null
     * @param characterName The name of the character to modify, must not be null or empty
     * @param deceased Whether the character should be marked as deceased
     * @param delayAutoSwitch If true, auto-switch will be delayed until after respawn
     * @return true if the operation was successful, false otherwise
     * @throws IllegalArgumentException if player or characterName is null
     */
    public static boolean setCharacterDeceasedByName(@Nonnull ServerPlayer player, @Nonnull String characterName, 
                                                   boolean deceased, boolean delayAutoSwitch) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(characterName, "Character name cannot be null");
        
        if (characterName.trim().isEmpty()) {
            LOGGER.warn("[CharacterUtils] Character name is empty for player {}", player.getName().getString());
            return false;
        }

        try {
            PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (characterData == null) {
                LOGGER.warn("[CharacterUtils] No character data found for player {}", player.getName().getString());
                return false;
            }

            // Find character by name (case-insensitive)
            Optional<CharacterProfile> profileOpt = characterData.getCharacters().values().stream()
                .filter(profile -> profile.getDisplayName().equalsIgnoreCase(characterName.trim()))
                .findFirst();

            if (profileOpt.isEmpty()) {
                LOGGER.warn("[CharacterUtils] Character '{}' not found for player {}", characterName, player.getName().getString());
                return false;
            }

            return setCharacterDeceased(player, profileOpt.get().getId(), deceased, delayAutoSwitch);
            
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error setting deceased status by name for character '{}' (player: {}): {}", 
                characterName, player.getName().getString(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Processes any pending auto-switches for a player after they respawn.
     * This should be called from a respawn event handler.
     * 
     * <p>This method is thread-safe and will only process one pending auto-switch per player.
     * If no pending auto-switch exists, this method does nothing.
     * 
     * @param player The player who just respawned, must not be null
     * @throws IllegalArgumentException if player is null
     */
    public static void processPendingAutoSwitch(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");

        try {
            UUID playerId = player.getUUID();
            UUID deceasedCharacterId = pendingAutoSwitches.remove(playerId);
            
            if (deceasedCharacterId == null) {
                return; // No pending auto-switch
            }

            LOGGER.info("[CharacterUtils] Processing pending auto-switch for player {} after respawn", 
                player.getName().getString());

            PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (characterData == null) {
                LOGGER.error("[CharacterUtils] No character data found for player {} during pending auto-switch", 
                    player.getName().getString());
                return;
            }

            CharacterProfile deceasedProfile = characterData.getCharacter(deceasedCharacterId);
            if (deceasedProfile == null) {
                LOGGER.error("[CharacterUtils] Deceased character {} not found for player {} during pending auto-switch", 
                    deceasedCharacterId, player.getName().getString());
                return;
            }

            // Now perform the auto-switch after respawn
            handleDeceasedActiveCharacter(player, characterData, deceasedProfile);
            
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error processing pending auto-switch for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }

    /**
     * Clears any pending auto-switch for a player (e.g., when they disconnect).
     * This method is thread-safe and can be called multiple times safely.
     * 
     * @param playerId The UUID of the player, must not be null
     * @throws IllegalArgumentException if playerId is null
     */
    public static void clearPendingAutoSwitch(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        
        try {
            UUID removedCharacterId = pendingAutoSwitches.remove(playerId);
            if (removedCharacterId != null && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CharacterUtils] Cleared pending auto-switch for player {} (character: {})", 
                    playerId, removedCharacterId);
            }
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error clearing pending auto-switch for player {}: {}", 
                playerId, e.getMessage(), e);
        }
    }

    /**
     * Checks if a player has a pending auto-switch.
     * This method is thread-safe.
     * 
     * @param playerId The UUID of the player, must not be null
     * @return true if the player has a pending auto-switch, false otherwise
     * @throws IllegalArgumentException if playerId is null
     */
    public static boolean hasPendingAutoSwitch(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        
        try {
            return pendingAutoSwitches.containsKey(playerId);
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error checking pending auto-switch for player {}: {}", 
                playerId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets the current number of players with pending auto-switches.
     * This method is primarily for debugging and monitoring purposes.
     * 
     * @return number of players with pending auto-switches
     */
    public static int getPendingAutoSwitchCount() {
        try {
            return pendingAutoSwitches.size();
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error getting pending auto-switch count: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Clears all pending auto-switches. This method should only be used for testing or emergency cleanup.
     * 
     * @apiNote This method is intended for administrative use only
     */
    public static void clearAllPendingAutoSwitches() {
        try {
            int clearedCount = pendingAutoSwitches.size();
            pendingAutoSwitches.clear();
            LOGGER.info("[CharacterUtils] Cleared all pending auto-switches ({} entries)", clearedCount);
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error clearing all pending auto-switches: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles the logic for switching away from a deceased active character.
     * This method performs the actual character switch and sends appropriate notifications.
     * 
     * @param player The server player, must not be null
     * @param characterData The player's character data, must not be null
     * @param deceasedProfile The deceased character profile, must not be null
     */
    private static void handleDeceasedActiveCharacter(@Nonnull ServerPlayer player, 
                                                    @Nonnull PlayerCharacterData characterData, 
                                                    @Nonnull CharacterProfile deceasedProfile) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(characterData, "Character data cannot be null");
        Objects.requireNonNull(deceasedProfile, "Deceased profile cannot be null");
        
        try {
            UUID oldActiveCharacterId = characterData.getActiveCharacterId();
            
            // Find another non-deceased character to switch to
            Optional<CharacterProfile> newActiveProfile = characterData.getCharacters().values().stream()
                .filter(Objects::nonNull)
                .filter(p -> !p.getId().equals(deceasedProfile.getId()) && !p.isDeceased())
                .findFirst();

            if (newActiveProfile.isPresent()) {
                // Switch to the new character
                CharacterProfile newProfile = newActiveProfile.get();
                characterData.setActiveCharacterId(newProfile.getId());
                
                // Send message to player
                player.sendSystemMessage(Component.translatable("command.persona.info.auto_switched_deceased", 
                    deceasedProfile.getDisplayName(), newProfile.getDisplayName()));
                
                LOGGER.info("[CharacterUtils] Auto-switched player {} from deceased character '{}' to '{}'", 
                    player.getName().getString(), deceasedProfile.getDisplayName(), newProfile.getDisplayName());
                
                // Post the event for the automatic switch
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new world.landfall.persona.registry.PersonaEvents.CharacterSwitchEvent(player, oldActiveCharacterId, newProfile.getId())
                );
                
                // Record the automatic switch for cooldown tracking
                CharacterSwitchCooldownManager.recordCharacterSwitch(player);
                
            } else {
                // No other characters available, set active to null
                characterData.setActiveCharacterId(null);
                
                // Send message to player
                player.sendSystemMessage(Component.translatable("command.persona.info.auto_switched_deceased_no_available", 
                    deceasedProfile.getDisplayName()));
                
                LOGGER.info("[CharacterUtils] No available characters for auto-switch after '{}' died for player {}", 
                    deceasedProfile.getDisplayName(), player.getName().getString());
                
                // Post the event for switching to null (no active character)
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new world.landfall.persona.registry.PersonaEvents.CharacterSwitchEvent(player, oldActiveCharacterId, null)
                );
            }
            
            // Ensure client is updated
            PersonaNetworking.sendToPlayer(characterData, player);
            
        } catch (Exception e) {
            LOGGER.error("[CharacterUtils] Error handling deceased active character '{}' for player {}: {}", 
                deceasedProfile.getDisplayName(), player.getName().getString(), e.getMessage(), e);
        }
    }
} 