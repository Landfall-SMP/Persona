package world.landfall.persona.features.display;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import world.landfall.persona.Persona;
import world.landfall.persona.config.Config;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.registry.PersonaEvents.CharacterSwitchEvent;

import java.util.UUID;

@EventBusSubscriber(modid = Persona.MODID)
public class DisplayNameManager {

    @SubscribeEvent
    public static void onCharacterSwitch(CharacterSwitchEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Persona.LOGGER.info("[Persona] DisplayNameManager.onCharacterSwitch for player: {}", serverPlayer.getName().getString());

        PlayerCharacterData data = serverPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) {
            Persona.LOGGER.warn("[Persona] Player {} has no PlayerCharacterData in onCharacterSwitch.", serverPlayer.getName().getString());
            return;
        }

        CharacterProfile profile = data.getCharacter(data.getActiveCharacterId());

        if (profile == null || profile.getDisplayName() == null || profile.getDisplayName().isEmpty()) {
            Persona.LOGGER.debug("[Persona] No valid profile/name for active character on switch for {}. Resetting to vanilla.", serverPlayer.getName().getString());
            serverPlayer.setCustomName(null);
            serverPlayer.setCustomNameVisible(false);
        } else {
            Component displayName = Component.literal(profile.getDisplayName());
            serverPlayer.setCustomName(displayName);
            serverPlayer.setCustomNameVisible(true);
            Persona.LOGGER.debug("[Persona] Set custom name to '{}' for {}", profile.getDisplayName(), serverPlayer.getName().getString());
        }

        serverPlayer.refreshDisplayName();
        serverPlayer.refreshTabListName();
        Persona.LOGGER.debug("[Persona] Called refreshDisplayName & refreshTabListName for {}", serverPlayer.getName().getString());

        if (serverPlayer.server != null) {
            ClientboundPlayerInfoUpdatePacket updatePacket = new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                serverPlayer
            );
            serverPlayer.server.getPlayerList().broadcastAll(updatePacket);
            Persona.LOGGER.debug("[Persona] Sent UPDATE_DISPLAY_NAME packet for {}", serverPlayer.getName().getString());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!Config.ENABLE_NAME_SYSTEM.get() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        UUID activeCharacterId = data.getActiveCharacterId();
        if (activeCharacterId == null) return;
        CharacterProfile profile = data.getCharacter(activeCharacterId);

        if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isEmpty()) {
            event.setDisplayname(Component.literal(profile.getDisplayName()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!Config.ENABLE_NAME_SYSTEM.get() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        UUID activeCharacterId = data.getActiveCharacterId();
        if (activeCharacterId == null) return;
        CharacterProfile profile = data.getCharacter(activeCharacterId);

        if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isEmpty()) {
            Component displayName;
            if (Config.SHOW_USERNAME_IN_TABLIST.get()) {
                String colorCode = Config.TABLIST_NAME_COLOR.get();
                String characterName = "§" + colorCode + profile.getDisplayName();
                String username = "§7(" + player.getName().getString() + ")";
                displayName = Component.literal(characterName + " " + username);
            } else {
                displayName = Component.literal(profile.getDisplayName());
            }
            PlayerTeam team = player.getTeam();
            if (team != null) {
                displayName = PlayerTeam.formatNameForTeam(team, displayName);
            }
            event.setDisplayName(displayName);
        }
    }
} 