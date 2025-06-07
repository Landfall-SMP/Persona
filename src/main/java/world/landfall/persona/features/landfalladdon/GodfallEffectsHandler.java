package world.landfall.persona.features.landfalladdon;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles godfall region effects for different origins.
 * 
 * Effects:
 * - Divinet-Touched: Speed and strength effects in godfall regions
 * - Nullborn: Weakness and mining fatigue in godfall regions, with warning message
 * - Moonspawn: No effects
 * 
 * Additionally handles immediate death for nullborn characters who die in godfall regions.
 */
@EventBusSubscriber(modid = Persona.MODID)
public class GodfallEffectsHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Attribute modifier IDs for godfall effects
    private static final ResourceLocation GODFALL_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "godfall_speed");
    private static final ResourceLocation GODFALL_STRENGTH_ID = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "godfall_strength");
    
    // Track players currently in godfall regions to avoid spam
    private static final Map<UUID, Boolean> playersInGodfall = new ConcurrentHashMap<>();
    
    // Tick counter for action bar updates (update every 20 ticks = 1 second)
    private static int tickCounter = 0;
    private static final int ACTION_BAR_UPDATE_INTERVAL = 20;
    
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
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
            LOGGER.error("[Persona] Error processing godfall effects for player {}", 
                player.getName().getString(), e);
        }
    }
    
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
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
            LOGGER.error("[Persona] Error processing nullborn godfall death for player {}", 
                player.getName().getString(), e);
        }
    }
    
    /**
     * Processes godfall effects for a player based on their character's origin.
     */
    private static void processPlayerGodfallEffects(ServerPlayer player) {
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
                LOGGER.debug("[Persona] Removed godfall effects for player {} ({})", 
                    player.getName().getString(), origin.getDisplayName());
            }
        }
    }
    
    /**
     * Applies godfall effects based on the character's origin.
     */
    private static void applyGodfallEffects(ServerPlayer player, Origin origin) {
        switch (origin) {
            case DIVINET_TOUCHED -> applyDivinetTouchedEffects(player);
            case NULLBORN -> applyNullbornEffects(player);
            case MOONSPAWN -> {
                // Moonspawn has no effects in godfall regions
            }
        }
    }
    
    /**
     * Applies speed and strength effects for Divinet-Touched characters.
     */
    private static void applyDivinetTouchedEffects(ServerPlayer player) {
        // Apply speed boost (0.02 = 20% increase)
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(GODFALL_SPEED_ID);
            AttributeModifier speedModifier = new AttributeModifier(
                GODFALL_SPEED_ID, 
                0.02, 
                AttributeModifier.Operation.ADD_VALUE
            );
            speedAttr.addPermanentModifier(speedModifier);
        }
        
        // Apply strength boost (2.0 = +1 attack damage)
        AttributeInstance attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(GODFALL_STRENGTH_ID);
            AttributeModifier strengthModifier = new AttributeModifier(
                GODFALL_STRENGTH_ID, 
                2.0, 
                AttributeModifier.Operation.ADD_VALUE
            );
            attackAttr.addPermanentModifier(strengthModifier);
        }
    }
    
    /**
     * Applies weakness and mining fatigue effects for Nullborn characters.
     */
    private static void applyNullbornEffects(ServerPlayer player) {
        // Apply weakness effect (reduces attack damage)
        MobEffectInstance weakness = new MobEffectInstance(
            MobEffects.WEAKNESS, 
            40, // 2 seconds duration (will be reapplied)
            0,  // Level 1
            false, // Not ambient
            false, // Not visible particles
            false  // Not show icon
        );
        player.addEffect(weakness);
        
        // Apply mining fatigue effect (reduces mining speed)
        MobEffectInstance miningFatigue = new MobEffectInstance(
            MobEffects.DIG_SLOWDOWN, 
            40, // 2 seconds duration (will be reapplied)
            0,  // Level 1
            false, // Not ambient
            false, // Not visible particles
            false  // Not show icon
        );
        player.addEffect(miningFatigue);
    }
    
    /**
     * Removes all godfall effects from a player.
     */
    private static void removeAllGodfallEffects(ServerPlayer player) {
        // Remove attribute modifiers
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(GODFALL_SPEED_ID);
        }
        
        AttributeInstance attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(GODFALL_STRENGTH_ID);
        }
        
        // Remove potion effects (they will naturally expire)
        // We don't forcibly remove them to avoid conflicts with other sources
    }
    
    /**
     * Handles immediate death for nullborn characters who die in godfall regions.
     */
    private static void processNullbornGodfallDeath(ServerPlayer player) {
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
        
        // Mark character as immediately deceased
        boolean success = CharacterUtils.setCharacterDeceased(player, profile.getId(), true);
        
        if (success) {
            LOGGER.debug("[Persona] Nullborn character {} died in godfall region - marked as immediately deceased", 
                profile.getDisplayName());
            
            // Send message to player
            player.sendSystemMessage(Component.literal(
                "§c§lYour nullborn core was too unstable to transmit out of the godfall zone. " +
                "Character '" + profile.getDisplayName() + "' is permanently deceased."));
        } else {
            LOGGER.error("[Persona] Failed to mark nullborn character {} as deceased after godfall death", 
                profile.getDisplayName());
        }
    }
    
    /**
     * Cleanup method to remove tracking for disconnected players.
     */
    public static void cleanupPlayer(UUID playerId) {
        playersInGodfall.remove(playerId);
    }
} 