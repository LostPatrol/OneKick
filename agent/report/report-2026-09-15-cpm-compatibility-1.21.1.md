# CPM compatibility implementation report (1.21.1)

## Scope

- Branch: `1.21.1` (Minecraft 1.21.1 / NeoForge).
- Compatibility target: Customizable Player Models API 0.6.27 and runtime 0.6.27a.
- Goal: render the local CPM model's right leg during One Kick's first-person animation.

## Changes

- Added CPM's API as an optional `compileOnly` dependency from its official Maven
  repository; no CPM code or runtime is bundled into One Kick.
- Registered a small client-only plugin through CPM's supported Forge/NeoForge IMC API.
- Added an isolated `CpmCompatibility` renderer that binds the local player's profile,
  applies One Kick's existing right-leg pose, and renders only CPM's right-leg root.
- Kept the existing first-person placement, scale, animation timing, hand cancellation,
  lighting, and buffer lifecycle.
- Added no Mixin.

## Isolation and impact

- `ClientEvents` and `FirstPersonKickRenderer` access the compatibility bridge only after
  `ModList` confirms that mod id `cpm` is loaded.
- If CPM is absent, or its renderer is not initialized/has no model texture, execution
  falls through to the pre-existing vanilla-skin leg renderer.
- Third-person model replacement, PAL integration, networking, kick mechanics, particles,
  sound, and server code are unchanged.

## Verification

- `gradlew compileJava --no-daemon`: passed.
- `gradlew test build --no-daemon`: passed.
- `git diff --check`: passed; only existing Windows LF-to-CRLF checkout notices appeared.
- Output JAR inspection confirmed the bridge is present and no `com/tom/cpm` classes are
  embedded.

## Manual verification still required

- Pixel-level behavior with a visibly customized CPM right leg must be checked in the
  production HMCL instance. This is necessary because automated compilation cannot
  assert the rendered model geometry.
