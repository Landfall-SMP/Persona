package world.landfall.persona.features.landfalladdon.origins;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import world.landfall.persona.Persona; // Assuming Persona.MODID is accessible
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.registry.PersonaEvents;
import world.landfall.persona.config.Config;

import java.util.Optional;

@EventBusSubscriber(modid = Persona.MODID) // Subscribe to Persona's event bus
public class OriginHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Using Persona's MODID for the namespace as this data is stored within Persona's CharacterProfile
    public static final ResourceLocation ORIGIN_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "origin_input");
    private static final String NO_ORIGIN_SELECTED = "UNSET";

    static {
        LOGGER.debug("OriginHandler loaded for Persona Landfall Addon features.");
    }

    @SubscribeEvent
    public static void onCharacterCreated(PersonaEvents.CharacterCreateEvent event) {
        if (!Config.ENABLE_LANDFALL_ADDONS.get()) {
            return;
        }
        CharacterProfile profile = event.getProfile();
        if (profile != null) {
            CompoundTag modData = profile.getModData(ORIGIN_KEY);
            // Initialize origin only if it's not already set (e.g. by an import or future feature)
            if (modData == null || modData.isEmpty() || !modData.contains("selectedOrigin")) {
                CompoundTag originData = new CompoundTag();
                originData.putString("selectedOrigin", NO_ORIGIN_SELECTED);
                profile.setModData(ORIGIN_KEY, originData);
                LOGGER.debug("[OriginHandler] Initialized origin as UNSET for new character {}", profile.getId());
            }
        } else {
            LOGGER.warn("[OriginHandler] CharacterProfile was null in CharacterCreateEvent for character ID: {}. Cannot set initial origin.", event.getCharacterId());
        }
    }

    public static void setOrigin(CharacterProfile profile, Origin origin) {
        if (!Config.ENABLE_LANDFALL_ADDONS.get()) {
            LOGGER.warn("[OriginHandler] Attempted to set origin while Landfall Addons are disabled.");
            return;
        }
        if (profile == null) {
            LOGGER.warn("[OriginHandler] Attempted to set origin on a null profile.");
            return;
        }
        CompoundTag originData = profile.getModData(ORIGIN_KEY);
        if (originData == null) { // Should be initialized by onCreate, but as a fallback
            originData = new CompoundTag();
        }
        originData.putString("selectedOrigin", origin.name());
        profile.setModData(ORIGIN_KEY, originData);
        LOGGER.debug("[OriginHandler] Set origin to {} for character {}", origin.name(), profile.getId());
    }

    public static Optional<Origin> getOrigin(CharacterProfile profile) {
        if (!Config.ENABLE_LANDFALL_ADDONS.get()) {
            return Optional.empty();
        }
        if (profile == null) {
            LOGGER.warn("[OriginHandler] Attempted to get origin from a null profile.");
            return Optional.empty();
        }
        CompoundTag originData = profile.getModData(ORIGIN_KEY);
        if (originData != null && originData.contains("selectedOrigin")) {
            String originName = originData.getString("selectedOrigin");
            if (NO_ORIGIN_SELECTED.equals(originName)) {
                return Optional.empty(); // No origin has been selected yet
            }
            return Origin.fromString(originName);
        }
        return Optional.empty(); // No data or key found
    }
    
    public static boolean hasSelectedOrigin(CharacterProfile profile) {
        if (!Config.ENABLE_LANDFALL_ADDONS.get()) {
            return false;
        }
        if (profile == null) return false;
        return getOrigin(profile).isPresent();
    }
} 