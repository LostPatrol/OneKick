# Project memory

## Current baseline

- Minecraft 1.21.1 / NeoForge 21.1.235 targets Java 21.
- One Kick 1.21.1-0.1.3 supports Player Animation Library 1.1.6 as an optional,
  compile-only client integration.
- Without PAL, player rendering still uses `KickPlayerModel` and the existing
  `RenderHandEvent` first-person leg renderer.
- With PAL, One Kick preserves the vanilla `PlayerModel` so PAL's animation and
  first-person visibility injections remain active. A priority-1500 PAL layer applies
  One Kick's right-leg pose in third person only.
- During another PAL animation's first-person model pass, One Kick's first-person leg
  is intentionally not rendered. This prevents the player's head/body from appearing
  inside the camera and is the accepted compatibility tradeoff.
- Customizable Player Models 0.6.27 is an optional, compile-only client integration.
  First- and third-person kicks support geometry assigned to CPM's standard `RIGHT_LEG`
  root, including models that disable vanilla limb animation. The no-CPM and CPM-with-
  vanilla-model paths retain One Kick's established renderer.

## Known risk

- The PAL bridge is compiled against PAL 1.1.6 for Minecraft 1.21.1. A future PAL
  release that breaks its factory, animation, or bone APIs will require a bridge update;
  installations without PAL remain isolated from this risk.
- CPM geometry assigned outside the standard `RIGHT_LEG` root is intentionally outside
  the compatibility scope; One Kick does not infer which arbitrary custom part is a leg.
