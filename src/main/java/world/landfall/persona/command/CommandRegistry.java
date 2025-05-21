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
import world.landfall.persona.registry.PersonaEvents;
import world.landfall.persona.registry.PersonaNetworking;

import java.util.UUID;

public class CommandRegistry {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Persona.MODID)
            .then(Commands.literal("create")
                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                    .executes(CommandRegistry::createCharacter)))
            .then(Commands.literal("switch")
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.greedyString())
                    .executes(CommandRegistry::switchCharacter)))
            .then(Commands.literal("list")
                .executes(CommandRegistry::listCharacters))
            .then(Commands.literal("delete")
                .then(Commands.argument("characterNameOrUUID", StringArgumentType.greedyString())
                    .executes(CommandRegistry::deleteCharacter)))
            .then(Commands.literal("rename")
                .then(Commands.argument("newName", StringArgumentType.greedyString())
                    .executes(CommandRegistry::renameCharacter)))
        );
    }

    private static int createCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String displayName = StringArgumentType.getString(context, "displayName");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.literal("Error: Character data not found for player."));
            return 0;
        }

        // Check character limit
        if (characterData.getCharacters().size() >= Config.MAX_CHARACTERS_PER_PLAYER.get()) {
            context.getSource().sendFailure(Component.literal("You have reached the maximum number of characters (" + 
                Config.MAX_CHARACTERS_PER_PLAYER.get() + ")."));
            return 0;
        }

        // Check if a character with this display name already exists for the player
        boolean nameExists = characterData.getCharacters().values().stream()
            .anyMatch(profile -> profile.getDisplayName().equalsIgnoreCase(displayName));
        if (nameExists) {
            context.getSource().sendFailure(Component.literal("A character with the name '" + displayName + "' already exists."));
            return 0;
        }

        UUID newCharacterId = UUID.randomUUID();
        CharacterProfile newProfile = new CharacterProfile(newCharacterId, displayName);
        
        characterData.addCharacter(newCharacterId, newProfile);
        GlobalCharacterRegistry.registerCharacter(newCharacterId, player.getUUID());

        // If it's the first character, set it as active
        if (characterData.getActiveCharacterId() == null) {
            characterData.setActiveCharacterId(newCharacterId);
            context.getSource().sendSuccess(() -> Component.literal("Character '" + displayName + "' created and set as active. UUID: " + newCharacterId), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Character '" + displayName + "' created. UUID: " + newCharacterId), true);
        }
        
        return 1;
    }

    private static int switchCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.literal("Error: Character data not found for player."));
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
            context.getSource().sendFailure(Component.literal("Character '" + nameOrUUID + "' not found."));
            return 0;
        }

        final UUID targetCharacterId = foundCharacterId; // Make it effectively final for lambda
        CharacterProfile targetProfile = characterData.getCharacter(targetCharacterId);

        if (targetProfile == null) { // Shouldnt happen if foundCharacterId was set from existing characters
            context.getSource().sendFailure(Component.literal("Character '" + nameOrUUID + "' not found or does not belong to you."));
            return 0;
        }

        if (targetCharacterId.equals(characterData.getActiveCharacterId())){
            context.getSource().sendFailure(Component.literal("Character '" + targetProfile.getDisplayName() + "' is already active."));
            return 0;
        }

        characterData.setActiveCharacterId(targetCharacterId);
        context.getSource().sendSuccess(() -> Component.literal("Switched to character: " + targetProfile.getDisplayName()), true);
        return 1;
    }

    private static int listCharacters(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);

        if (characterData == null || characterData.getCharacters().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have no characters."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Your characters:\n");
        characterData.getCharacters().forEach((uuid, profile) -> {
            sb.append("- ").append(profile.getDisplayName()).append(" (UUID: ").append(uuid).append(")");
            if (uuid.equals(characterData.getActiveCharacterId())) {
                sb.append(" [ACTIVE]");
            }
            sb.append("\n");
        });

        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int deleteCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nameOrUUID = StringArgumentType.getString(context, "characterNameOrUUID");

        // Check if character deletion is enabled
        if (!Config.ENABLE_CHARACTER_DELETION.get()) {
            context.getSource().sendFailure(Component.literal("Character deletion is disabled on this server."));
            return 0;
        }

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.literal("Error: Character data not found for player."));
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
            context.getSource().sendFailure(Component.literal("Character '" + nameOrUUID + "' not found."));
            return 0;
        }

        CharacterProfile targetProfile = characterData.getCharacter(foundCharacterId);
        if (targetProfile == null) {
            context.getSource().sendFailure(Component.literal("Character '" + nameOrUUID + "' not found or does not belong to you."));
            return 0;
        }

        // Don't allow deleting the active character
        if (foundCharacterId.equals(characterData.getActiveCharacterId())) {
            context.getSource().sendFailure(Component.literal("Cannot delete your active character. Switch to a different character first."));
            return 0;
        }

        // Fire the delete event
        PersonaEvents.CharacterDeleteEvent deleteEvent = new PersonaEvents.CharacterDeleteEvent(player, foundCharacterId);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(deleteEvent);
        if (deleteEvent.isCanceled()) {
            context.getSource().sendFailure(Component.literal("Character deletion was cancelled."));
            return 0;
        }

        // Remove the character
        characterData.removeCharacter(foundCharacterId);
        GlobalCharacterRegistry.unregisterCharacter(foundCharacterId);
        context.getSource().sendSuccess(() -> Component.literal("Character '" + targetProfile.getDisplayName() + "' has been deleted."), true);
        return 1;
    }

    private static int renameCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String newName = StringArgumentType.getString(context, "newName");

        PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (characterData == null) {
            context.getSource().sendFailure(Component.literal("Error: Character data not found for player."));
            return 0;
        }

        UUID activeCharacterId = characterData.getActiveCharacterId();
        if (activeCharacterId == null) {
            context.getSource().sendFailure(Component.literal("You don't have an active character."));
            return 0;
        }

        CharacterProfile activeProfile = characterData.getCharacter(activeCharacterId);
        if (activeProfile == null) {
            context.getSource().sendFailure(Component.literal("Error: Active character not found."));
            return 0;
        }

        // Check if the new name is already taken by another character
        boolean nameExists = characterData.getCharacters().values().stream()
            .anyMatch(profile -> profile.getDisplayName().equalsIgnoreCase(newName) && 
                              !profile.getId().equals(activeCharacterId));
        if (nameExists) {
            context.getSource().sendFailure(Component.literal("A character with the name '" + newName + "' already exists."));
            return 0;
        }

        String oldName = activeProfile.getDisplayName();
        activeProfile.setDisplayName(newName);

        // Sync the change to other players
        PersonaNetworking.sendToServer(characterData);

        context.getSource().sendSuccess(() -> Component.literal("Renamed character from '" + oldName + "' to '" + newName + "'"), true);
        return 1;
    }
} 