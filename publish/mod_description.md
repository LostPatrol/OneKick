# One Kick

One Kick is a Minecraft Forge/NeoForge mod built around kicking. It adds a set of powerful, interlocking enchantments, flashy visuals, and a dedicated advancement tab.

Used well, one kick can send a pesky Warden flying, or shear the top off a mountain.

## Compatibility

| Mod version | Minecraft | Loader | Java |
| --- | --- | --- | --- |
| 1.20.1-0.1.0 | 1.20.1 | Forge 47.4.10 | 17 |
| 1.21.1-0.1.0 | 1.21.1 | NeoForge 21.1.235 | 21 |

- Side: client and server. Install the mod on both sides for multiplayer.
- Dependencies: no additional mods are required.
- Mod ID: `onekick`
- Issues: [GitHub issue tracker](https://github.com/LostPatrol/OneKick/issues)

## Gameplay

### Kick

1. Aim at a creature, a block, or the air
2. Press R (default). The key can be changed in Controls under **One Kick**.

### Mechanics

Kick strength and launch speed are the core of this mod. They decide how hard a kick hits.

Kick strength is computed from boot quality, player movement speed, charge, and special enchantments. Launch speed depends on kick speed, the target's maximum health, and knockback resistance.

## Enchantments

This mod adds a set of enchantments that can only go on boots. Vanilla Silk Touch can now also be enchanted onto boots. If the enchantment level is `N`, the effects are:

| Enchantment | ID | Rarity | Max | Effect |
| --- | --- | --- | --- | --- |
| Reaction | `onekick:reaction` | Common | 1 | Recoil proportional to kick power when kicking a creature or a block |
| Aerodynamics | `onekick:aerodynamics` | Uncommon | 3 | Kick the air `N` times to produce recoil |
| Charge | `onekick:charge` | Uncommon | 5 | Allows charged kicks; hold the key and spend hunger to charge |
| Overcharge | `onekick:overcharge` | Very rare | 2 | Multiplies the charge cap by `N` |
| Conservation of Angular Momentum | `onekick:angular_momentum` | Uncommon | 1 | Makes launched creatures spin |
| Disintegration | `onekick:disintegration` | Uncommon | 1 | Breaks blocks when a kicked creature hits a wall |
| Unstable Collision | `onekick:unstable_collision` | Very rare | 3 | Explodes on impact (does not break blocks on its own) |
| Kinetic Overload | `onekick:kinetic_overload` | Very rare | 3 | Greatly increases kick speed, impact damage, and destruction |
| Silk Touch (vanilla) | `minecraft:silk_touch` | — | 1 | Kick-broken blocks drop 100% of the time and are silk-touched |

- Besides the hunger spent while charging, a charged kick spends extra hunger on release. If hunger is not enough at that moment, kick power is scaled down in proportion.
- Some combinations drain hunger extremely fast. You may need to eat during the charge to reach full power.
- Kinetic Overload has very high numbers. Any kick with it can become dangerous and destructive; be careful around your base.

## Enchantment synergies

All of the enchantments above can be combined in any way and still do their own jobs. Higher levels hit harder. None of them conflict, and they amplify each other. There are also these special synergies:

| Combination | Effect |
| --- | --- |
| Disintegration + Kinetic Overload | Impact causes very long, large-scale block destruction |
| Disintegration + Unstable Collision | The impact explosion breaks blocks using Unstable Collision's logic; Disintegration's own destruction is disabled |
| Disintegration + Unstable Collision + Kinetic Overload | Impact causes very long, fairly wide large-scale block destruction and explosions |

## Configuration and commands

Commands require permission level 2.

| Command | Effect |
| --- | --- |
| `/onekick kinetic_overload_drop_protection true` | Enable (destruction from Kinetic Overload kicks no longer drops items) |
| `/onekick kinetic_overload_drop_protection false` | Disable (drops follow this mod's rules) |

Commands immediately sync `performance.suppressKineticOverloadBlockDrops` in the server config file `serverconfig/onekick-server.toml`.

Data packs and other mods can change these tags to configure this mod's effects:

| Tag | Default contents | Used for |
| --- | --- | --- |
| `onekick:entity_types/kick_immune` | `minecraft:ender_dragon` | Cannot be launched |
| `onekick:entity_types/flying` | allay, bat, bee, blaze, ender dragon, ghast, parrot, phantom, vex, wither | Cannot be spun by Conservation of Angular Momentum; vanilla `FlyingMob` / `FlyingAnimal` are also excluded |
| `onekick:blocks/disintegration_immune` | `#minecraft:wither_immune` | Cannot be broken by kick destruction |
| `onekick:blocks/disintegration_direct` | glass, glass pane, `#minecraft:leaves`, `#minecraft:wool` | Shatter in place; no flying debris |

## Notes

- Enchantment rarities have not been tuned yet, so this mod's enchantments may turn up too often or too rarely. Please report issues if you run into that.
- This is an entertainment mod. Many values grow on a quadratic curve and are not balanced for vanilla survival; numbers can get out of hand.

> **Warning:** Kick strength and launch speed currently have no cap. This mod includes performance work for large-scale block destruction from Kinetic Overload, but behavior is not guaranteed on every setup—especially if enchantments are obtained above this mod's intended maximum levels by unusual means. Use caution.

## Credits

The core idea of this mod was inspired by part of https://www.bilibili.com/video/BV1m19YBuEjp/. Special thanks to the original authors: [老丹Daniel](https://space.bilibili.com/26947053) and [空栈不是空伐](https://space.bilibili.com/291397844). The features here are original implementations; only the concept was referenced.

## License

One Kick is available under the [MIT License](https://github.com/LostPatrol/OneKick/blob/1.20.1/LICENSE).
