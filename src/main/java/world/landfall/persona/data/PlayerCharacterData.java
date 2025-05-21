package world.landfall.persona.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

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
            CompoundTag profileTag = new CompoundTag();
            profileTag.putUUID("id", uuid);
            profileTag.putString("displayName", profile.getDisplayName());
            
            CompoundTag modDataTag = new CompoundTag();
            profile.getModData().forEach((modId, data) -> 
                modDataTag.put(modId.toString(), data));
            profileTag.put("modData", modDataTag);
            
            charactersList.add(profileTag);
        });
        tag.put("characters", charactersList);
        
        return tag;
    }
    
    public static PlayerCharacterData deserialize(CompoundTag tag) {
        PlayerCharacterData data = new PlayerCharacterData();
        
        if (tag.contains("activeCharacter", Tag.TAG_INT_ARRAY)) { // TAG_INT_ARRAY is for UUIDs
            data.activeCharacterId = tag.getUUID("activeCharacter");
        }
        
        ListTag charactersList = tag.getList("characters", Tag.TAG_COMPOUND);
        for (Tag t : charactersList) {
            if (t instanceof CompoundTag profileTag) { // Modern instanceof check
                UUID id = profileTag.getUUID("id");
                String displayName = profileTag.getString("displayName");
                
                CharacterProfile profile = new CharacterProfile(id, displayName);
                if (profileTag.contains("modData", Tag.TAG_COMPOUND)) {
                    CompoundTag modData = profileTag.getCompound("modData");
                    modData.getAllKeys().forEach(key -> {
                        ResourceLocation modId = ResourceLocation.tryParse(key);
                        if (modId != null && modData.contains(key, Tag.TAG_COMPOUND)) {
                            profile.setModData(modId, modData.getCompound(key));
                        }
                    });
                }
                data.characters.put(id, profile);
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
        this.characters.putAll(other.characters);
        this.activeCharacterId = other.activeCharacterId;
    }
}
