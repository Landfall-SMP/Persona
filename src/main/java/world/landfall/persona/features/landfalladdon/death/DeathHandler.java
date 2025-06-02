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

import java.util.UUID;
import java.util.Objects;

/**
 * Handles player death events for the LandfallAddon system.
 * Manages death counting, shell assignment, and decay progression when characters die.
 */
@EventBusSubscriber(modid = Persona.MODID)
public class DeathHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Handles player respawn events to process character death mechanics.
     */
    @SubscribeEvent
    public static void onPlayerDeath(PlayerEvent.PlayerRespawnEvent event) {
        if (event == null) {
            LOGGER.error("Received null PlayerRespawnEvent");
            return;
        }

        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                LOGGER.debug("PlayerRespawnEvent entity is not a ServerPlayer, ignoring");
                return;
            }

            processPlayerDeath(player);
            
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing player death event", e);
        }
    }

    /**
     * Processes the death of a player character.
     */
    private static void processPlayerDeath(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            PlayerCharacterData data = getPlayerCharacterData(player);
            if (data == null) {
                LOGGER.debug("No character data found for player {}, skipping death processing", 
                    player.getName().getString());
                return;
            }

            UUID activeId = data.getActiveCharacterId();
            if (activeId == null) {
                LOGGER.debug("No active character for player {}, skipping death processing", 
                    player.getName().getString());
                return;
            }
            
            CharacterProfile currentProfile = data.getCharacter(activeId);
            if (currentProfile == null) {
                LOGGER.warn("Active character {} not found for player {}", 
                    activeId, player.getName().getString());
                return;
            }

            if (currentProfile.isDeceased()) {
                LOGGER.debug("Character {} is already deceased, skipping death processing", 
                    currentProfile.getDisplayName());
                return;
            }

            processCharacterDeath(player, currentProfile);
            
        } catch (Exception e) {
            LOGGER.error("Error processing death for player {}", player.getName().getString(), e);
        }
    }

    /**
     * Processes the death of a specific character.
     */
    private static void processCharacterDeath(ServerPlayer player, CharacterProfile profile) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(profile, "Character profile cannot be null");
        
        try {
            LOGGER.info("Processing death for character {} (player: {})", 
                profile.getDisplayName(), player.getName().getString());

            incrementDeathCount(profile);

            String origin = getOrigin(profile);
            Shell newShell = assignNewShell(profile, origin);

            int deathCount = LandfallAddonData.getDeathCount(profile);
            LOGGER.info("Character {} died (Origin: {}, New Shell: {}, Total Deaths: {})",
                    profile.getDisplayName(), origin, newShell.name(), deathCount);

            updatePlayerActionBar(player, profile, newShell);

        } catch (Exception e) {
            LOGGER.error("Error processing death for character {} (player: {})", 
                profile.getDisplayName(), player.getName().getString(), e);
        }
    }

    /**
     * Gets player character data.
     */
    private static PlayerCharacterData getPlayerCharacterData(ServerPlayer player) {
        try {
            return player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        } catch (Exception e) {
            LOGGER.error("Error getting character data for player {}", 
                player.getName().getString(), e);
            return null;
        }
    }

    /**
     * Increments the death count for a character.
     */
    private static void incrementDeathCount(CharacterProfile profile) {
        try {
            LandfallAddonData.incrementDeathCount(profile);
        } catch (Exception e) {
            LOGGER.error("Failed to increment death count for character {}", 
                profile.getDisplayName(), e);
        }
    }

    /**
     * Gets the origin for a character with fallback handling.
     */
    private static String getOrigin(CharacterProfile profile) {
        try {
            String origin = LandfallAddonData.getOrigin(profile);
            if (origin == null || origin.isBlank()) {
                LOGGER.warn("Character {} has null/blank origin, using UNKNOWN_ORIGIN", 
                    profile.getDisplayName());
                return "UNKNOWN_ORIGIN";
            }
            return origin;
        } catch (Exception e) {
            LOGGER.error("Error getting origin for character {}, using UNKNOWN_ORIGIN", 
                profile.getDisplayName(), e);
            return "UNKNOWN_ORIGIN";
        }
    }

    /**
     * Assigns a new shell to a character based on their origin.
     */
    private static Shell assignNewShell(CharacterProfile profile, String origin) {
        Objects.requireNonNull(profile, "Profile cannot be null");
        Objects.requireNonNull(origin, "Origin cannot be null");
        
        try {
            Shell newShell = ShellManager.getRandomShell(origin);
            if (newShell == null) {
                LOGGER.error("ShellManager returned null shell for origin {}, using NEUTRAL", origin);
                newShell = Shell.NEUTRAL;
            }
            
            LandfallAddonData.setCurrentShell(profile, newShell);
            return newShell;
            
        } catch (Exception e) {
            LOGGER.error("Error assigning new shell for character {} with origin {}, using NEUTRAL", 
                profile.getDisplayName(), origin, e);
            
            try {
                LandfallAddonData.setCurrentShell(profile, Shell.NEUTRAL);
                return Shell.NEUTRAL;
            } catch (Exception fallbackError) {
                LOGGER.error("Failed to set fallback NEUTRAL shell for character {}", 
                    profile.getDisplayName(), fallbackError);
                return Shell.NEUTRAL;
            }
        }
    }

    /**
     * Updates the player's action bar with current status information.
     */
    private static void updatePlayerActionBar(ServerPlayer player, CharacterProfile profile, Shell shell) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(profile, "Profile cannot be null");
        Objects.requireNonNull(shell, "Shell cannot be null");
        
        try {
            var decayStage = DecayManager.getStage(DecayManager.calculateDecayIndex(profile));
            ActionBarManager.updatePlayerStatus(player, decayStage, shell);
            
        } catch (Exception e) {
            LOGGER.error("Error updating action bar for player {} (character: {})", 
                player.getName().getString(), profile.getDisplayName(), e);
        }
    }

    /**
     * Validates that the death handler is properly configured.
     */
    public static boolean validateConfiguration() {
        try {
            Class.forName("world.landfall.persona.features.landfalladdon.LandfallAddonData");
            Class.forName("world.landfall.persona.features.landfalladdon.shells.ShellManager");
            Class.forName("world.landfall.persona.features.landfalladdon.decay.DecayManager");
            Class.forName("world.landfall.persona.features.landfalladdon.ActionBarManager");
            
            LOGGER.info("DeathHandler configuration validation passed");
            return true;
            
        } catch (ClassNotFoundException e) {
            LOGGER.error("DeathHandler configuration validation failed - missing required class", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("DeathHandler configuration validation failed", e);
            return false;
        }
    }
} 