package world.landfall.persona.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import world.landfall.persona.config.Config;
import world.landfall.persona.Persona;
import world.landfall.persona.features.landfalladdon.LandfallAddonData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CharacterProfile {
    private static Pattern NAME_PATTERN = null; // Will be initialized from config
    private static final ResourceLocation IS_DECEASED_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "is_deceased"); // Key for modData

    private final UUID id;
    private String displayName;
    private final Map<ResourceLocation, CompoundTag> modData;

    static {
        updateNamePattern();
    }

    public CharacterProfile(UUID id, String displayName) throws IllegalArgumentException {
        this.id = id;
        setDisplayName(displayName, true); // Validate on creation
        this.modData = new HashMap<>();
        
        // Initialize LandfallAddon data
        LandfallAddonData.initializeData(this);

        // Initialize isDeceased in modData
        CompoundTag deceasedTag = new CompoundTag();
        deceasedTag.putBoolean("value", false);
        this.modData.put(IS_DECEASED_KEY, deceasedTag);
    }

    private CharacterProfile(UUID id, String displayName, boolean skipValidation) {
        this.id = id;
        setDisplayName(displayName, skipValidation);
        this.modData = new HashMap<>();
        
        // Initialize LandfallAddon data
        LandfallAddonData.initializeData(this);

        // Initialize isDeceased in modData
        CompoundTag deceasedTag = new CompoundTag();
        deceasedTag.putBoolean("value", false);
        this.modData.put(IS_DECEASED_KEY, deceasedTag);
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) throws IllegalArgumentException {
        setDisplayName(displayName, true); // Validate when changing name
    }

    private void setDisplayName(String displayName, boolean validate) throws IllegalArgumentException {
        if (validate && !isValidName(displayName)) {
            throw new IllegalArgumentException(Component.translatable("command.persona.error.invalid_name").getString());
        }
        this.displayName = displayName;
    }

    /**
     * Validates if a given name matches the configured pattern.
     * @param name The name to validate
     * @return true if the name is valid, false otherwise
     */
    public static boolean isValidName(String name) {
        if (NAME_PATTERN == null) {
            updateNamePattern();
        }
        // First check regex pattern
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            return false;
        }

        // Then check blacklist/whitelist
        return world.landfall.persona.util.NameListManager.isNameAllowed(name);
    }

    /**
     * Updates the name validation pattern from the config.
     * Should be called when the config is reloaded.
     * @throws PatternSyntaxException if the pattern in the config is invalid
     */
    public static void updateNamePattern() {
        NAME_PATTERN = Pattern.compile(Config.NAME_VALIDATION_REGEX.get());
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

    public boolean isDeceased() {
        CompoundTag deceasedTag = modData.get(IS_DECEASED_KEY);
        if (deceasedTag != null && deceasedTag.contains("value")) {
            return deceasedTag.getBoolean("value");
        }
        return false; // Default to false if not found or malformed
    }

    public void setDeceased(boolean deceased) {
        CompoundTag deceasedTag = new CompoundTag();
        deceasedTag.putBoolean("value", deceased);
        modData.put(IS_DECEASED_KEY, deceasedTag);
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
        CharacterProfile profile = new CharacterProfile(id, name, false); // Skip validation for stored data

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
