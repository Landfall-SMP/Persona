package world.landfall.persona.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.registry.PersonaNetworking;

import java.util.Optional;
import java.util.UUID;

public class CharacterUtils {

    /**
     * Sets a character's deceased status and handles automatic switching if needed.
     * This method should be used by addons instead of calling setDeceased directly.
     * 
     * @param player The server player who owns the character
     * @param characterId The UUID of the character to modify
     * @param deceased Whether the character should be marked as deceased
     * @return true if the operation was successful, false otherwise
     */
    public static boolean setCharacterDeceased(ServerPlayer player, UUID characterId, boolean deceased) {
        if (player == null || characterId == null) {
            return false;
        }

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            return false;
        }

        CharacterProfile profile = characterData.getCharacter(characterId);
        if (profile == null) {
            return false;
        }

        // Set the deceased status
        profile.setDeceased(deceased);

        // Handle automatic switching if the active character was marked as deceased
        if (deceased && characterId.equals(characterData.getActiveCharacterId())) {
            handleDeceasedActiveCharacter(player, characterData, profile);
        }

        return true;
    }

    /**
     * Sets a character's deceased status by name and handles automatic switching if needed.
     * 
     * @param player The server player who owns the character
     * @param characterName The name of the character to modify
     * @param deceased Whether the character should be marked as deceased
     * @return true if the operation was successful, false otherwise
     */
    public static boolean setCharacterDeceasedByName(ServerPlayer player, String characterName, boolean deceased) {
        if (player == null || characterName == null) {
            return false;
        }

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            return false;
        }

        // Find character by name
        Optional<CharacterProfile> profileOpt = characterData.getCharacters().values().stream()
            .filter(profile -> profile.getDisplayName().equalsIgnoreCase(characterName))
            .findFirst();

        if (profileOpt.isEmpty()) {
            return false;
        }

        return setCharacterDeceased(player, profileOpt.get().getId(), deceased);
    }

    /**
     * Handles the logic for switching away from a deceased active character.
     */
    private static void handleDeceasedActiveCharacter(ServerPlayer player, PlayerCharacterData characterData, CharacterProfile deceasedProfile) {
        UUID oldActiveCharacterId = characterData.getActiveCharacterId();
        
        // Find another non-deceased character to switch to
        Optional<CharacterProfile> newActiveProfile = characterData.getCharacters().values().stream()
            .filter(p -> !p.getId().equals(deceasedProfile.getId()) && !p.isDeceased())
            .findFirst();

        if (newActiveProfile.isPresent()) {
            // Switch to the new character
            characterData.setActiveCharacterId(newActiveProfile.get().getId());
            
            // Send message to player
            player.sendSystemMessage(Component.translatable("command.persona.info.auto_switched_deceased", 
                deceasedProfile.getDisplayName(), newActiveProfile.get().getDisplayName()));
            
            // Post the event for the automatic switch
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new world.landfall.persona.registry.PersonaEvents.CharacterSwitchEvent(player, oldActiveCharacterId, newActiveProfile.get().getId())
            );
        } else {
            // No other characters available, set active to null
            characterData.setActiveCharacterId(null);
            
            // Send message to player
            player.sendSystemMessage(Component.translatable("command.persona.info.auto_switched_deceased_no_available", 
                deceasedProfile.getDisplayName()));
            
            // Post the event for switching to null (no active character)
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new world.landfall.persona.registry.PersonaEvents.CharacterSwitchEvent(player, oldActiveCharacterId, null)
            );
        }
        
        // Ensure client is updated
        PersonaNetworking.sendToPlayer(characterData, player);
    }
} 