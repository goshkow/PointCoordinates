# PointCoordinates

[Русская версия](README_RU.md)

PointCoordinates is a marker plugin for Minecraft servers that helps players save important places, keep coordinates organized, and get back to them quickly.

Instead of memorizing coordinates or searching through old chat messages, players can create named markers, share public locations, open marker info, and teleport when the server allows it. For admins, the plugin stays flexible and easy to control without turning into a mess.

## Why use it

- Players can save useful places without manually storing coordinates
- Public markers make sharing locations simple
- Lists, search, and marker info make saved places easy to manage
- Disabled features do not clutter the player experience
- Admins can turn features on or off and control access through permissions

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
- Teleportation can be restricted by marker type
- Command aliases can be toggled one by one
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

### Command branches

- `/pcords add <name>`
  Creates a personal marker at your current location.

- `/pcords open <name>`
  Creates a public marker at your current location, or turns your existing marker into a public one.

- `/pcords list`
  Opens marker lists. The exact modes shown depend on config and permissions.

- `/pcords search`
  Opens the available search modes. Depending on your permissions and config, you can search by name or by public tags.

- `/pcords info <name>`
  Shows information about a marker, including coordinates, world, type, and extra details when enabled.

- `/pcords tp <name>`
  Teleports to a marker if teleportation is allowed for that marker and for that player.

- `/pcords edit`
  Opens the available edit actions. Depending on permissions, this can include renaming, moving, or editing public tags.

- `/pcords remove <name>`
  Removes a marker.

- `/pcords reload`
  Reloads the plugin config, language files, aliases, and other runtime settings.

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

### Core access

- `cords.use`
  Allows using the main plugin command.

- `cords.reload`
  Allows reloading the plugin.

### Marker creation

- `cords.add`
  Allows creating personal markers.

- `cords.open`
  Allows creating public markers or turning a marker into a public one.

### Teleportation

- `cords.teleport.personal`
  Allows teleporting to personal markers.

- `cords.teleport.public`
  Allows teleporting to public markers.

- `cords.teleport.owned`
  Allows teleporting to your own markers even if the matching personal/public teleport permission is not granted.

- `cords.teleport.bypass_disabled`
  Allows teleporting even when teleportation for that marker type is disabled in config.

- `cords.teleport.bypass_cooldown`
  Allows bypassing teleport cooldowns.

### Lists and info

- `cords.list`
  Allows using the list command.

- `cords.list.private`
  Allows viewing private marker lists.

- `cords.list.owned`
  Allows viewing lists of markers owned by the player.

- `cords.list.open`
  Allows viewing public marker lists.

- `cords.list.all`
  Allows viewing combined lists of all available markers.

- `cords.info`
  Allows viewing marker information.

### Search

- `cords.search.name`
  Allows searching markers by name.

- `cords.search.tag`
  Allows searching public markers by tags.

### Editing

- `cords.edit.name`
  Allows renaming your own markers.

- `cords.edit.name.others`
  Allows renaming markers owned by other players.

- `cords.edit.move`
  Allows moving your own markers to your current position.

- `cords.edit.move.others`
  Allows moving markers owned by other players.

- `cords.edit.tag`
  Allows editing tags on your own public markers.

- `cords.edit.tag.others`
  Allows editing tags on other players' public markers.

### Removal

- `cords.remove`
  Allows removing your own markers.

- `cords.remove.others`
  Allows removing markers owned by other players.

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
