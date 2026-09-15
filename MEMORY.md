# Project memory

## Current baseline

- Minecraft 1.20.1 / Forge 47.4.10 targets Java 17 through the project toolchain.
- One Kick 1.20.1-0.1.2 uses its existing `KickPlayerModel` replacement for
  third-person animation and its `RenderHandEvent` renderer for first-person kicks.
- Customizable Player Models 0.6.27 is an optional, compile-only client integration.
  First- and third-person kicks support geometry assigned to CPM's standard `RIGHT_LEG`
  root, including models that disable vanilla limb animation. The no-CPM and CPM-with-
  vanilla-model paths retain One Kick's established renderer.

## Known risk

- CPM geometry assigned outside the standard `RIGHT_LEG` root is intentionally outside
  the compatibility scope; One Kick does not infer which arbitrary custom part is a leg.
