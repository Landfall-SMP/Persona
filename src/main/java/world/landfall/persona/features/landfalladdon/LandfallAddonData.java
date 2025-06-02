package world.landfall.persona.features.landfalladdon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.features.landfalladdon.shells.Shell;

/**
 * Handles all LandfallAddon-specific data operations.
 * This centralizes the data management that was previously scattered in CharacterProfile.
 */
public class LandfallAddonData {
    public static final ResourceLocation DATA_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "landfall_addon_data");
    private static final ResourceLocation ORIGIN_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "origin_input");

    public static void initializeData(CharacterProfile profile) {
        CompoundTag landfallData = new CompoundTag();
        landfallData.putString("currentShell", Shell.NEUTRAL.name());
        landfallData.putInt("deathCount", 0);
        profile.setModData(DATA_KEY, landfallData);
    }

    public static Shell getCurrentShell(CharacterProfile profile) {
        CompoundTag landfallData = profile.getModData(DATA_KEY);
        if (landfallData.contains("currentShell", CompoundTag.TAG_STRING)) {
            try {
                return Shell.valueOf(landfallData.getString("currentShell"));
            } catch (IllegalArgumentException e) {
                Persona.LOGGER.warn("Failed to parse shell from LandfallAddon data, defaulting to NEUTRAL: {}", landfallData.getString("currentShell"));
                return Shell.NEUTRAL;
            }
        }
        return Shell.NEUTRAL;
    }

    public static void setCurrentShell(CharacterProfile profile, Shell shell) {
        CompoundTag landfallData = profile.getModData(DATA_KEY);
        landfallData.putString("currentShell", shell.name());
        profile.setModData(DATA_KEY, landfallData);
    }

    public static int getDeathCount(CharacterProfile profile) {
        CompoundTag landfallData = profile.getModData(DATA_KEY);
        if (landfallData.contains("deathCount", CompoundTag.TAG_INT)) {
            return landfallData.getInt("deathCount");
        }
        return 0;
    }

    public static void incrementDeathCount(CharacterProfile profile) {
        CompoundTag landfallData = profile.getModData(DATA_KEY);
        int currentCount = getDeathCount(profile);
        landfallData.putInt("deathCount", currentCount + 1);
        profile.setModData(DATA_KEY, landfallData);
    }

    /**
     * Gets the character's origin string from modData.
     * @return The origin string, or "UNKNOWN_ORIGIN" if not set or not a string.
     */
    public static String getOrigin(CharacterProfile profile) {
        CompoundTag originData = profile.getModData(ORIGIN_KEY);
        if (originData != null && originData.contains("selectedOrigin", CompoundTag.TAG_STRING)) {
            String origin = originData.getString("selectedOrigin");
            return origin.isEmpty() ? "UNKNOWN_ORIGIN" : origin;
        }
        return "UNKNOWN_ORIGIN"; // Default if not found or not a string
    }
} 