package world.landfall.persona.features.landfalladdon.death;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.features.landfalladdon.LandfallAddonData;
import world.landfall.persona.features.landfalladdon.shells.Shell;
import world.landfall.persona.features.landfalladdon.shells.ShellManager;
import world.landfall.persona.features.landfalladdon.decay.DecayManager;
import world.landfall.persona.features.landfalladdon.ActionBarManager;
import world.landfall.persona.util.CharacterUtils;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles player death events for the LandfallAddon system.
 */
@EventBusSubscriber(modid = Persona.MODID)
public final class DeathHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Private constructor to prevent instantiation
    private DeathHandler() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Handles player respawn events to process character death mechanics.
     * 
     * This method processes death-related mechanics after the player has respawned,
     * including death counting, shell assignment, and pending auto-switches. Processing
     * after respawn prevents inventory duplication issues.
     * 
     * @param event The player respawn event, must not be null
     */
    @SubscribeEvent
    public static void onPlayerDeath(@Nonnull PlayerEvent.PlayerRespawnEvent event) {
        Objects.requireNonNull(event, "PlayerRespawnEvent cannot be null");

        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[DeathHandler] PlayerRespawnEvent entity is not a ServerPlayer, ignoring");
                }
                return;
            }

            // Process death mechanics first
            processPlayerDeath(player);
            
            // Then process any pending auto-switches after respawn
            CharacterUtils.processPendingAutoSwitch(player);
            
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Unexpected error processing player death event: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes the death of a player character.
     * 
     * This method handles the core death mechanics including death counting,
     * shell assignment, and action bar updates. It only processes deaths for
     * characters that are not already deceased.
     * 
     * @param player The server player who died, must not be null
     */
    private static void processPlayerDeath(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            PlayerCharacterData data = getPlayerCharacterData(player);
            if (data == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[DeathHandler] No character data found for player {}, skipping death processing", 
                        player.getName().getString());
                }
                return;
            }

            UUID activeId = data.getActiveCharacterId();
            if (activeId == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[DeathHandler] No active character for player {}, skipping death processing", 
                        player.getName().getString());
                }
                return;
            }
            
            CharacterProfile currentProfile = data.getCharacter(activeId);
            if (currentProfile == null) {
                LOGGER.warn("[DeathHandler] Active character {} not found for player {}", 
                    activeId, player.getName().getString());
                return;
            }

            if (currentProfile.isDeceased()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[DeathHandler] Character '{}' is already deceased, skipping death processing", 
                        currentProfile.getDisplayName());
                }
                return;
            }

            processCharacterDeath(player, currentProfile);
            
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Error processing death for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }

    /**
     * Processes the death of a specific character.
     * 
     * This method handles the core death mechanics including:
     *   Incrementing the death count
     *   Assigning a new shell based on origin
     *   Updating the player's action bar
     * 
     * @param player The server player, must not be null
     * @param profile The character profile that died, must not be null
     */
    private static void processCharacterDeath(@Nonnull ServerPlayer player, @Nonnull CharacterProfile profile) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(profile, "Character profile cannot be null");
        
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DeathHandler] Processing death for character '{}' (player: {})", 
                    profile.getDisplayName(), player.getName().getString());
            }

            incrementDeathCount(profile);

            String origin = getOrigin(profile);
            Shell newShell = assignNewShell(profile, origin);

            int deathCount = LandfallAddonData.getDeathCount(profile);
            LOGGER.info("[DeathHandler] Character '{}' died (Origin: {}, New Shell: {}, Total Deaths: {}) for player {}",
                    profile.getDisplayName(), origin, newShell.name(), deathCount, player.getName().getString());

            updatePlayerActionBar(player, profile, newShell);

        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Error processing death for character '{}' (player: {}): {}", 
                profile.getDisplayName(), player.getName().getString(), e.getMessage(), e);
        }
    }

    /**
     * Gets player character data with error handling.
     * 
     * @param player The server player, must not be null
     * @return the player character data, or null if not available
     */
    private static PlayerCharacterData getPlayerCharacterData(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            return player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Error getting character data for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Increments the death count for a character with error handling.
     * 
     * @param profile The character profile, must not be null
     */
    private static void incrementDeathCount(@Nonnull CharacterProfile profile) {
        Objects.requireNonNull(profile, "Character profile cannot be null");
        
        try {
            LandfallAddonData.incrementDeathCount(profile);
            if (LOGGER.isDebugEnabled()) {
                int newCount = LandfallAddonData.getDeathCount(profile);
                LOGGER.debug("[DeathHandler] Incremented death count for character '{}' to {}", 
                    profile.getDisplayName(), newCount);
            }
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Failed to increment death count for character '{}': {}", 
                profile.getDisplayName(), e.getMessage(), e);
        }
    }

    /**
     * Gets the origin for a character with fallback handling.
     * 
     * @param profile The character profile, must not be null
     * @return the origin string, never null (uses fallback if needed)
     */
    @Nonnull
    private static String getOrigin(@Nonnull CharacterProfile profile) {
        Objects.requireNonNull(profile, "Character profile cannot be null");
        
        try {
            String origin = LandfallAddonData.getOrigin(profile);
            if (origin == null || origin.isBlank()) {
                LOGGER.warn("[DeathHandler] Character '{}' has null/blank origin, using UNKNOWN_ORIGIN", 
                    profile.getDisplayName());
                return "UNKNOWN_ORIGIN";
            }
            return origin;
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Error getting origin for character '{}', using UNKNOWN_ORIGIN: {}", 
                profile.getDisplayName(), e.getMessage(), e);
            return "UNKNOWN_ORIGIN";
        }
    }

    /**
     * Assigns a new shell to a character based on their origin.
     * 
     * @param profile The character profile, must not be null
     * @param origin The character's origin, must not be null
     * @return the assigned shell, never null (uses fallback if needed)
     */
    @Nonnull
    private static Shell assignNewShell(@Nonnull CharacterProfile profile, @Nonnull String origin) {
        Objects.requireNonNull(profile, "Profile cannot be null");
        Objects.requireNonNull(origin, "Origin cannot be null");
        
        try {
            Shell newShell = ShellManager.getRandomShell(origin);
            if (newShell == null) {
                LOGGER.error("[DeathHandler] ShellManager returned null shell for origin '{}', using NEUTRAL", origin);
                newShell = Shell.NEUTRAL;
            }
            
            LandfallAddonData.setCurrentShell(profile, newShell);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DeathHandler] Assigned shell {} to character '{}' (origin: {})", 
                    newShell.name(), profile.getDisplayName(), origin);
            }
            
            return newShell;
            
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Error assigning new shell for character '{}' with origin '{}', using NEUTRAL: {}", 
                profile.getDisplayName(), origin, e.getMessage(), e);
            
            try {
                LandfallAddonData.setCurrentShell(profile, Shell.NEUTRAL);
                return Shell.NEUTRAL;
            } catch (Exception fallbackError) {
                LOGGER.error("[DeathHandler] Failed to set fallback NEUTRAL shell for character '{}': {}", 
                    profile.getDisplayName(), fallbackError.getMessage(), fallbackError);
                return Shell.NEUTRAL;
            }
        }
    }

    /**
     * Updates the player's action bar with current status information.
     * 
     * @param player The server player, must not be null
     * @param profile The character profile, must not be null
     * @param shell The character's current shell, must not be null
     */
    private static void updatePlayerActionBar(@Nonnull ServerPlayer player, 
                                            @Nonnull CharacterProfile profile, 
                                            @Nonnull Shell shell) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(profile, "Profile cannot be null");
        Objects.requireNonNull(shell, "Shell cannot be null");
        
        try {
            var decayStage = DecayManager.getStage(DecayManager.calculateDecayIndex(profile));
            ActionBarManager.updatePlayerStatus(player, decayStage, shell);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DeathHandler] Updated action bar for player {} (character: '{}', shell: {}, decay: {})", 
                    player.getName().getString(), profile.getDisplayName(), shell.name(), decayStage.name());
            }
            
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Error updating action bar for player {} (character: '{}'): {}", 
                player.getName().getString(), profile.getDisplayName(), e.getMessage(), e);
        }
    }

    /**
     * Validates that the death handler is properly configured.
     * This method checks that all required dependencies are available.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public static boolean validateConfiguration() {
        try {
            // Check that all required classes are available
            Class.forName("world.landfall.persona.features.landfalladdon.LandfallAddonData");
            Class.forName("world.landfall.persona.features.landfalladdon.shells.ShellManager");
            Class.forName("world.landfall.persona.features.landfalladdon.decay.DecayManager");
            Class.forName("world.landfall.persona.features.landfalladdon.ActionBarManager");
            Class.forName("world.landfall.persona.util.CharacterUtils");
            
            LOGGER.info("[DeathHandler] Configuration validation passed");
            return true;
            
        } catch (ClassNotFoundException e) {
            LOGGER.error("[DeathHandler] Configuration validation failed - missing required class: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Configuration validation failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets statistics about death processing for monitoring purposes.
     * 
     * @return a string containing current statistics
     */
    @Nonnull
    public static String getStatistics() {
        try {
            // This could be expanded to include actual statistics if needed
            return String.format("[DeathHandler] Status: Active, Configuration: %s", 
                validateConfiguration() ? "Valid" : "Invalid");
        } catch (Exception e) {
            LOGGER.error("[DeathHandler] Error getting statistics: {}", e.getMessage(), e);
            return "[DeathHandler] Status: Error getting statistics";
        }
    }
} 