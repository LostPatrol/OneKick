# One Kick

[简体中文](README_zh.md)

One Kick is a Minecraft NeoForge mod built around one simple idea: kick things.

Press **R** to kick in the direction you are looking. The key can be changed in Minecraft's Controls menu. Your movement speed and boot quality affect kick strength, while a target's maximum health affects how far it is launched. A launched creature takes kinetic damage if it hits a wall.

Boot enchantments expand the move:

- **Reaction** and **Aerodynamics** turn kicks into movement tools.
- **Charge** adds a hold-to-charge meter and trades food energy for power, consuming saturation before hunger just like normal movement. Its strength follows a quadratic curve, keeping brief charges gentle while high charge ramps up rapidly; the very rare **Overcharge** enchantment multiplies its maximum charge.
- **Disintegration** and **Unstable Collision** break terrain or create explosions when a launched creature collides.
- **Kinetic Overload** greatly raises speed and damage.
- **Conservation of Angular Momentum** makes creatures kicked upward spin until they land.
- Vanilla **Silk Touch** can be enchanted onto boots and preserves drops from kick-driven block destruction, unless the server's Kinetic Overload drop protection is active.

The mod also includes a dedicated advancement tab with 13 milestones, ranging from a first kick to maximum charge, Mach-ring launches, large-scale destruction, and the fully enchanted **One Kick!**

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.235 or later in the 21.1 series
- Java 21

## Status

The core gameplay is available in development builds. There is currently no published release.

Charge stops at its enchantment-defined maximum, while **Overcharge I–II** multiplies that maximum by 2–3. Releasing a fully charged Charge V kick with Overcharge II costs about 35 food energy by itself. Charging consumes food energy separately, so even full saturation and hunger may not be enough to reach the maximum naturally. Launched creatures follow a server-controlled ballistic arc: stronger kicks keep the curve flatter and travel farther. The kick itself deals no damage; kinetic damage starts only when the creature later collides, while Disintegration can add damage during its forced traversal. Unstable Collision deals that kinetic impact damage uniformly throughout its blast area and does not knock entities back. If movement veers too far from the initial direction, both the trail and follow-up impact effects stop. Disintegration limits flying block debris to 64 entities per impact, but the current tuning build still leaves kick and terrain-destruction upper bounds uncapped, so back up test worlds before experimenting with extreme values.

## Server drop protection

Kinetic Overload block-drop protection is enabled by default. When enabled, any kick-driven terrain destruction from boots with **Kinetic Overload** suppresses block-item drops and debris landing drops, regardless of impact speed. Container contents still drop normally.

Operators with permission level 2 can query or change it without restarting:

```text
/onekick kinetic_overload_drop_protection
/onekick kinetic_overload_drop_protection true|false
```

The setting is persisted in the world's `serverconfig/onekick-server.toml` as `performance.suppressKineticOverloadBlockDrops`.

## License

One Kick is available under the [MIT License](LICENSE).
