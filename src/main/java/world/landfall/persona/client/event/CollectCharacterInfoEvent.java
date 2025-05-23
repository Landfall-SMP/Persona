package world.landfall.persona.client.event;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.Event;
import world.landfall.persona.data.CharacterProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired on the client when the CharacterManagementScreen is gathering information
 * to display next to a character's name. Subscribers can add text components.
 * This event is fired on the {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}.
 */
public class CollectCharacterInfoEvent extends Event {
    private final CharacterProfile characterProfile;
    private final List<Component> infoComponents;

    public CollectCharacterInfoEvent(CharacterProfile characterProfile) {
        this.characterProfile = characterProfile;
        this.infoComponents = new ArrayList<>();
    }

    public CharacterProfile getCharacterProfile() {
        return characterProfile;
    }

    public List<Component> getInfoComponents() {
        return infoComponents;
    }

    /**
     * Adds a component to be displayed next to the character's name.
     * @param component The component to add.
     */
    public void addInfo(Component component) {
        this.infoComponents.add(component);
    }
} 