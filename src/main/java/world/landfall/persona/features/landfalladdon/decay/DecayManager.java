package world.landfall.persona.features.landfalladdon.decay;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
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
import world.landfall.persona.registry.PersonaEvents;
import world.landfall.persona.util.CharacterUtils;

import java.util.UUID;

/**
 * Handles the LandfallAddon "Decay" system.
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

    /* --------------------------- Calculation -------------------------------- */
    public static int calculateDecayIndex(CharacterProfile profile) {
        double ageYears = AgingManager.getCalculatedAge(profile);
        int ageComponent = (int) Math.floor(ageYears / 10.0);
        int deathComponent = LandfallAddonData.getDeathCount(profile);
        return ageComponent + deathComponent;
    }

    public static DecayStages getStage(int index) {
        if (index < 50) return DecayStages.STABLE;
        if (index < 65) return DecayStages.MILD;
        if (index < 80) return DecayStages.MODERATE;
        if (index < 95) return DecayStages.HIGH;
        return DecayStages.SEVERE;
    }

    /* --------------------------- Handling ----------------------------------- */
    private static void evaluateAndApply(ServerPlayer player, CharacterProfile profile) {
        int index = calculateDecayIndex(profile);
        DecayStages stage = getStage(index);

        LOGGER.debug("[Decay] Evaluated decay for {}: index={} stage={}", profile.getDisplayName(), index, stage);

        // Notify player of current decay status (can be refined)
        player.sendSystemMessage(Component.literal("Decay Index: " + index + " (" + stage.getDisplayName() + ")"));

        // If index >= 100, mark as deceased automatically
        if (index >= 100 && !profile.isDeceased()) {
            LOGGER.info("[Decay] Character {} reached critical decay ({}). Marking as deceased.", profile.getDisplayName(), index);
            // Use existing utility to set deceased and handle auto-switch.
            CharacterUtils.setCharacterDeceased(player, profile.getId(), true);
        }
    }

    /* --------------------------- Event Hooks -------------------------------- */

    // On player login, evaluate decay for active character
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        UUID active = data.getActiveCharacterId();
        if (active == null) return;
        CharacterProfile profile = data.getCharacter(active);
        if (profile != null) evaluateAndApply(player, profile);
    }

    // After character switch, evaluate decay for new active character
    @SubscribeEvent
    public static void onCharacterSwitch(PersonaEvents.CharacterSwitchEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        UUID toId = event.getToCharacterId();
        if (toId == null) return; // Could be switching to null
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        CharacterProfile profile = data.getCharacter(toId);
        if (profile != null) evaluateAndApply(player, profile);
    }
} 