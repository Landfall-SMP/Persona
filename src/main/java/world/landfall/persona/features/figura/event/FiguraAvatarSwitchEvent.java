package world.landfall.persona.features.figura.event;

import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Fired on the client side after an attempt to switch the Figura avatar.
 * This event is fired regardless of whether Figura is installed or if the avatar switch was successful.
 * Check {@link #isSuccess()} and {@link #isFiguraInstalled()} before proceeding.
 */
public class FiguraAvatarSwitchEvent extends Event {
    private final String avatarFolderName;
    private final boolean success;
    private final boolean figuraInstalled;
    @Nullable
    private final String message;

    public FiguraAvatarSwitchEvent(String avatarFolderName, boolean success, boolean figuraInstalled, @Nullable String message) {
        this.avatarFolderName = avatarFolderName;
        this.success = success;
        this.figuraInstalled = figuraInstalled;
        this.message = message;
    }

    /**
     * @return The name of the avatar folder that was targeted for the switch.
     */
    public String getAvatarFolderName() {
        return avatarFolderName;
    }

    /**
     * @return {@code true} if the switch operation (e.g., calling Figura's methods via reflection)
     *         was completed without throwing an immediate exception, {@code false} otherwise.
     *         This does not guarantee the avatar visually changed, only that the attempt was made.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return {@code true} if Figura appears to be installed and its classes were accessible, {@code false} otherwise.
     */
    public boolean isFiguraInstalled() {
        return figuraInstalled;
    }

    /**
     * @return An optional message providing more details about the switch attempt,
     *         such as an error message if {@link #isSuccess()} is {@code false}.
     */
    @Nullable
    public String getMessage() {
        return message;
    }
} 