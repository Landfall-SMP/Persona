package world.landfall.persona.features.landfalladdon;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import world.landfall.persona.Persona;
import world.landfall.persona.config.Config;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.features.landfalladdon.origins.Origin;
import world.landfall.persona.features.landfalladdon.origins.OriginHandler;
import world.landfall.persona.util.CharacterUtils;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles godfall region effects for different origins.
 */
@EventBusSubscriber(modid = Persona.MODID)
public final class GodfallEffectsHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Attribute modifier IDs for godfall effects
    private static final ResourceLocation GODFALL_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "godfall_speed");
    private static final ResourceLocation GODFALL_STRENGTH_ID = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "godfall_strength");
    
    // Track players currently in godfall regions to avoid spam - thread-safe map
    private static final Map<UUID, Boolean> playersInGodfall = new ConcurrentHashMap<>();
    
    // Tick counter for action bar updates (update every 20 ticks = 1 second)
    private static volatile int tickCounter = 0;
    private static final int ACTION_BAR_UPDATE_INTERVAL = 20;
    
    // Effect durations and strengths
    private static final int EFFECT_DURATION_TICKS = 40; // 2 seconds (will be reapplied)
    private static final double SPEED_BOOST_AMOUNT = 0.02; // 20% increase
    private static final double STRENGTH_BOOST_AMOUNT = 2.0; // +1 attack damage
    
    // Private constructor to prevent instantiation
    private GodfallEffectsHandler() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Handles player tick events to apply godfall effects based on origin.
     * 
     * @param event The player tick event, must not be null
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull PlayerTickEvent.Post event) {
        if (!Config.ENABLE_LANDFALL_ADDONS.get()) {
            return;
        }
        
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        // Only check LFE integration if available
        if (!PersonaLFEIntegration.isLFEAvailable()) {
            return;
        }
        
        try {
            processPlayerGodfallEffects(player);
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error processing godfall effects for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Handles player death events to process nullborn godfall deaths.
     * 
     * @param event The living death event, must not be null
     */
    @SubscribeEvent
    public static void onPlayerDeath(@Nonnull LivingDeathEvent event) {
        if (!Config.ENABLE_LANDFALL_ADDONS.get()) {
            return;
        }
        
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        // Only check LFE integration if available
        if (!PersonaLFEIntegration.isLFEAvailable()) {
            return;
        }
        
        try {
            processNullbornGodfallDeath(player);
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error processing nullborn godfall death for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Processes godfall effects for a player based on their character's origin.
     * This method is called every tick for each player.
     * 
     * @param player The server player to process, must not be null
     */
    private static void processPlayerGodfallEffects(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (data == null || data.getActiveCharacterId() == null) {
                return;
            }
            
            CharacterProfile profile = data.getCharacter(data.getActiveCharacterId());
            if (profile == null || profile.isDeceased()) {
                return;
            }
            
            boolean inGodfall = PersonaLFEIntegration.isPlayerInGodfallRegion(player);
            UUID playerId = player.getUUID();
            Boolean wasInGodfall = playersInGodfall.get(playerId);
            
            // Update tracking
            playersInGodfall.put(playerId, inGodfall);
            
            // Get character origin
            var originOpt = OriginHandler.getOrigin(profile);
            if (originOpt.isEmpty()) {
                // No origin selected, remove any effects
                if (wasInGodfall != null && wasInGodfall) {
                    removeAllGodfallEffects(player);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[GodfallEffects] Removed godfall effects for player {} (no origin)", 
                            player.getName().getString());
                    }
                }
                return;
            }
            
            Origin origin = originOpt.get();
            
            if (inGodfall) {
                applyGodfallEffects(player, origin);
                
                // Update action bar for nullborn (every second)
                if (origin == Origin.NULLBORN) {
                    tickCounter++;
                    if (tickCounter >= ACTION_BAR_UPDATE_INTERVAL) {
                        tickCounter = 0;
                        ActionBarManager.updateActionBarWithCustomMessage(player, 
                            "§c§lYou are in a godfall zone. If you die, your core will be too unstable to transmit out.");
                    }
                }
            } else {
                // Player left godfall region, remove effects
                if (wasInGodfall != null && wasInGodfall) {
                    removeAllGodfallEffects(player);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[GodfallEffects] Removed godfall effects for player {} ({})", 
                            player.getName().getString(), origin.getDisplayName());
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error in processPlayerGodfallEffects for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Applies godfall effects based on the character's origin.
     * 
     * @param player The server player, must not be null
     * @param origin The character's origin, must not be null
     */
    private static void applyGodfallEffects(@Nonnull ServerPlayer player, @Nonnull Origin origin) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(origin, "Origin cannot be null");
        
        try {
            switch (origin) {
                case DIVINET_TOUCHED -> applyDivinetTouchedEffects(player);
                case NULLBORN -> applyNullbornEffects(player);
                case MOONSPAWN -> {
                    // Moonspawn has no effects in godfall regions
                }
            }
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error applying godfall effects for player {} (origin: {}): {}", 
                player.getName().getString(), origin.getDisplayName(), e.getMessage(), e);
        }
    }
    
    /**
     * Applies speed and strength effects for Divinet-Touched characters.
     * 
     * @param player The server player, must not be null
     */
    private static void applyDivinetTouchedEffects(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            // Apply speed boost
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(GODFALL_SPEED_ID);
                AttributeModifier speedModifier = new AttributeModifier(
                    GODFALL_SPEED_ID, 
                    SPEED_BOOST_AMOUNT, 
                    AttributeModifier.Operation.ADD_VALUE
                );
                speedAttr.addPermanentModifier(speedModifier);
            } else {
                LOGGER.warn("[GodfallEffects] Player {} has no movement speed attribute", player.getName().getString());
            }
            
            // Apply strength boost
            AttributeInstance attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(GODFALL_STRENGTH_ID);
                AttributeModifier strengthModifier = new AttributeModifier(
                    GODFALL_STRENGTH_ID, 
                    STRENGTH_BOOST_AMOUNT, 
                    AttributeModifier.Operation.ADD_VALUE
                );
                attackAttr.addPermanentModifier(strengthModifier);
            } else {
                LOGGER.warn("[GodfallEffects] Player {} has no attack damage attribute", player.getName().getString());
            }
            
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error applying Divinet-Touched effects for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Applies weakness and mining fatigue effects for Nullborn characters.
     * 
     * @param player The server player, must not be null
     */
    private static void applyNullbornEffects(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            // Apply weakness effect (reduces attack damage)
            MobEffectInstance weakness = new MobEffectInstance(
                MobEffects.WEAKNESS, 
                EFFECT_DURATION_TICKS,
                0,  // Level 1
                false, // Not ambient
                false, // Not visible particles
                false  // Not show icon
            );
            player.addEffect(weakness);
            
            // Apply mining fatigue effect (reduces mining speed)
            MobEffectInstance miningFatigue = new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, 
                EFFECT_DURATION_TICKS,
                0,  // Level 1
                false, // Not ambient
                false, // Not visible particles
                false  // Not show icon
            );
            player.addEffect(miningFatigue);
            
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error applying Nullborn effects for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Removes all godfall effects from a player.
     * 
     * @param player The server player, must not be null
     */
    private static void removeAllGodfallEffects(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            // Remove attribute modifiers
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(GODFALL_SPEED_ID);
            }
            
            AttributeInstance attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.removeModifier(GODFALL_STRENGTH_ID);
            }
            
            // Note: We don't forcibly remove potion effects to avoid conflicts with other sources
            // They will naturally expire since they have short durations
            
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error removing godfall effects for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Handles immediate death for nullborn characters who die in godfall regions.
     * Uses delayed auto-switching to prevent inventory duplication issues.
     * 
     * @param player The server player who died, must not be null
     */
    private static void processNullbornGodfallDeath(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (data == null || data.getActiveCharacterId() == null) {
                return;
            }
            
            CharacterProfile profile = data.getCharacter(data.getActiveCharacterId());
            if (profile == null || profile.isDeceased()) {
                return;
            }
            
            // Check if player is in godfall region
            if (!PersonaLFEIntegration.isPlayerInGodfallRegion(player)) {
                return;
            }
            
            // Check if character is nullborn
            var originOpt = OriginHandler.getOrigin(profile);
            if (originOpt.isEmpty() || originOpt.get() != Origin.NULLBORN) {
                return;
            }
            
            // Mark character as immediately deceased, but delay auto-switch until after respawn
            boolean success = CharacterUtils.setCharacterDeceased(player, profile.getId(), true, true);
            
            if (success) {
                LOGGER.info("[GodfallEffects] Nullborn character '{}' died in godfall region - marked as immediately deceased (auto-switch delayed) for player {}", 
                    profile.getDisplayName(), player.getName().getString());
                
                // Send message to player
                player.sendSystemMessage(Component.literal(
                    "§c§lYour nullborn core was too unstable to transmit out of the godfall zone. " +
                    "Character '" + profile.getDisplayName() + "' is permanently deceased."));
            } else {
                LOGGER.error("[GodfallEffects] Failed to mark nullborn character '{}' as deceased after godfall death for player {}", 
                    profile.getDisplayName(), player.getName().getString());
            }
            
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error processing nullborn godfall death for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Cleanup method to remove tracking for disconnected players.
     * This prevents memory leaks by removing godfall tracking data.
     * 
     * @param playerId The UUID of the player who disconnected, must not be null
     * @throws IllegalArgumentException if playerId is null
     */
    public static void cleanupPlayer(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        
        try {
            Boolean wasTracked = playersInGodfall.remove(playerId);
            if (wasTracked != null && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[GodfallEffects] Cleaned up godfall tracking for player {} (was in godfall: {})", 
                    playerId, wasTracked);
            }
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error cleaning up player {}: {}", playerId, e.getMessage(), e);
        }
    }
    
    /**
     * Gets the current number of players being tracked in godfall regions.
     * This method is primarily for debugging and monitoring purposes.
     * 
     * @return number of players currently being tracked
     */
    public static int getTrackedPlayerCount() {
        try {
            return playersInGodfall.size();
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error getting tracked player count: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Gets the number of players currently in godfall regions.
     * This method is primarily for debugging and monitoring purposes.
     * 
     * @return number of players currently in godfall regions
     */
    public static long getPlayersInGodfallCount() {
        try {
            return playersInGodfall.values().stream()
                .filter(Objects::nonNull)
                .filter(Boolean::booleanValue)
                .count();
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error counting players in godfall: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Clears all godfall tracking data. This method should only be used for testing or emergency cleanup.
     * 
     * @apiNote This method is intended for administrative use only
     */
    public static void clearAllTracking() {
        try {
            int clearedCount = playersInGodfall.size();
            playersInGodfall.clear();
            LOGGER.info("[GodfallEffects] Cleared all godfall tracking data ({} entries)", clearedCount);
        } catch (Exception e) {
            LOGGER.error("[GodfallEffects] Error clearing all tracking data: {}", e.getMessage(), e);
        }
    }
} 