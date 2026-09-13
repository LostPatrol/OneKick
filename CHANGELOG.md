# Changelog

This document records notable changes to One Kick releases.

## [0.1.3] - 2026-09-13

Minecraft 1.21.1 only. The Minecraft 1.20.1 build was not updated in this
release.

### Fixed

- Fixed compatibility with Player Animation Library and The Awakening so the
  player's head or body no longer appears inside the first-person camera during
  ability animations.
- Preserved PAL's vanilla `PlayerModel` animation hooks when PAL is installed.
- Kept One Kick's third-person right-leg animation through an isolated PAL
  animation layer.
- One Kick's first-person kick leg is intentionally hidden while another PAL
  animation controls first person; it returns after that animation ends.

### Compatibility

- Tested with Minecraft 1.21.1, NeoForge 21.1.250, Player Animation Library
  1.1.6, The Awakening 1.12.3, and Curios 9.5.0.
- Behavior without Player Animation Library remains unchanged.
- No new required dependency was added.

[0.1.3]: https://github.com/LostPatrol/OneKick/releases/tag/0.1.3
