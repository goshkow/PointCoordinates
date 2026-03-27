# PointCoordinates

[Русская версия](README_RU.md)

PointCoordinates is a marker plugin for Minecraft servers that helps players save important places, keep coordinates organized, and get back to them quickly.

Instead of memorizing coordinates or scrolling through old chat messages, players can create clear named markers, share public locations, view useful marker info, and teleport when the server allows it. It keeps location management simple for players and stays flexible for admins.

## Why servers use it

- Players can save important places without writing coordinates down manually
- Public markers make it easy to share useful locations with others
- Lists, search, and marker info keep everything easy to find
- The plugin stays clean for players because it only shows features they can actually use
- Admins can enable only the parts they want and disable everything else

## Player features

- Personal markers
- Public markers
- Marker info in chat
- Search by name and public tags
- Rename, move, and remove actions
- Teleport to allowed markers
- Paged marker lists
- Confirmation for important actions

## Admin-friendly setup

- Personal and public markers can be configured separately
- Teleportation can be restricted per marker type
- Aliases can be turned on or off one by one
- Help, usage, and tab-complete adapt to config and permissions
- Language files update without wiping custom values
- Marker storage backups are created automatically
- Optional integrations are available for common server plugins

## Commands

Main command:

- `/pcords`

Optional aliases:

- `/cords`
- `/pc`
- `/pt`

Each alias can be enabled or disabled separately in `config.yml`.

Main command branches:

- `/pcords add <name>` create a personal marker
- `/pcords open <name>` create a public marker or make your marker public
- `/pcords list` open a marker list
- `/pcords search` open search options
- `/pcords info <name>` show marker information
- `/pcords tp <name>` teleport to a marker
- `/pcords edit` open edit options
- `/pcords remove <name>` remove a marker
- `/pcords reload` reload the plugin

If a feature is disabled or a player does not have permission for it, it will not appear in help, usage, or tab-complete.

## Configuration

PointCoordinates can be kept simple or expanded into a more feature-rich setup.

You can configure:

- personal markers
- public markers
- teleport access and cooldowns
- sounds and confirmation behavior
- search and public tags
- command aliases
- localized command labels in help and usage
- optional integrations

Main files:

- `config.yml` - plugin settings
- `cords.yml` - saved markers
- `languages/*.yml` - translations

## Localization

Built-in languages:

- English
- Russian
- Spanish
- French
- German
- Brazilian Portuguese
- Simplified Chinese

Language loading works like this:

- first, the plugin uses the language file from the plugin folder
- if some keys are missing, it fills them from the bundled file inside the jar
- English is used only as the last fallback

Command words in help, usage, and tab-complete can also be localized if that option is enabled in config.

## Permissions

Permissions are split into clear actions, so server admins can decide exactly what players are allowed to do.

Examples:

- `cords.add`
- `cords.open`
- `cords.info`
- `cords.search.name`
- `cords.search.tag`
- `cords.edit.name`
- `cords.edit.move`
- `cords.edit.tag`
- `cords.remove`
- `cords.teleport.personal`
- `cords.teleport.public`
- `cords.teleport.owned`
- `cords.reload`

There are also separate `*.others` permissions for actions on markers owned by other players.

## Integrations

Optional support is available for:

- Vault
- CombatLogX
- Dynmap
- BlueMap

Everything stays disabled until you enable it in config.

## Notes

- Server plugin name: `PointCoordinates`
- Main command: `/pcords`
- Aliases can be toggled individually
- Players only see the functionality that is enabled and available to them
