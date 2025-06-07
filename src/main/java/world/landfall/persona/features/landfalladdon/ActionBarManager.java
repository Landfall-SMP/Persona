package world.landfall.persona.features.landfalladdon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import world.landfall.persona.features.landfalladdon.decay.DecayStages;
import world.landfall.persona.features.landfalladdon.shells.Shell;

import java.util.Objects;

/**
 * Manages action bar updates for the LandfallAddon system
 */
public class ActionBarManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Constants for formatting
    private static final String DECAY_LABEL = "Decay: ";
    private static final String SHELL_LABEL = "Shell: ";
    private static final String SEPARATOR = " | ";
    private static final String FALLBACK_DECAY_TEXT = "Unknown";
    private static final String FALLBACK_SHELL_TEXT = "unknown";
    
    // Maximum lengths to prevent potential issues
    private static final int MAX_DECAY_NAME_LENGTH = 50;
    private static final int MAX_SHELL_NAME_LENGTH = 50;

    /**
     * Updates the player's action bar with current decay stage and shell information.
     */
    public static void updatePlayerStatus(ServerPlayer player, DecayStages decayStage, Shell shell) {
        Objects.requireNonNull(player, "ServerPlayer cannot be null");
        Objects.requireNonNull(decayStage, "DecayStages cannot be null");
        Objects.requireNonNull(shell, "Shell cannot be null");
        
        try {
            if (!isPlayerValid(player)) {
                LOGGER.debug("Player {} is not in a valid state for action bar update", 
                    getPlayerName(player));
                return;
            }
            
            Component message = buildActionBarMessage(decayStage, shell);
            if (message == null) {
                LOGGER.warn("Failed to build action bar message for player {}", 
                    getPlayerName(player));
                return;
            }
            
            sendActionBarMessage(player, message);
            
            LOGGER.debug("Updated action bar for player {} with decay: {} and shell: {}", 
                getPlayerName(player), decayStage, shell);
                
        } catch (Exception e) {
            LOGGER.error("Error updating action bar for player {} (decay: {}, shell: {})", 
                getPlayerName(player), decayStage, shell, e);
        }
    }

    /**
     * Builds the action bar message component with proper formatting.
     */
    private static Component buildActionBarMessage(DecayStages decayStage, Shell shell) {
        try {
            String decayDisplayName = getValidatedDecayDisplayName(decayStage);
            ChatFormatting decayColor = getDecayColor(decayStage);
            
            String shellDisplayName = getValidatedShellDisplayName(shell);
            
            return Component.literal(DECAY_LABEL)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(decayDisplayName)
                    .withStyle(decayColor))
                .append(Component.literal(SEPARATOR)
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD))
                .append(Component.literal(SHELL_LABEL)
                    .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(shellDisplayName)
                    .withStyle(ChatFormatting.GOLD));
                    
        } catch (Exception e) {
            LOGGER.error("Error building action bar message for decay: {} and shell: {}", 
                decayStage, shell, e);
            return null;
        }
    }

    /**
     * Gets the appropriate color formatting for a decay stage.
     */
    private static ChatFormatting getDecayColor(DecayStages stage) {
        if (stage == null) {
            LOGGER.warn("Null decay stage provided to getDecayColor, using default");
            return ChatFormatting.GRAY;
        }
        
        try {
            return switch (stage) {
                case STABLE -> ChatFormatting.GREEN;
                case MILD -> ChatFormatting.YELLOW;
                case MODERATE -> ChatFormatting.GOLD;
                case HIGH -> ChatFormatting.RED;
                case SEVERE -> ChatFormatting.DARK_RED;
            };
        } catch (Exception e) {
            LOGGER.error("Error getting decay color for stage {}, using default", stage, e);
            return ChatFormatting.GRAY;
        }
    }

    /**
     * Gets a validated display name for a decay stage.
     */
    private static String getValidatedDecayDisplayName(DecayStages decayStage) {
        try {
            String displayName = decayStage.getDisplayName();
            if (displayName == null || displayName.isBlank()) {
                LOGGER.warn("Decay stage {} has null/blank display name, using fallback", decayStage);
                return FALLBACK_DECAY_TEXT;
            }
            
            displayName = displayName.trim();
            if (displayName.length() > MAX_DECAY_NAME_LENGTH) {
                LOGGER.warn("Decay display name too long ({}), truncating: {}", 
                    displayName.length(), displayName);
                displayName = displayName.substring(0, MAX_DECAY_NAME_LENGTH);
            }
            
            return displayName;
            
        } catch (Exception e) {
            LOGGER.error("Error getting display name for decay stage {}, using fallback", 
                decayStage, e);
            return FALLBACK_DECAY_TEXT;
        }
    }

    /**
     * Gets a validated display name for a shell.
     */
    private static String getValidatedShellDisplayName(Shell shell) {
        try {
            String shellName = shell.name();
            if (shellName == null || shellName.isBlank()) {
                LOGGER.warn("Shell {} has null/blank name, using fallback", shell);
                return FALLBACK_SHELL_TEXT;
            }
            
            String displayName = shellName.toLowerCase().replace('_', ' ');
            
            if (displayName.length() > MAX_SHELL_NAME_LENGTH) {
                LOGGER.warn("Shell display name too long ({}), truncating: {}", 
                    displayName.length(), displayName);
                displayName = displayName.substring(0, MAX_SHELL_NAME_LENGTH);
            }
            
            return displayName;
            
        } catch (Exception e) {
            LOGGER.error("Error getting display name for shell {}, using fallback", shell, e);
            return FALLBACK_SHELL_TEXT;
        }
    }

    /**
     * Sends an action bar message to a player.
     */
    private static void sendActionBarMessage(ServerPlayer player, Component message) {
        try {
            player.displayClientMessage(message, true);
        } catch (Exception e) {
            LOGGER.error("Error sending action bar message to player {}", 
                getPlayerName(player), e);
        }
    }

    /**
     * Validates that a player is in a valid state for action bar updates.
     */
    private static boolean isPlayerValid(ServerPlayer player) {
        try {
            return player.connection != null && 
                   !player.hasDisconnected() && 
                   player.isAlive();
                   
        } catch (Exception e) {
            LOGGER.debug("Error validating player state for {}", getPlayerName(player), e);
            return false;
        }
    }

    /**
     * Gets a player's name for logging purposes.
     */
    private static String getPlayerName(ServerPlayer player) {
        try {
            if (player == null) {
                return "null";
            }
            
            var name = player.getName();
            if (name == null) {
                return "unnamed";
            }
            
            String nameString = name.getString();
            return nameString != null ? nameString : "unnamed";
            
        } catch (Exception e) {
            return "error-getting-name";
        }
    }

    /**
     * Clears the action bar for a player by sending an empty message.
     */
    public static void clearActionBar(ServerPlayer player) {
        Objects.requireNonNull(player, "ServerPlayer cannot be null");
        
        try {
            if (!isPlayerValid(player)) {
                LOGGER.debug("Player {} is not in a valid state for action bar clear", 
                    getPlayerName(player));
                return;
            }
            
            sendActionBarMessage(player, Component.empty());
            LOGGER.debug("Cleared action bar for player {}", getPlayerName(player));
            
        } catch (Exception e) {
            LOGGER.error("Error clearing action bar for player {}", getPlayerName(player), e);
        }
    }

    /**
     * Updates the action bar with a custom message.
     */
    public static void updateActionBarWithCustomMessage(ServerPlayer player, String message) {
        Objects.requireNonNull(player, "ServerPlayer cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        
        try {
            if (!isPlayerValid(player)) {
                LOGGER.debug("Player {} is not in a valid state for custom action bar update", 
                    getPlayerName(player));
                return;
            }
            
            String sanitizedMessage = message.trim();
            if (sanitizedMessage.length() > 100) {
                LOGGER.warn("Custom action bar message too long ({}), truncating", 
                    sanitizedMessage.length());
                sanitizedMessage = sanitizedMessage.substring(0, 100);
            }
            
            Component messageComponent = Component.literal(sanitizedMessage);
            sendActionBarMessage(player, messageComponent);
            
            LOGGER.debug("Updated action bar for player {} with custom message", 
                getPlayerName(player));
                
        } catch (Exception e) {
            LOGGER.error("Error updating action bar with custom message for player {}", 
                getPlayerName(player), e);
        }
    }

    /**
     * Validates that the action bar manager is properly configured.
     */
    public static boolean validateConfiguration() {
        try {
            Class.forName("net.minecraft.network.chat.Component");
            Class.forName("net.minecraft.ChatFormatting");
            Class.forName("world.landfall.persona.features.landfalladdon.decay.DecayStages");
            Class.forName("world.landfall.persona.features.landfalladdon.shells.Shell");
            
            if (DECAY_LABEL == null || SHELL_LABEL == null || SEPARATOR == null) {
                LOGGER.error("ActionBarManager constants are null");
                return false;
            }
            
            LOGGER.debug("ActionBarManager configuration validation passed");
            return true;
            
        } catch (ClassNotFoundException e) {
            LOGGER.error("ActionBarManager configuration validation failed - missing required class", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("ActionBarManager configuration validation failed", e);
            return false;
        }
    }
} 