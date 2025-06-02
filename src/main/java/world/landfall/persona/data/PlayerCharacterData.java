package world.landfall.persona.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerCharacterData {
    private UUID activeCharacterId;
    private final Map<UUID, CharacterProfile> characters;
    
    public PlayerCharacterData() {
        this.characters = new HashMap<>();
        // Persona.LOGGER.info("PlayerCharacterData created.");
    }
    
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        if (activeCharacterId != null) {
            tag.putUUID("activeCharacter", activeCharacterId);
        }
        
        ListTag charactersList = new ListTag();
        characters.forEach((uuid, profile) -> {
            charactersList.add(profile.serialize());
        });
        tag.put("characters", charactersList);
        
        return tag;
    }
    
    public static PlayerCharacterData deserialize(CompoundTag tag) {
        PlayerCharacterData data = new PlayerCharacterData();
        
        if (tag.contains("activeCharacter", Tag.TAG_INT_ARRAY)) { // UUIDs are stored as TAG_INT_ARRAY
            data.activeCharacterId = tag.getUUID("activeCharacter");
        }
        
        ListTag charactersList = tag.getList("characters", Tag.TAG_COMPOUND);
        for (Tag t : charactersList) {
            if (t instanceof CompoundTag profileTag) {
                CharacterProfile profile = CharacterProfile.deserialize(profileTag);
                data.characters.put(profile.getId(), profile);
            }
        }
        return data;
    }
    
    public UUID getActiveCharacterId() {
        return activeCharacterId;
    }
    
    public void setActiveCharacterId(UUID id) {
        this.activeCharacterId = id;
    }
    
    public Map<UUID, CharacterProfile> getCharacters() {
        return characters;
    }
    
    public CharacterProfile getCharacter(UUID id) {
        return characters.get(id);
    }
    
    public void addCharacter(UUID id, CharacterProfile profile) {
        characters.put(id, profile);
    }
    
    public void removeCharacter(UUID id) {
        characters.remove(id);
        if (activeCharacterId != null && activeCharacterId.equals(id)) {
            activeCharacterId = null;
        }
    }
    
    public void copyFrom(PlayerCharacterData other) {
        this.characters.clear();
        other.characters.forEach((id, profile) -> {
            this.characters.put(id, CharacterProfile.deserialize(profile.serialize()));
        });
        this.activeCharacterId = other.activeCharacterId;
    }
}
