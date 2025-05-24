# Persona

A Minecraft mod that allows players to create and manage multiple character personas with unique identities, aging systems, and lifecycle management.

## Features

### Character Management
- **Multiple Characters**: Create up to a configurable number of characters per player
- **Character Switching**: Seamlessly switch between your different personas
- **Unique Identities**: Each character has its own display name and data
- **Global Name Registry**: Character names are unique across the entire server
- **Figura Integration**: The Persona system seamlessly integrates with Figura to automatic swap avatars.

### Character Lifecycle
- **Aging System**: Characters age in real-time based on configurable ratios
- **Deceased Status**: Characters can be marked as deceased while preserving their data

### User Interface
- **GUI Management**: Intuitive graphical interface for character creation and management
- **Visual Indicators**: Active characters are listed in green, and deceased characters are displayed with a red skull (☠) icon
- **Smart Controls**: Switch buttons are automatically disabled for deceased or active characters

## Commands

### Player Commands
- `/persona create <displayName>` - Create a new character
- `/persona switch <characterNameOrUUID>` - Switch to a different character
- `/persona list` - List all your characters
- `/persona delete <characterNameOrUUID>` - Delete a character (if enabled)
- `/persona rename <newName>` - Rename your active character

### Debug Commands (OP only)
- `/persona debug registry` - View the global character registry
- `/persona debug characterdata <characterNameOrUUID>` - View character mod data
- `/persona debug ageinfo <characterNameOrUUID>` - View character aging information
- `/persona debug setdeceased <characterNameOrUUID> <true/false>` - Set character deceased status

### Admin Commands (OP only)
- `/persona admin listall <playerName>` - List all characters for a specific player
- `/persona admin forcedelete <playerName> <characterNameOrUUID>` - Force delete a character
- `/persona admin forcerename <playerName> <characterNameOrUUID> <newName>` - Force rename a character

## Configuration

The mod includes several configurable options:
- **Maximum Characters**: Set the maximum number of characters per player
- **Character Deletion**: Enable/disable character deletion
- **Time Passing Ratio**: Configure how fast characters age (real days per game year)
- **Name Validation**: Customize character name validation patterns
- **Name Lists**: Configure blacklists/whitelists for character names
