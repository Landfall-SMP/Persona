package world.landfall.persona.features.landfalladdon.decay;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.features.aging.AgingManager;
import world.landfall.persona.features.landfalladdon.LandfallAddonData;
import world.landfall.persona.features.landfalladdon.ActionBarManager;
import world.landfall.persona.registry.PersonaEvents;
import world.landfall.persona.util.CharacterUtils;

import java.util.UUID;
import java.util.Objects;

/**
 * Handles the LandfallAddon "Decay" system
 * The decay index approximates the old KubeJS logic:
 *   decayIndex = floor(ageYears / 10) + deathCount
 * where ageYears is derived from the Aging system and deathCount is from LandfallAddonData.
 *
 * Stages:
 *   < 50  -> STABLE
 *   < 65  -> MILD
 *   < 80  -> MODERATE
 *   < 95  -> HIGH
 *   >= 95 -> SEVERE (visual)
 *   >=100 -> Character is automatically marked deceased.
 */
@EventBusSubscriber(modid = Persona.MODID)
public class DecayManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Decay thresholds
    private static final int STABLE_THRESHOLD = 50;
    private static final int MILD_THRESHOLD = 65;
    private static final int MODERATE_THRESHOLD = 80;
    private static final int HIGH_THRESHOLD = 95;
    private static final int CRITICAL_THRESHOLD = 100;
    
    // Safety limits
    private static final double MAX_AGE_YEARS = 10000.0;
    private static final int MAX_DEATH_COUNT = 10000;
    private static final double AGE_DIVISOR = 10.0;

    /**
     * Calculates the decay index for a character.
     */
    public static int calculateDecayIndex(CharacterProfile profile) {
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        
        try {
            double ageYears = getValidatedAge(profile);
            int ageComponent = (int) Math.floor(ageYears / AGE_DIVISOR);
            
            if (ageComponent < 0) {
                LOGGER.warn("Negative age component {} for character {}, using 0", 
                    ageComponent, profile.getDisplayName());
                ageComponent = 0;
            }
            
            int deathComponent = getValidatedDeathCount(profile);
            
            int decayIndex = ageComponent + deathComponent;
            
            if (decayIndex < 0) {
                LOGGER.error("Decay index overflow for character {} (age: {}, deaths: {}), using 0", 
                    profile.getDisplayName(), ageComponent, deathComponent);
                return 0;
            }
            
            LOGGER.debug("Calculated decay index {} for character {} (age component: {}, death component: {})",
                decayIndex, profile.getDisplayName(), ageComponent, deathComponent);
                
            return decayIndex;
            
        } catch (Exception e) {
            LOGGER.error("Error calculating decay index for character {}, returning 0", 
                profile.getDisplayName(), e);
            return 0;
        }
    }

    /**
     * Gets the decay stage for a given index.
     */
    public static DecayStages getStage(int index) {
        try {
            if (index < 0) {
                LOGGER.warn("Negative decay index {}, treating as 0", index);
                index = 0;
            }
            
            if (index < STABLE_THRESHOLD) return DecayStages.STABLE;
            if (index < MILD_THRESHOLD) return DecayStages.MILD;
            if (index < MODERATE_THRESHOLD) return DecayStages.MODERATE;
            if (index < HIGH_THRESHOLD) return DecayStages.HIGH;
            return DecayStages.SEVERE;
            
        } catch (Exception e) {
            LOGGER.error("Error determining decay stage for index {}, returning STABLE", index, e);
            return DecayStages.STABLE;
        }
    }

    /**
     * Checks if a decay index is at the critical threshold for automatic death.
     */
    public static boolean isCriticalDecay(int index) {
        return index >= CRITICAL_THRESHOLD;
    }

    /**
     * Evaluates and applies decay effects for a character
     */
    private static void evaluateAndApply(ServerPlayer player, CharacterProfile profile) {
        Objects.requireNonNull(player, "ServerPlayer cannot be null");
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        
        try {
            if (profile.isDeceased()) {
                LOGGER.debug("Character {} is already deceased, skipping decay evaluation", 
                    profile.getDisplayName());
                return;
            }
            
            int index = calculateDecayIndex(profile);
            DecayStages stage = getStage(index);

            LOGGER.debug("Evaluated decay for {}: index={} stage={}", 
                profile.getDisplayName(), index, stage);

            updateActionBar(player, stage, profile);

            if (isCriticalDecay(index)) {
                handleCriticalDecay(player, profile, index);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error evaluating decay for character {} (player: {})", 
                profile.getDisplayName(), player.getName().getString(), e);
        }
    }

    /**
     * Handles critical decay that results in automatic character death.
     */
    private static void handleCriticalDecay(ServerPlayer player, CharacterProfile profile, int index) {
        Objects.requireNonNull(player, "ServerPlayer cannot be null");
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        
        try {
            LOGGER.debug("Character {} reached critical decay ({}). Marking as deceased.", 
                profile.getDisplayName(), index);
                
            CharacterUtils.setCharacterDeceased(player, profile.getId(), true);
            
        } catch (Exception e) {
            LOGGER.error("Error handling critical decay for character {} (index: {})", 
                profile.getDisplayName(), index, e);
        }
    }

    /**
     * Updates the action bar for a player.
     */
    private static void updateActionBar(ServerPlayer player, DecayStages stage, CharacterProfile profile) {
        try {
            var currentShell = LandfallAddonData.getCurrentShell(profile);
            ActionBarManager.updatePlayerStatus(player, stage, currentShell);
            
        } catch (Exception e) {
            LOGGER.error("Error updating action bar for player {} (character: {})", 
                player.getName().getString(), profile.getDisplayName(), e);
        }
    }

    /**
     * Handles player login events to evaluate decay for the active character.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event == null) {
            LOGGER.error("Received null PlayerLoggedInEvent");
            return;
        }
        
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                LOGGER.debug("PlayerLoggedInEvent entity is not a ServerPlayer, ignoring");
                return;
            }
            
            processPlayerLogin(player);
            
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing player login event", e);
        }
    }

    /**
     * Handles character switch events to evaluate decay for the new active character.
     */
    @SubscribeEvent
    public static void onCharacterSwitch(PersonaEvents.CharacterSwitchEvent event) {
        if (event == null) {
            LOGGER.error("Received null CharacterSwitchEvent");
            return;
        }
        
        try {
            if (!(event.getPlayer() instanceof ServerPlayer player)) {
                LOGGER.debug("CharacterSwitchEvent player is not a ServerPlayer, ignoring");
                return;
            }
            
            UUID toId = event.getToCharacterId();
            if (toId == null) {
                LOGGER.debug("Character switch to null character for player {}", 
                    player.getName().getString());
                return;
            }
            
            processCharacterSwitch(player, toId);
            
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing character switch event", e);
        }
    }

    /**
     * Processes a player login for decay evaluation.
     */
    private static void processPlayerLogin(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            PlayerCharacterData data = getPlayerCharacterData(player);
            if (data == null) {
                LOGGER.debug("No character data for player {} on login", 
                    player.getName().getString());
                return;
            }
            
            UUID activeId = data.getActiveCharacterId();
            if (activeId == null) {
                LOGGER.debug("No active character for player {} on login", 
                    player.getName().getString());
                return;
            }
            
            CharacterProfile profile = data.getCharacter(activeId);
            if (profile == null) {
                LOGGER.warn("Active character {} not found for player {} on login", 
                    activeId, player.getName().getString());
                return;
            }
            
            evaluateAndApply(player, profile);
            
        } catch (Exception e) {
            LOGGER.error("Error processing login for player {}", 
                player.getName().getString(), e);
        }
    }

    /**
     * Processes a character switch for decay evaluation.
     */
    private static void processCharacterSwitch(ServerPlayer player, UUID toId) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(toId, "Character ID cannot be null");
        
        try {
            PlayerCharacterData data = getPlayerCharacterData(player);
            if (data == null) {
                LOGGER.debug("No character data for player {} on character switch", 
                    player.getName().getString());
                return;
            }
            
            CharacterProfile profile = data.getCharacter(toId);
            if (profile == null) {
                LOGGER.warn("Character {} not found for player {} on switch", 
                    toId, player.getName().getString());
                return;
            }
            
            evaluateAndApply(player, profile);
            
        } catch (Exception e) {
            LOGGER.error("Error processing character switch for player {} to character {}", 
                player.getName().getString(), toId, e);
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
     * Gets a validated age for a character with bounds checking.
     */
    private static double getValidatedAge(CharacterProfile profile) {
        try {
            double ageYears = AgingManager.getCalculatedAge(profile);
            
            if (Double.isNaN(ageYears) || Double.isInfinite(ageYears)) {
                LOGGER.warn("Invalid age {} for character {}, using 0", 
                    ageYears, profile.getDisplayName());
                return 0.0;
            }
            
            if (ageYears < 0) {
                LOGGER.warn("Negative age {} for character {}, using 0", 
                    ageYears, profile.getDisplayName());
                return 0.0;
            }
            
            if (ageYears > MAX_AGE_YEARS) {
                LOGGER.warn("Age {} exceeds maximum {} for character {}, clamping", 
                    ageYears, MAX_AGE_YEARS, profile.getDisplayName());
                return MAX_AGE_YEARS;
            }
            
            return ageYears;
            
        } catch (Exception e) {
            LOGGER.error("Error getting age for character {}, using 0", 
                profile.getDisplayName(), e);
            return 0.0;
        }
    }

    /**
     * Gets a validated death count for a character.
     */
    private static int getValidatedDeathCount(CharacterProfile profile) {
        try {
            int deathCount = LandfallAddonData.getDeathCount(profile);
            
            if (deathCount < 0) {
                LOGGER.warn("Negative death count {} for character {}, using 0", 
                    deathCount, profile.getDisplayName());
                return 0;
            }
            
            if (deathCount > MAX_DEATH_COUNT) {
                LOGGER.warn("Death count {} exceeds maximum {} for character {}, clamping", 
                    deathCount, MAX_DEATH_COUNT, profile.getDisplayName());
                return MAX_DEATH_COUNT;
            }
            
            return deathCount;
            
        } catch (Exception e) {
            LOGGER.error("Error getting death count for character {}, using 0", 
                profile.getDisplayName(), e);
            return 0;
        }
    }

    /**
     * Validates that the decay manager is properly configured.
     */
    public static boolean validateConfiguration() {
        try {
            Class.forName("world.landfall.persona.features.aging.AgingManager");
            Class.forName("world.landfall.persona.features.landfalladdon.LandfallAddonData");
            Class.forName("world.landfall.persona.features.landfalladdon.ActionBarManager");
            Class.forName("world.landfall.persona.util.CharacterUtils");
            
            LOGGER.debug("DecayManager configuration validation passed");
            return true;
            
        } catch (ClassNotFoundException e) {
            LOGGER.error("DecayManager configuration validation failed - missing required class", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("DecayManager configuration validation failed", e);
            return false;
        }
    }
} 