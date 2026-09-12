# PAL compatibility implementation report

## Scope

- Branch/version line: Minecraft 1.21.1 only.
- Release version: `1.21.1-0.1.3`.
- Compatibility target: Player Animation Library 1.1.6 and The Awakening 1.12.3.
- Accepted tradeoff: One Kick's first-person kick leg is temporarily hidden while an
  active PAL animation owns the first-person model pass.

## Changes

- Added PAL 1.1.6 as a compile-only dependency from its public Maven repository.
- Added a PAL-loaded guard before One Kick replaces player renderer models. This leaves
  PAL's `PlayerModel.setupAnim` injection reachable and fixes the head/body visibility bug.
- Registered an isolated priority-1500 PAL animation layer that writes only the right-leg
  rotation while One Kick's charge/kick pose is active.
- Kept PAL's default first-person mode (`NONE`) for the One Kick layer. Awakening therefore
  remains responsible for first-person body-part visibility during its abilities.
- Added no Mixin and no required/embedded PAL dependency.

## Isolation guarantees

- The PAL bridge is called only after `ModList` confirms `player_animation_library` is loaded.
- All PAL API references are confined to `client.compat.PalCompatibility`.
- When PAL is absent, the existing `KickPlayerModel` replacement and `RenderHandEvent`
  behavior execute unchanged.
- PAL is absent from `runtimeClasspath` and is not bundled into the output JAR.

## Verification

- `gradlew clean test`: passed.
- `gradlew build`: passed.
- No-PAL client startup: reached completed GUI/resource loading with only Minecraft,
  NeoForge, and One Kick present.
- PAL client startup: reached completed GUI/resource loading; PAL's `PlayerModel` Mixins
  applied successfully and client setup completed.
- Full client startup with One Kick + PAL 1.1.6 + The Awakening 1.12.3 + Curios 9.5.0:
  reached completed GUI/resource loading; both PAL and Awakening `PlayerModel` Mixins
  applied, sided setup completed, and Awakening animation resources loaded.
- `git diff --check`: passed (Git reported only expected LF-to-CRLF checkout notices).

## Remaining limitation

- Automated startup verifies the conflicting render pipeline and registrations, but does
  not provide a pixel-level in-world ability animation assertion. The behavioral result
  follows directly from retaining PAL's vanilla model and from the One Kick layer keeping
  first-person mode disabled.
