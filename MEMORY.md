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
  When CPM is present, its supported rendering API supplies the custom right-leg mesh
  and texture for One Kick's first-person kick; without CPM, the original vanilla-skin
  renderer executes unchanged.

## Known risk

- The PAL bridge is compiled against PAL 1.1.6 for Minecraft 1.21.1. A future PAL
  release that breaks its factory, animation, or bone APIs will require a bridge update;
  installations without PAL remain isolated from this risk.
- The CPM bridge is compiled against API 0.6.27 and intentionally renders only the
  right-leg root in `AnimationMode.HAND`. Unusual models that attach kick-visible
  geometry outside that root remain a manual compatibility edge case.
