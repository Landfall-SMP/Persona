package world.landfall.persona.features.aging;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import world.landfall.persona.Persona;
import world.landfall.persona.client.gui.input.CharacterCreationInputRegistry;
import world.landfall.persona.config.Config;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;

import java.util.Map;

@EventBusSubscriber(modid = Persona.MODID)
public class AgingManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation AGING_DATA_KEY = ResourceLocation.parse(Persona.MODID + ":aging_data");
    public static final String CREATION_TIMESTAMP_KEY = "CreationTimestampMillis";
    public static final String STARTING_AGE_KEY = "StartingAge"; // Used by AgingInputProvider and here
    
    private static final double MILLIS_PER_REAL_DAY = 24.0 * 60.0 * 60.0 * 1000.0;
    private static final ResourceLocation AGING_INPUT_PROVIDER_ID = ResourceLocation.parse(Persona.MODID + ":aging_input");

    private static boolean isAgingEnabled() {
        return Config.ENABLE_AGING_SYSTEM.get();
    }

    /**
     * Gets the time passing ratio, interpreted as real-life days per game year.
     * Defaults to 1.0 if the configured ratio is invalid (<= 0).
     */
    private static double getRealLifeDaysPerGameYearRatio() {
        double ratio = Config.TIME_PASSING_RATIO.get();
        if (ratio <= 0) {
            LOGGER.warn("Configured TIME_PASSING_RATIO is invalid ({}). Defaulting to 1.0.", ratio);
            return 1.0;
        }
        return ratio;
    }

    /**
     * Sets the initial creation timestamp for a character. If a starting age is provided
     * (e.g., from character creation GUI), the timestamp is backdated accordingly.
     * Otherwise, it's set to the current system time.
     *
     * @param profile The character profile.
     * @param agingInputData NBT data from input providers, may contain a STARTING_AGE_KEY.
     */
    public static void initializeCharacterAging(CharacterProfile profile, CompoundTag agingInputData) {
        if (!isAgingEnabled()) {
            LOGGER.debug("Aging system disabled. Skipping aging initialization for character {}.", profile.getId());
            return;
        }

        CompoundTag agingDataTag = profile.getModData(AGING_DATA_KEY);
        if (agingDataTag == null) {
            agingDataTag = new CompoundTag();
        }

        long effectiveTimestampMillis;
        double startingAge;

        if (agingInputData != null && agingInputData.contains(STARTING_AGE_KEY, Tag.TAG_ANY_NUMERIC)) {
            startingAge = agingInputData.getDouble(STARTING_AGE_KEY);
            
            // Validate against config limits
            double minAge = Config.MIN_CHARACTER_AGE.get();
            double maxAge = Config.MAX_CHARACTER_AGE.get();
            
            if (startingAge < minAge) {
                LOGGER.warn("Starting age {} for character {} is below minimum age {}. Using minimum age.", 
                           startingAge, profile.getId(), minAge);
                startingAge = minAge;
            } else if (startingAge > maxAge) {
                LOGGER.warn("Starting age {} for character {} is above maximum age {}. Using maximum age.", 
                           startingAge, profile.getId(), maxAge);
                startingAge = maxAge;
            }
        } else {
            // Use default age from config
            startingAge = Config.DEFAULT_CHARACTER_AGE.get();
            LOGGER.debug("No starting age provided for character {}. Using default age: {}", 
                        profile.getDisplayName(), startingAge);
        }

        double realLifeDaysPerGameYear = getRealLifeDaysPerGameYearRatio();
        long backdateMillis = (long) (startingAge * realLifeDaysPerGameYear * MILLIS_PER_REAL_DAY);
        effectiveTimestampMillis = System.currentTimeMillis() - backdateMillis;
        
        LOGGER.debug("Character {} created with age: {:.2f} game years. Effective creation timestamp: {}. (Ratio: {:.2f} real days/game year)",
                profile.getDisplayName(), startingAge, effectiveTimestampMillis, realLifeDaysPerGameYear);

        agingDataTag.putLong(CREATION_TIMESTAMP_KEY, effectiveTimestampMillis);
        profile.setModData(AGING_DATA_KEY, agingDataTag);
        LOGGER.debug("Set aging data for {}: {}", profile.getDisplayName(), agingDataTag);
    }

    /**
     * Initializes aging data for a new character, considering inputs from creation (like starting age).
     *
     * @param newProfile The newly created character profile.
     * @param modData Mod-specific data provided during character creation (e.g., from GUI inputs).
     */
    public static void handleCharacterCreationWithModData(CharacterProfile newProfile, Map<ResourceLocation, CompoundTag> modData) {
        if (!isAgingEnabled()) {
            LOGGER.debug("Aging system disabled. Skipping character creation aging handler for {}.", newProfile.getDisplayName());
            return;
        }
        LOGGER.debug("Handling character creation aging for {} with modData keys: {}", 
                     newProfile.getDisplayName(), modData != null ? modData.keySet() : "null");
                     
        CompoundTag agingInput = null;
        if (modData != null && modData.containsKey(AGING_INPUT_PROVIDER_ID)) {
            agingInput = modData.get(AGING_INPUT_PROVIDER_ID);
            LOGGER.debug("Found aging input data via AGING_INPUT_PROVIDER_ID for {}: {}", newProfile.getDisplayName(), agingInput);
        }
        initializeCharacterAging(newProfile, agingInput); // agingInput can be null if not found
    }

    /**
     * Calculates and logs the character's current age. Called on login or context changes.
     * Age is always derived dynamically and not stored.
     */
    public static void calculateAndUpdateAge(CharacterProfile profile) {
        if (!isAgingEnabled()) return;
        if (profile == null) {
            LOGGER.warn("calculateAndUpdateAge called with null profile.");
            return;
        }
        double currentAge = getCalculatedAge(profile);
        LOGGER.debug("Character {} ({}): Current age is approx. {:.2f} game years.",
                profile.getDisplayName(), profile.getId(), currentAge);
    }

    /**
     * Logs the current age of a character, usually triggered by player actions.
     */
    public static void triggerAgeUpdate(ServerPlayer player, CharacterProfile profile) {
        if (!isAgingEnabled()) return;
        if (profile == null) {
            LOGGER.warn("triggerAgeUpdate called with null profile for player {}.", player != null ? player.getName().getString() : "unknown");
            return;
        }
        // Player can be null if called from a non-player context, though less common for this method.
        LOGGER.debug("Triggering age update for character: {} (Player: {})", 
                     profile.getDisplayName(), player != null ? player.getName().getString() : "N/A");
        calculateAndUpdateAge(profile);
    }

    /**
     * Calculates a character's age in game years based on their creation timestamp and current real time.
     *
     * @return Age in game years, or 0.0 if aging is disabled, data is missing, or an error occurs.
     */
    public static double getCalculatedAge(CharacterProfile profile) {
        if (!isAgingEnabled()) return 0.0;
        if (profile == null) {
            LOGGER.warn("getCalculatedAge called with null profile. Returning 0.");
            return 0.0;
        }

        CompoundTag agingData = profile.getModData(AGING_DATA_KEY);
        if (agingData == null || !agingData.contains(CREATION_TIMESTAMP_KEY, Tag.TAG_LONG)) {
            LOGGER.warn("Aging data or creation timestamp not found for character {}. Cannot calculate age. Returning 0.",
                        profile.getId() != null ? profile.getId().toString() : "UNKNOWN_ID");
            return 0.0;
        }

        long creationTimestampMillis = agingData.getLong(CREATION_TIMESTAMP_KEY);
        long currentTimeMillis = System.currentTimeMillis();
        double realLifeDaysPerGameYear = getRealLifeDaysPerGameYearRatio();

        if (currentTimeMillis < creationTimestampMillis) {
            LOGGER.warn("Current time ({}) is before creation timestamp ({}) for character {}. Age might be negative or zero (indicates clock issue or bad backdating).",
                    currentTimeMillis, creationTimestampMillis, profile.getId());
        }

        double elapsedMillis = (double) (currentTimeMillis - creationTimestampMillis);
        double elapsedRealDays = elapsedMillis / MILLIS_PER_REAL_DAY;

        // realLifeDaysPerGameYear is defended against zero in its getter.
        double gameYears = elapsedRealDays / realLifeDaysPerGameYear;
        return gameYears;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isAgingEnabled()) return;

        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null || data.getActiveCharacterId() == null) {
            LOGGER.debug("Player {} logged in, but no active character data. No aging actions taken.", player.getName().getString());
            return;
        }

        CharacterProfile profile = data.getCharacter(data.getActiveCharacterId());
        if (profile != null) {
            LOGGER.debug("Player {} logged in. Checking/updating age for character {}.", player.getName().getString(), profile.getDisplayName());

            CompoundTag agingData = profile.getModData(AGING_DATA_KEY);
            boolean needsInitialization = agingData == null || !agingData.contains(CREATION_TIMESTAMP_KEY, Tag.TAG_LONG);

            if (needsInitialization) {
                LOGGER.warn("Aging data for character {} of player {} is missing or incomplete on login. Initializing timestamp with current time.", 
                            profile.getDisplayName(), player.getName().getString());
                initializeCharacterAging(profile, null); // Initialize with current time as base
            }
            calculateAndUpdateAge(profile); // Log current age
        } else {
            LOGGER.debug("Player {} logged in, but no active Persona profile found. No aging actions taken.", player.getName().getString());
        }
    }

    @EventBusSubscriber(modid = Persona.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientRegistration {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                CharacterCreationInputRegistry.register(new AgingInputProvider());
                LOGGER.debug("[Persona] Registered AgingInputProvider for character creation GUI.");
            });
        }
    }
} 