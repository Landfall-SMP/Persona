package world.landfall.persona.features.figura;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import world.landfall.persona.features.figura.event.FiguraAvatarSwitchEvent;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class FiguraReflector {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Figura class and method names for reflection
    private static final String FIGURA_MOD_CLASS = "org.figuramc.figura.FiguraMod";
    private static final String GET_LOCAL_PLAYER_UUID_METHOD = "getLocalPlayerUUID";

    private static final String FETCHER_CLASS = "org.figuramc.figura.avatar.local.LocalAvatarFetcher";
    private static final String GET_LOCAL_AVATAR_DIRECTORY_METHOD = "getLocalAvatarDirectory";

    private static final String MANAGER_CLASS = "org.figuramc.figura.avatar.AvatarManager";
    private static final String LOAD_LOCAL_AVATAR_METHOD = "loadLocalAvatar";
    private static final String GET_LOADED_AVATAR_METHOD = "getLoadedAvatar";
    private static final String GET_AVATAR_FOR_PLAYER_METHOD = "getAvatarForPlayer";

    private static final String NETWORK_STUFF_CLASS = "org.figuramc.figura.backend2.NetworkStuff";
    private static final String UPLOAD_AVATAR_METHOD = "uploadAvatar";

    // Rate limiting for uploads
    private static final Deque<Long> UPLOAD_TIMESTAMPS = new ArrayDeque<>();
    private static final long RATE_LIMIT_WINDOW_MS = 2 * 60 * 1000; // 2 minutes
    private static final int MAX_UPLOADS_IN_WINDOW = 10;

    private static Boolean figuraAvailable = null;

    /**
     * Checks if the Figura mod classes seem to be available.
     * The result is cached.
     *
     * @return true if Figura classes are likely present, false otherwise.
     */
    public static boolean isFiguraAvailable() {
        if (figuraAvailable == null) {
            try {
                Class.forName(FIGURA_MOD_CLASS);
                Class.forName(FETCHER_CLASS);
                Class.forName(MANAGER_CLASS);
                Class.forName(NETWORK_STUFF_CLASS);
                figuraAvailable = true;
                LOGGER.info("Figura classes detected. Figura integration enabled.");
            } catch (ClassNotFoundException e) {
                figuraAvailable = false;
                LOGGER.info("Figura classes not detected. Figura integration disabled.");
            }
        }
        return figuraAvailable;
    }

    /**
     * Attempts to switch the player's Figura avatar by loading it from local files and then uploading it.
     * Fires a {@link FiguraAvatarSwitchEvent} with the outcome.
     * This method should only be called on the client side.
     *
     * @param avatarFolderName The name of the avatar folder within Figura's local avatar directory.
     */
    public static void performAvatarSwitch(String avatarFolderName) {
        if (!isFiguraAvailable()) {
            NeoForge.EVENT_BUS.post(new FiguraAvatarSwitchEvent(avatarFolderName, false, false, "Figura mod not detected."));
            return;
        }

        boolean success = false;
        String eventMessage = "Avatar switch initiated for '" + avatarFolderName + "'.";

        try {
            Class<?> fetcherClass = Class.forName(FETCHER_CLASS);
            Method getLocalAvatarDirectoryMethod = fetcherClass.getMethod(GET_LOCAL_AVATAR_DIRECTORY_METHOD);
            Path baseDir = (Path) getLocalAvatarDirectoryMethod.invoke(null);
            Path fullPath = baseDir.resolve(avatarFolderName);

            if (!Files.isDirectory(fullPath)) {
                eventMessage = "Figura avatar folder not found: " + fullPath;
                LOGGER.warn(eventMessage);
                NeoForge.EVENT_BUS.post(new FiguraAvatarSwitchEvent(avatarFolderName, false, true, eventMessage));
                return;
            }

            if (!canUploadInternal()) {
                eventMessage = "Figura upload rate limit reached. Try again later.";
                LOGGER.warn(eventMessage);
                NeoForge.EVENT_BUS.post(new FiguraAvatarSwitchEvent(avatarFolderName, false, true, eventMessage));
                return;
            }

            LOGGER.info("Attempting to load Figura avatar from: {}", fullPath);
            boolean loadInitiated = loadAvatarInternal(fullPath);

            if (loadInitiated) {
                LOGGER.info("Figura avatar load initiated for '{}'. Waiting for it to be ready for upload.", avatarFolderName);
                waitForLoadAndUploadInternal(avatarFolderName, 10_000); // 10 second timeout
                success = true; // Signifies the process was initiated successfully.
                // Note: The actual upload happens asynchronously via waitForLoadAndUploadInternal.
                // The success here means the steps to load and queue the upload check were successful.
            } else {
                eventMessage = "Failed to initiate Figura avatar load for '" + avatarFolderName + "'. Check logs for details.";
                // loadAvatarInternal would have logged the specific error.
            }

        } catch (Exception e) {
            LOGGER.error("Critical error during Figura avatar switch for '{}'", avatarFolderName, e);
            eventMessage = "Exception during avatar switch: " + e.getMessage();
            success = false; // Ensure success is false on exception
        } finally {
            // Post the event reflecting the outcome of *initiating* the switch process.
            NeoForge.EVENT_BUS.post(new FiguraAvatarSwitchEvent(avatarFolderName, success, true, eventMessage));
        }
    }

    private static boolean loadAvatarInternal(Path avatarPath) {
        try {
            Class<?> managerClass = Class.forName(MANAGER_CLASS);
            Method loadLocalAvatarMethod = managerClass.getMethod(LOAD_LOCAL_AVATAR_METHOD, Path.class);
            loadLocalAvatarMethod.invoke(null, avatarPath);
            LOGGER.info("Figura's loadLocalAvatar method invoked for: {}", avatarPath);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to invoke Figura's loadLocalAvatar method for '{}'", avatarPath, e);
            return false;
        }
    }

    private static boolean isCurrentAvatarLoadedInternal() {
        try {
            Class<?> figuraModClass = Class.forName(FIGURA_MOD_CLASS);
            Method getLocalPlayerUUIDMethod = figuraModClass.getMethod(GET_LOCAL_PLAYER_UUID_METHOD);
            UUID localPlayerUUID = (UUID) getLocalPlayerUUIDMethod.invoke(null);

            if (localPlayerUUID == null) {
                LOGGER.warn("Could not get local player UUID from FiguraMod.");
                return false;
            }

            Class<?> avatarManagerClass = Class.forName(MANAGER_CLASS);
            Method getLoadedAvatarMethod = avatarManagerClass.getMethod(GET_LOADED_AVATAR_METHOD, UUID.class);
            Object avatar = getLoadedAvatarMethod.invoke(null, localPlayerUUID);

            return avatar != null;
        } catch (Exception e) {
            LOGGER.error("Error while checking if Figura avatar is loaded", e);
            return false;
        }
    }

    private static boolean canUploadInternal() {
        long now = System.currentTimeMillis();
        // Remove old timestamps
        UPLOAD_TIMESTAMPS.removeIf(timestamp -> (now - timestamp > RATE_LIMIT_WINDOW_MS));
        return UPLOAD_TIMESTAMPS.size() < MAX_UPLOADS_IN_WINDOW;
    }

    private static void uploadCurrentAvatarInternal() {
        try {
            Class<?> figuraModClass = Class.forName(FIGURA_MOD_CLASS);
            Method getLocalPlayerUUIDMethod = figuraModClass.getMethod(GET_LOCAL_PLAYER_UUID_METHOD);
            UUID localPlayerUUID = (UUID) getLocalPlayerUUIDMethod.invoke(null);

            if (localPlayerUUID == null) {
                LOGGER.warn("Cannot upload avatar: Local player UUID is null.");
                return;
            }

            Class<?> avatarManagerClass = Class.forName(MANAGER_CLASS);
            Method getAvatarForPlayerMethod = avatarManagerClass.getMethod(GET_AVATAR_FOR_PLAYER_METHOD, UUID.class);
            Object avatar = getAvatarForPlayerMethod.invoke(null, localPlayerUUID);

            if (avatar == null) {
                LOGGER.warn("Cannot upload avatar: No avatar is currently loaded for UUID {}.", localPlayerUUID);
                return;
            }

            Class<?> networkStuffClass = Class.forName(NETWORK_STUFF_CLASS);
            // Need to get the actual class of the 'avatar' object for the method signature
            Method uploadAvatarMethod = networkStuffClass.getMethod(UPLOAD_AVATAR_METHOD, avatar.getClass());
            uploadAvatarMethod.invoke(null, avatar);

            UPLOAD_TIMESTAMPS.addLast(System.currentTimeMillis());
            LOGGER.info("Figura's uploadAvatar method invoked for player UUID: {}", localPlayerUUID);

        } catch (Exception e) {
            LOGGER.error("Failed to invoke Figura's uploadAvatar method", e);
        }
    }

    private static void waitForLoadAndUploadInternal(String avatarFolderNameForLogging, long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        Runnable checkTask = new Runnable() {
            @Override
            public void run() {
                if (isCurrentAvatarLoadedInternal()) {
                    LOGGER.info("Avatar '{}' confirmed loaded. Proceeding with upload.", avatarFolderNameForLogging);
                    if (canUploadInternal()) {
                        uploadCurrentAvatarInternal();
                    } else {
                        LOGGER.warn("Avatar '{}' loaded, but upload rate limit reached. Upload skipped.", avatarFolderNameForLogging);
                    }
                    return; // Task complete
                }

                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    LOGGER.warn("Timeout waiting for Figura avatar '{}' to load. Upload will not proceed.", avatarFolderNameForLogging);
                    return; // Task timed out
                }

                // Reschedule for next client tick
                // Minecraft.getInstance() ensures this is client-side
                if (Minecraft.getInstance() != null) {
                     Minecraft.getInstance().tell(this);
                } else {
                    LOGGER.error("Minecraft instance is null, cannot reschedule avatar load check for '{}'.", avatarFolderNameForLogging);
                }
            }
        };
        // Initial schedule
        if (Minecraft.getInstance() != null) {
            Minecraft.getInstance().tell(checkTask);
        } else {
             LOGGER.error("Minecraft instance is null, cannot initially schedule avatar load check for '{}'.", avatarFolderNameForLogging);
        }
    }
} 