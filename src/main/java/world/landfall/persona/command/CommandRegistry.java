package world.landfall.persona.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import world.landfall.persona.Persona;
import world.landfall.persona.config.Config;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.registry.GlobalCharacterRegistry;
import world.landfall.persona.registry.PersonaNetworking;
import world.landfall.persona.registry.RegistryPersistence;
import world.landfall.persona.features.aging.AgingManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.nbt.Tag;
import com.mojang.brigadier.arguments.BoolArgumentType;
import world.landfall.persona.util.CharacterUtils;
import world.landfall.persona.features.figura.event.ClientPersonaSwitchedEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;
import java.util.UUID;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;

public class CommandRegistry {

    private static void sendError(ServerPlayer player, Component message, boolean fromGui) {
        if (!fromGui) {
            player.sendSystemMessage(message.copy().withStyle(style -> style.withColor(0xFF0000)));
        }
    }

    private static void sendSuccess(ServerPlayer player, Component message, boolean fromGui) {
        if (!fromGui) {
            player.sendSystemMessage(message);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> personaCommand = Commands.literal(Persona.MODID)
            .then(Commands.literal("create")
                .then(Commands.argument("displayName", StringArgumentType.string())
                    .executes(CommandRegistry::createCharacter)))
            .then(Commands.literal("switch")
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.string())
                    .executes(CommandRegistry::switchCharacter)))
            .then(Commands.literal("list")
                .executes(CommandRegistry::listCharacters))
            .then(Commands.literal("delete")
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.string())
                    .executes(CommandRegistry::deleteCharacter)))
            .then(Commands.literal("rename")
                .then(Commands.argument("newName", StringArgumentType.string())
                    .executes(CommandRegistry::renameCharacter)));

        LiteralArgumentBuilder<CommandSourceStack> debugCommand = Commands.literal("debug")
            .requires(source -> source.hasPermission(2)) // Requires permission level 2 (ops)
            .then(Commands.literal("registry")
                .executes(CommandRegistry::debugRegistry))
            .then(Commands.literal("characterdata")
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.string())
                    .executes(CommandRegistry::debugCharacterData)))
            .then(Commands.literal("ageinfo")
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.string())
                    .executes(CommandRegistry::debugAgeInfo)))
            .then(Commands.literal("setdeceased")
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.string())
                    .then(Commands.argument("isDeceased", BoolArgumentType.bool())
                        .executes(CommandRegistry::debugSetDeceased))));
        
        personaCommand.then(debugCommand); // Nest debug under persona

        LiteralArgumentBuilder<CommandSourceStack> adminCommand = Commands.literal("admin")
            .requires(source -> source.hasPermission(2)) // Requires permission level 2 (ops)
            .then(Commands.literal("listall")
                .then(Commands.argument("playerName", StringArgumentType.word())
                    .executes(CommandRegistry::adminListCharacters)))
            .then(Commands.literal("forcedelete")
                .then(Commands.argument("playerName", StringArgumentType.word())
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.string())
                    .executes(CommandRegistry::adminDeleteCharacter))))
            .then(Commands.literal("forcerename")
                .then(Commands.argument("playerName", StringArgumentType.word())
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.string())
                .then(Commands.argument("newName", StringArgumentType.string())
                    .executes(CommandRegistry::adminRenameCharacter)))));
        
        personaCommand.then(adminCommand); // Nest admin under persona
        
        dispatcher.register(personaCommand);
    }

    private static int createCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String displayName = StringArgumentType.getString(context, "displayName");
        // For command-based creation, modData is empty (no GUI inputs)
        Map<ResourceLocation, CompoundTag> modData = new HashMap<>();
        internalCreateCharacter(player, displayName, false, modData);
        return 1;
    }

    private static int switchCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            sendError(player, Component.translatable("command.persona.error.data_not_found"), false);
            return 0;
        }

        UUID foundCharacterId = null;
        try {
            foundCharacterId = UUID.fromString(nameOrUUID);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try to find by display name
            for (CharacterProfile profile : characterData.getCharacters().values()) {
                if (profile.getDisplayName().equalsIgnoreCase(nameOrUUID)) {
                    foundCharacterId = profile.getId();
                    break;
                }
            }
        }

        if (foundCharacterId == null) {
            sendError(player, Component.translatable("command.persona.error.not_found", nameOrUUID), false);
            return 0;
        }

        final UUID targetCharacterId = foundCharacterId; // Make it effectively final for lambda
        CharacterProfile targetProfile = characterData.getCharacter(targetCharacterId);

        if (targetProfile == null) { 
            sendError(player, Component.translatable("command.persona.error.char_not_found_or_not_yours", nameOrUUID), false);
            return 0;
        }

        // Check if the character is deceased
        if (targetProfile.isDeceased()) {
            sendError(player, Component.translatable("command.persona.error.char_is_deceased", targetProfile.getDisplayName()), false);
            return 0;
        }

        UUID oldActiveCharacterId = characterData.getActiveCharacterId(); // Get the old active ID *before* changing it

        if (targetCharacterId.equals(oldActiveCharacterId)){
            sendError(player, Component.translatable("command.persona.error.already_active", targetProfile.getDisplayName()), false);
            return 0;
        }

        // Update active character ID in data
        characterData.setActiveCharacterId(targetCharacterId); 

        // Post the event
        Persona.LOGGER.info("[Persona] Posting CharacterSwitchEvent for player: {}, from: {}, to: {}", 
            player.getName().getString(), 
            (oldActiveCharacterId != null ? oldActiveCharacterId.toString() : "null"), 
            targetCharacterId.toString());
            
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new world.landfall.persona.registry.PersonaEvents.CharacterSwitchEvent(player, oldActiveCharacterId, targetCharacterId)
        );
        Persona.LOGGER.info("[Persona] CharacterSwitchEvent was posted.");

        sendSuccess(player, Component.translatable("command.persona.success.switch", targetProfile.getDisplayName()), false);
        return 1;
    }

    private static int listCharacters(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);

        if (characterData == null || characterData.getCharacters().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("command.persona.error.no_characters"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n§6=== Your Characters ===§r\n");
        characterData.getCharacters().forEach((uuid, profile) -> {
            if (uuid.equals(characterData.getActiveCharacterId())) {
                sb.append("§a➤ "); // Green arrow for active character
            } else {
                sb.append("§7• "); // Gray bullet for inactive characters
            }
            sb.append("§f").append(profile.getDisplayName()); // White text for name
            sb.append(" §8(").append(uuid.toString().substring(0, 8)).append(")"); // Gray UUID
            if (uuid.equals(characterData.getActiveCharacterId())) {
                sb.append(" §a(Active)"); // Green active indicator
            }
            sb.append("§r\n");
        });

        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int deleteCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");

        // Check if character deletion is enabled
        if (!Config.ENABLE_CHARACTER_DELETION.get()) {
            sendError(player, Component.translatable("command.persona.error.deletion_disabled"), false);
            return 0;
        }

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            sendError(player, Component.translatable("command.persona.error.data_not_found"), false);
            return 0;
        }

        UUID foundCharacterId = null;
        try {
            foundCharacterId = UUID.fromString(nameOrUUID);
        } catch (IllegalArgumentException e) {
            for (CharacterProfile profile : characterData.getCharacters().values()) {
                if (profile.getDisplayName().equalsIgnoreCase(nameOrUUID)) {
                    foundCharacterId = profile.getId();
                    break;
                }
            }
        }

        if (foundCharacterId == null) {
            sendError(player, Component.translatable("command.persona.error.not_found", nameOrUUID), false);
            return 0;
        }

        CharacterProfile targetProfile = characterData.getCharacter(foundCharacterId);
        if (targetProfile == null) {
            sendError(player, Component.translatable("command.persona.error.char_not_found_or_not_yours", nameOrUUID), false);
            return 0;
        }

        // Check if trying to delete active character
        if (foundCharacterId.equals(characterData.getActiveCharacterId())) {
            sendError(player, Component.translatable("command.persona.error.delete_active"), false);
            return 0;
        }

        String characterName = targetProfile.getDisplayName();
        characterData.removeCharacter(foundCharacterId);
        GlobalCharacterRegistry.unregisterCharacter(foundCharacterId, characterName);
        sendSuccess(player, Component.translatable("command.persona.success.delete", targetProfile.getDisplayName()), false);
        return 1;
    }

    private static int renameCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String newName = StringArgumentType.getString(context, "newName");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            sendError(player, Component.translatable("command.persona.error.data_not_found"), false);
            return 0;
        }

        UUID activeCharacterId = characterData.getActiveCharacterId();
        if (activeCharacterId == null) {
            sendError(player, Component.translatable("command.persona.error.no_active_char"), false);
            return 0;
        }

        CharacterProfile activeProfile = characterData.getCharacter(activeCharacterId);
        if (activeProfile == null) { // Should technically not happen if activeCharacterId is not null
            sendError(player, Component.translatable("command.persona.error.active_char_not_found"), false);
            return 0;
        }

        // Validate new name format
        if (!CharacterProfile.isValidName(newName)) {
            sendError(player, Component.translatable("command.persona.error.invalid_name"), false);
            return 0;
        }

        // Check if new name is taken globally, unless it's the same as current active character (case-insensitive)
        if (!newName.equalsIgnoreCase(activeProfile.getDisplayName()) && GlobalCharacterRegistry.isNameTaken(newName)) {
            Optional<UUID> existingCharId = GlobalCharacterRegistry.getCharacterIdByName(newName);
            Optional<UUID> ownerUUID = existingCharId.flatMap(GlobalCharacterRegistry::getPlayerForCharacter);
            
            String errorKey = ownerUUID.isPresent() && ownerUUID.get().equals(player.getUUID()) ?
                "command.persona.error.name_taken_self" : "command.persona.error.name_taken_other";
            sendError(player, Component.translatable(errorKey, newName), false);
            return 0;
        }

        String oldName = activeProfile.getDisplayName();
        GlobalCharacterRegistry.unregisterCharacter(activeCharacterId, oldName);
        activeProfile.setDisplayName(newName);
        GlobalCharacterRegistry.registerCharacter(activeCharacterId, player.getUUID(), newName);
        
        sendSuccess(player, Component.translatable("command.persona.success.renamed", newName), false);
        return 1;
    }

    private static int debugRegistry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Map<UUID, UUID> characterToPlayerMap = GlobalCharacterRegistry.getCharacterToPlayerMap();
        Map<String, UUID> characterNameMap = GlobalCharacterRegistry.getCharacterNameMap();

        StringBuilder sb = new StringBuilder();
        sb.append("\n§6=== Character Registry Debug ===§r\n");
        
        sb.append("\n§eCharacter to Player Mappings:§r\n");
        if (characterToPlayerMap.isEmpty()) {
            sb.append("§7  No character mappings found§r\n");
        } else {
            characterToPlayerMap.forEach((charId, playerId) -> {
                final String characterName = characterNameMap.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(charId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("Unknown");

                sb.append("§7• §f").append(characterName)
                  .append(" §8(").append(charId.toString().substring(0, 8)).append(")")
                  .append(" §7→ §f").append(playerId.toString().substring(0, 8))
                  .append("§r\n");
            });
        }

        sb.append("\n§eCharacter Name Mappings:§r\n");
        if (characterNameMap.isEmpty()) {
            sb.append("§7  No name mappings found§r\n");
        } else {
            characterNameMap.forEach((name, charId) -> {
                sb.append("§7• §f").append(name)
                  .append(" §7→ §8").append(charId.toString().substring(0, 8))
                  .append("§r\n");
            });
        }

        // Show registry file location
        Path registryPath = RegistryPersistence.getRegistryPath();
        if (registryPath != null) {
            sb.append("\n§eRegistry File Location:§r\n");
            sb.append("§7  ").append(registryPath).append("§r\n");
        }

        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int adminListCharacters(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "playerName");
        ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        
        if (targetPlayer == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.player_not_found", playerName));
            return 0;
        }

        PlayerCharacterData characterData = targetPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null || characterData.getCharacters().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("command.persona.admin.no_characters", playerName), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n§6=== Characters for §f").append(playerName).append(" §6===§r\n");
        characterData.getCharacters().forEach((uuid, profile) -> {
            if (uuid.equals(characterData.getActiveCharacterId())) {
                sb.append("§a➤ "); // Green arrow for active character
            } else {
                sb.append("§7• "); // Gray bullet for inactive characters
            }
            sb.append("§f").append(profile.getDisplayName()); // White text for name
            sb.append(" §8(").append(uuid.toString().substring(0, 8)).append(")"); // Gray UUID
            if (uuid.equals(characterData.getActiveCharacterId())) {
                sb.append(" §a(Active)"); // Green active indicator
            }
            sb.append("§r\n");
        });

        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int adminDeleteCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "playerName");
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");
        ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        
        if (targetPlayer == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.player_not_found", playerName));
            return 0;
        }

        PlayerCharacterData characterData = targetPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.data_not_found"));
            return 0;
        }

        UUID foundCharacterId = findCharacterId(nameOrUUID, characterData);
        if (foundCharacterId == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.not_found", nameOrUUID));
            return 0;
        }

        CharacterProfile targetProfile = characterData.getCharacter(foundCharacterId);
        if (targetProfile == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.char_not_found_or_not_yours", nameOrUUID));
            return 0;
        }

        // If it's the active character, force switch to another character first
        if (foundCharacterId.equals(characterData.getActiveCharacterId())) {
            Optional<UUID> newActiveId = characterData.getCharacters().keySet().stream()
                .filter(id -> !id.equals(foundCharacterId))
                .findFirst();
            
            if (newActiveId.isPresent()) {
                characterData.setActiveCharacterId(newActiveId.get());
            } else {
                characterData.setActiveCharacterId(null);
            }
        }

        String characterName = targetProfile.getDisplayName();
        characterData.removeCharacter(foundCharacterId);
        GlobalCharacterRegistry.unregisterCharacter(foundCharacterId, characterName);
        
        context.getSource().sendSuccess(() -> Component.translatable("command.persona.admin.success.delete", 
            characterName, playerName), true);
        return 1;
    }

    private static int adminRenameCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "playerName");
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");
        String newName = StringArgumentType.getString(context, "newName");
        ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        
        if (targetPlayer == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.player_not_found", playerName));
            return 0;
        }

        PlayerCharacterData characterData = targetPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.data_not_found"));
            return 0;
        }

        UUID foundCharacterId = findCharacterId(nameOrUUID, characterData);
        if (foundCharacterId == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.not_found", nameOrUUID));
            return 0;
        }

        CharacterProfile targetProfile = characterData.getCharacter(foundCharacterId);
        if (targetProfile == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.char_not_found_or_not_yours", nameOrUUID));
            return 0;
        }

        // Validate new name format
        if (!CharacterProfile.isValidName(newName)) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.invalid_name"));
            return 0;
        }

        // Check if new name is taken globally, unless it's the same as current character (case-insensitive)
        if (!newName.equalsIgnoreCase(targetProfile.getDisplayName()) && GlobalCharacterRegistry.isNameTaken(newName)) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.name_taken_other", newName));
            return 0;
        }

        String oldName = targetProfile.getDisplayName();
        GlobalCharacterRegistry.unregisterCharacter(foundCharacterId, oldName);
        targetProfile.setDisplayName(newName);
        GlobalCharacterRegistry.registerCharacter(foundCharacterId, targetPlayer.getUUID(), newName);
        
        context.getSource().sendSuccess(() -> Component.translatable("command.persona.admin.success.rename", 
            oldName, newName, playerName), true);
        return 1;
    }

    private static UUID findCharacterId(String nameOrUUID, PlayerCharacterData characterData) {
        try {
            return UUID.fromString(nameOrUUID);
        } catch (IllegalArgumentException e) {
            for (CharacterProfile profile : characterData.getCharacters().values()) {
                if (profile.getDisplayName().equalsIgnoreCase(nameOrUUID)) {
                    return profile.getId();
                }
            }
            return null;
        }
    }

    public static void createCharacter(ServerPlayer player, String displayName) {
        createCharacter(player, displayName, false);
    }

    // This is the main method called by GUI/Network
    public static void createCharacter(ServerPlayer player, String displayName, boolean fromGui) {
        internalCreateCharacter(player, displayName, fromGui, new HashMap<>());
    }
    
    // Method for creating character with modData from input providers
    public static void createCharacter(ServerPlayer player, String displayName, boolean fromGui, Map<ResourceLocation, CompoundTag> modData) {
        internalCreateCharacter(player, displayName, fromGui, modData);
    }

    // New internal method to consolidate creation logic
    private static void internalCreateCharacter(ServerPlayer player, String displayName, boolean fromGui, Map<ResourceLocation, CompoundTag> modData) {
        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            sendError(player, Component.translatable("command.persona.error.data_not_found"), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.data_not_found");
            return;
        }

        if (characterData.getCharacters().size() >= Config.MAX_CHARACTERS_PER_PLAYER.get()) {
            sendError(player, Component.translatable("command.persona.error.max_characters", Config.MAX_CHARACTERS_PER_PLAYER.get()), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.max_characters", String.valueOf(Config.MAX_CHARACTERS_PER_PLAYER.get()));
            return;
        }

        if (!CharacterProfile.isValidName(displayName)) {
            sendError(player, Component.translatable("command.persona.error.invalid_name"), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.invalid_name");
            return;
        }

        if (GlobalCharacterRegistry.isNameTaken(displayName)) {
            sendError(player, Component.translatable("command.persona.error.name_taken", displayName), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.name_taken", displayName);
            return;
        }

        UUID newCharacterUUID = UUID.randomUUID();
        CharacterProfile newProfile = new CharacterProfile(newCharacterUUID, displayName);
        
        if (modData != null && !modData.isEmpty()) {
            Persona.LOGGER.debug("[Persona] Applying modData to new character {}: {}", displayName, modData.keySet());
            modData.forEach(newProfile::setModData);
        }

        AgingManager.handleCharacterCreationWithModData(newProfile, modData);
        AgingManager.calculateAndUpdateAge(newProfile);
        Persona.LOGGER.info("[Persona] Initial age calculation triggered for new character {}.", displayName);

        characterData.addCharacter(newProfile.getId(), newProfile);
        GlobalCharacterRegistry.registerCharacter(newProfile.getId(), player.getUUID(), newProfile.getDisplayName());

        String successKey;
        if (characterData.getActiveCharacterId() == null) {
            characterData.setActiveCharacterId(newProfile.getId());
            successKey = "command.persona.success.create_set_active";
        } else {
            successKey = "command.persona.success.create";
        }
        
        if (fromGui) {
            PersonaNetworking.sendCreationResponseToPlayer(player, true, successKey, newProfile.getDisplayName(), newProfile.getId().toString());
        } else {
            sendSuccess(player, Component.translatable(successKey, newProfile.getDisplayName(), newProfile.getId().toString()), false);
        }
    }

    public static void switchCharacter(ServerPlayer player, String nameOrUUID) {
        switchCharacter(player, nameOrUUID, false);
    }

    public static void switchCharacter(ServerPlayer player, String nameOrUUID, boolean fromGui) {
        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            sendError(player, Component.translatable("command.persona.error.data_not_found"), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.data_not_found");
            return;
        }

        UUID foundCharacterId = findCharacterId(nameOrUUID, characterData);

        if (foundCharacterId == null) {
            sendError(player, Component.translatable("command.persona.error.not_found", nameOrUUID), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.not_found", nameOrUUID);
            return;
        }

        CharacterProfile targetProfile = characterData.getCharacter(foundCharacterId);
        if (targetProfile == null) { 
            sendError(player, Component.translatable("command.persona.error.char_not_found_or_not_yours", nameOrUUID), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.char_not_found_or_not_yours", nameOrUUID);
            return;
        }

        // Check if the character is deceased
        if (targetProfile.isDeceased()) {
            sendError(player, Component.translatable("command.persona.error.char_is_deceased", targetProfile.getDisplayName()), fromGui);
            return;
        }

        UUID oldActiveCharacterId = characterData.getActiveCharacterId();

        if (foundCharacterId.equals(oldActiveCharacterId)) {
            sendError(player, Component.translatable("command.persona.error.already_active", targetProfile.getDisplayName()), fromGui);
            if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, false, "command.persona.error.already_active", targetProfile.getDisplayName());
            return;
        }

        characterData.setActiveCharacterId(foundCharacterId);

        CharacterProfile newActiveProfile = characterData.getCharacter(foundCharacterId);
        if (newActiveProfile != null) {
            AgingManager.triggerAgeUpdate(player, newActiveProfile);
            Persona.LOGGER.info("[Persona] Triggered age update for {} during switch.", newActiveProfile.getDisplayName());
        }

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new world.landfall.persona.registry.PersonaEvents.CharacterSwitchEvent(player, oldActiveCharacterId, foundCharacterId)
        );
        PersonaNetworking.sendToPlayer(characterData, player);
        sendSuccess(player, Component.translatable("command.persona.success.switch", targetProfile.getDisplayName()), fromGui);
        if (fromGui) PersonaNetworking.sendCreationResponseToPlayer(player, true, "command.persona.success.switch", targetProfile.getDisplayName());
        
        // Note: ClientPersonaSwitchedEvent should be fired on the client side when receiving server data updates
        // This happens automatically through the existing networking/GUI systems
    }

    public static void deleteCharacter(ServerPlayer player, String nameOrUUID) {
        deleteCharacter(player, nameOrUUID, false);
    }

    public static void deleteCharacter(ServerPlayer player, String nameOrUUID, boolean fromGui) {
        // Check if character deletion is enabled
        if (!Config.ENABLE_CHARACTER_DELETION.get()) {
            sendError(player, Component.translatable("command.persona.error.deletion_disabled"), fromGui);
            return;
        }

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            sendError(player, Component.translatable("command.persona.error.data_not_found"), fromGui);
            return;
        }

        UUID foundCharacterId = null;
        try {
            foundCharacterId = UUID.fromString(nameOrUUID);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try to find by display name
            for (CharacterProfile profile : characterData.getCharacters().values()) {
                if (profile.getDisplayName().equalsIgnoreCase(nameOrUUID)) {
                    foundCharacterId = profile.getId();
                    break;
                }
            }
        }

        if (foundCharacterId == null) {
            sendError(player, Component.translatable("command.persona.error.not_found", nameOrUUID), fromGui);
            return;
        }

        CharacterProfile targetProfile = characterData.getCharacter(foundCharacterId);
        if (targetProfile == null) {
            sendError(player, Component.translatable("command.persona.error.char_not_found_or_not_yours", nameOrUUID), fromGui);
            return;
        }

        // Check if trying to delete active character
        if (foundCharacterId.equals(characterData.getActiveCharacterId())) {
            sendError(player, Component.translatable("command.persona.error.delete_active"), fromGui);
            return;
        }

        String characterName = targetProfile.getDisplayName();
        characterData.removeCharacter(foundCharacterId);
        GlobalCharacterRegistry.unregisterCharacter(foundCharacterId, characterName);
        sendSuccess(player, Component.translatable("command.persona.success.delete", targetProfile.getDisplayName()), fromGui);
    }

    public static void renameCharacter(ServerPlayer player, String newName, boolean fromGui) {
        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            sendError(player, Component.translatable("command.persona.error.data_not_found"), fromGui);
            return;
        }

        UUID activeCharacterId = characterData.getActiveCharacterId();
        if (activeCharacterId == null) {
            sendError(player, Component.translatable("command.persona.error.no_active_char"), fromGui);
            return;
        }

        CharacterProfile activeProfile = characterData.getCharacter(activeCharacterId);
        if (activeProfile == null) {
            sendError(player, Component.translatable("command.persona.error.active_char_not_found"), fromGui);
            return;
        }

        if (!CharacterProfile.isValidName(newName)) {
            sendError(player, Component.translatable("command.persona.error.invalid_name"), fromGui);
            return;
        }

        if (!newName.equalsIgnoreCase(activeProfile.getDisplayName()) && GlobalCharacterRegistry.isNameTaken(newName)) {
            Optional<UUID> existingCharId = GlobalCharacterRegistry.getCharacterIdByName(newName);
            Optional<UUID> ownerUUID = existingCharId.flatMap(GlobalCharacterRegistry::getPlayerForCharacter);
            
            String errorKey = ownerUUID.isPresent() && ownerUUID.get().equals(player.getUUID()) ?
                "command.persona.error.name_taken_self" : "command.persona.error.name_taken_other";
            sendError(player, Component.translatable(errorKey, newName), fromGui);
            return;
        }

        String oldName = activeProfile.getDisplayName();
        GlobalCharacterRegistry.unregisterCharacter(activeCharacterId, oldName);
        activeProfile.setDisplayName(newName);
        GlobalCharacterRegistry.registerCharacter(activeCharacterId, player.getUUID(), newName);
        
        sendSuccess(player, Component.translatable("command.persona.success.renamed", newName), fromGui);
    }

    private static int debugCharacterData(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.data_not_found"));
            return 0;
        }

        CharacterProfile profile = findCharacterProfile(nameOrUUID, characterData, player);
        if (profile == null) {
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n§6=== ModData for %s (%s) ===§r\n", profile.getDisplayName(), profile.getId()));
        
        Map<ResourceLocation, CompoundTag> allModData = profile.getModData();
        if (allModData.isEmpty()) {
            sb.append("No mod data found.\n");
        } else {
            allModData.forEach((key, tag) -> {
                sb.append(String.format("§e%s:§r\n", key.toString()));
                sb.append(tagToString(tag, 1));
            });
        }

        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static String tagToString(Tag nbtTag, int indentLevel) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(indentLevel);
        if (nbtTag instanceof CompoundTag compoundTag) {
            if (compoundTag.isEmpty()) {
                sb.append("{}\n");
            } else {
                sb.append("{\n");
                for (String key : compoundTag.getAllKeys()) {
                    sb.append(String.format("%s§b%s:§r ", indent, key));
                    sb.append(tagToString(compoundTag.get(key), indentLevel + 1));
                }
                sb.append("  ".repeat(indentLevel -1 ) + "}\n");
            }
        } else {
            // For simple tags, just append their string representation and a newline
            sb.append(nbtTag.getAsString().replace("\n", "\\n")).append("\n"); 
        }
        return sb.toString();
    }

    private static int debugAgeInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.data_not_found"));
            return 0;
        }

        CharacterProfile profile = findCharacterProfile(nameOrUUID, characterData, player);
        if (profile == null) {
            return 0;
        }

        long currentRealTimeMillis = System.currentTimeMillis();
        CompoundTag agingData = profile.getModData(AgingManager.AGING_DATA_KEY);
        long creationTimestampMillis = agingData != null && agingData.contains(AgingManager.CREATION_TIMESTAMP_KEY) ? agingData.getLong(AgingManager.CREATION_TIMESTAMP_KEY) : -1;
        
        double configRatio = Config.TIME_PASSING_RATIO.get();
        double effectiveRealLifeDaysPerGameYear = (configRatio <= 0) ? 1.0 : configRatio;
        
        // Use the AgingManager's dynamic calculation method
        double currentCalculatedAge = AgingManager.getCalculatedAge(profile);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n§6=== Age Info for %s (Real-Time Based) ===§r\n", profile.getDisplayName()));
        sb.append(String.format("§eCurrent Real Time:§r %d ms\n", currentRealTimeMillis));
        sb.append(String.format("§eStored CREATION_TIMESTAMP_MILLIS:§r %d%s\n", creationTimestampMillis, creationTimestampMillis == -1 ? " (Not Set)" : ""));
        sb.append(String.format("§eConfig TIME_PASSING_RATIO (Real days per game year):§r %.2f\n", Config.TIME_PASSING_RATIO.get()));
        sb.append(String.format("§eEffective RealLifeDaysPerGameYearRatio:§r %.2f\n", effectiveRealLifeDaysPerGameYear));
        sb.append(String.format("§aCurrent Calculated Age:§r %.2f game years\n", currentCalculatedAge));
        
        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    // Helper to find a character profile, sending an error if not found
    private static CharacterProfile findCharacterProfile(String nameOrUUID, PlayerCharacterData characterData, ServerPlayer playerForErrorMsg) {
        UUID characterId = findCharacterId(nameOrUUID, characterData); // Assumes findCharacterId is defined and works
        if (characterId == null) {
            sendError(playerForErrorMsg, Component.translatable("command.persona.error.not_found", nameOrUUID), false);
            return null;
        }
        CharacterProfile profile = characterData.getCharacter(characterId);
        if (profile == null) {
            sendError(playerForErrorMsg, Component.translatable("command.persona.error.char_not_found_or_not_yours", nameOrUUID), false);
            return null;
        }
        return profile;
    }

    // debug set deceased cmd
    private static int debugSetDeceased(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");
        boolean isDeceased = BoolArgumentType.getBool(context, "isDeceased");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.translatable("command.persona.error.data_not_found"));
            return 0;
        }

        CharacterProfile profile = findCharacterProfile(nameOrUUID, characterData, player);
        if (profile == null) {
            return 0;
        }

        // Use the utility method to handle deceased status change with automatic switching
        boolean success = CharacterUtils.setCharacterDeceased(player, profile.getId(), isDeceased);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.translatable("command.persona.debug.success.setdeceased", profile.getDisplayName(), isDeceased), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.translatable("command.persona.error.generic_error"));
            return 0;
        }
    }
} 