package world.landfall.persona.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CharacterProfile {
    private final UUID id;
    private String displayName;
    private final Map<ResourceLocation, CompoundTag> modData;

    public CharacterProfile(UUID id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.modData = new HashMap<>();
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Map<ResourceLocation, CompoundTag> getModData() {
        return modData;
    }

    public CompoundTag getModData(ResourceLocation modId) {
        return modData.getOrDefault(modId, new CompoundTag());
    }

    public void setModData(ResourceLocation modId, CompoundTag data) {
        modData.put(modId, data);
    }

    public void removeModData(ResourceLocation modId) {
        modData.remove(modId);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", displayName);

        CompoundTag dataTag = new CompoundTag();
        modData.forEach((modId, modData) -> dataTag.put(modId.toString(), modData));
        tag.put("characterData", dataTag);

        return tag;
    }

    public static CharacterProfile deserialize(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String name = tag.getString("name");
        CharacterProfile profile = new CharacterProfile(id, name);

        CompoundTag dataTag = tag.getCompound("characterData");
        for (String key : dataTag.getAllKeys()) {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null) {
                profile.setModData(rl, dataTag.getCompound(key));
            }
        }

        return profile;
    }
}
