package world.landfall.persona.client.gui.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import world.landfall.persona.Persona;

/**
 * Registry for input providers that can add fields to the character creation screen.
 */
public class CharacterCreationInputRegistry {
    private static final Map<ResourceLocation, CharacterCreationInputProvider> providers = new HashMap<>();
    
    /**
     * Registers an input provider.
     * @param provider The provider to register
     * @return true if registration was successful, false if a provider with the same ID already exists
     */
    public static boolean register(CharacterCreationInputProvider provider) {
        if (provider == null) return false;
        
        ResourceLocation id = provider.getId();
        if (providers.containsKey(id)) {
            Persona.LOGGER.warn("[Persona] Attempted to register duplicate CharacterCreationInputProvider with ID: {}", id);
            return false;
        }
        
        providers.put(id, provider);
        Persona.LOGGER.info("[Persona] Registered CharacterCreationInputProvider: {}", id);
        return true;
    }
    
    /**
     * Unregisters an input provider.
     * @param id The ID of the provider to unregister
     * @return The removed provider, or null if none was found
     */
    public static CharacterCreationInputProvider unregister(ResourceLocation id) {
        if (id == null) return null;
        return providers.remove(id);
    }
    
    /**
     * Gets all registered input providers, sorted by priority.
     * @return A sorted list of input providers
     */
    public static List<CharacterCreationInputProvider> getAll() {
        List<CharacterCreationInputProvider> sortedProviders = new ArrayList<>(providers.values());
        sortedProviders.sort(Comparator.comparingInt(CharacterCreationInputProvider::getPriority));
        return sortedProviders;
    }
    
    /**
     * Gets a specific input provider by ID.
     * @param id The ID of the provider to get
     * @return The provider, or null if none with that ID exists
     */
    public static CharacterCreationInputProvider get(ResourceLocation id) {
        return providers.get(id);
    }
    
    /**
     * Clears all registered providers.
     * This is mainly useful for testing or when completely resetting the system.
     */
    public static void clear() {
        providers.clear();
    }
    
    /**
     * Gets the number of registered providers.
     * @return The count of providers
     */
    public static int size() {
        return providers.size();
    }
} 