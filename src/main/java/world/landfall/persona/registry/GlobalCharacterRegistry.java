package world.landfall.persona.registry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import world.landfall.persona.Persona;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.PlayerCharacterCapability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlobalCharacterRegistry {
    private static final Map<UUID, UUID> characterToPlayerMap = new HashMap<>();
    
    public static void initialize() {
        characterToPlayerMap.clear();
        Persona.LOGGER.info("[Persona] Global Character Registry initialized.");
    }
    
    public static void registerCharacter(UUID characterId, UUID playerId) {
        characterToPlayerMap.put(characterId, playerId);
    }
    
    public static void unregisterCharacter(UUID characterId) {
        characterToPlayerMap.remove(characterId);
    }
    
    public static UUID getPlayerForCharacter(UUID characterId) {
        return characterToPlayerMap.get(characterId);
    }
    
    public static void syncRegistry(ServerPlayer player) {
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data != null) {
            data.getCharacters().forEach((characterId, profile) -> 
                registerCharacter(characterId, player.getUUID()));
        } else {
            Persona.LOGGER.warn("[Persona] Player {} missing character data for registry sync.", player.getName().getString());
        }
    }
    
    public static void removePlayerCharacters(Player player) {
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data != null) {
            data.getCharacters().keySet().forEach(GlobalCharacterRegistry::unregisterCharacter);
        } else {
            Persona.LOGGER.warn("[Persona] Player {} missing character data for character removal from registry.", player.getName().getString());
        }
    }
} 