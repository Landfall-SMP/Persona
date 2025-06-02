package world.landfall.persona.features.landfalladdon.death;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.features.landfalladdon.LandfallAddonData;
import world.landfall.persona.features.landfalladdon.shells.ShellManager;

import java.util.UUID;

@EventBusSubscriber(modid = Persona.MODID)
public class DeathHandler {
    @SubscribeEvent
    public static void onPlayerDeath(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;

        UUID activeId = data.getActiveCharacterId();
        if (activeId == null) return;
        
        CharacterProfile currentProfile = data.getCharacter(activeId);
        if (currentProfile == null) return;

        if (!currentProfile.isDeceased()) {
            // Increment death count
            LandfallAddonData.incrementDeathCount(currentProfile);

            // Get origin and assign new shell
            String origin = LandfallAddonData.getOrigin(currentProfile);
            if (origin.equals("UNKNOWN_ORIGIN")) {
                Persona.LOGGER.warn("Character {} has unknown origin, using default shell weights", currentProfile.getDisplayName());
            }
            LandfallAddonData.setCurrentShell(currentProfile, ShellManager.getRandomShell(origin));

            // Log the death and shell assignment
            Persona.LOGGER.info("Character {} died (Origin: {}, Shell: {}, Deaths: {})",
                    currentProfile.getDisplayName(),
                    origin,
                    LandfallAddonData.getCurrentShell(currentProfile).name(),
                    LandfallAddonData.getDeathCount(currentProfile));

            // Send message to player about new shell
            String shellName = LandfallAddonData.getCurrentShell(currentProfile).name().toLowerCase().replace('_', ' ');
            player.sendSystemMessage(Component.literal("Your soul has been assigned a " + shellName + " shell."));
        }
    }
} 