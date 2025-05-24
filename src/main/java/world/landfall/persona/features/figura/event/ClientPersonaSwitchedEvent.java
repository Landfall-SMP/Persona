package world.landfall.persona.features.figura.event;

import net.neoforged.bus.api.Event;

/**
 * Fired on the client side after the active persona has been successfully switched.
 * This event should be fired by the Persona system's client-side logic.
 */
public class ClientPersonaSwitchedEvent extends Event {
    private final String newPersonaDisplayName;

    public ClientPersonaSwitchedEvent(String newPersonaDisplayName) {
        this.newPersonaDisplayName = newPersonaDisplayName;
    }

    /**
     * @return The display name of the persona that has just become active.
     */
    public String getNewPersonaDisplayName() {
        return newPersonaDisplayName;
    }
} 