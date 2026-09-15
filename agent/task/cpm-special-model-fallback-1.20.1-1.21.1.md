# CPM special-model fallback

- Remove the unreliable CPM-specific leg renderer from the 1.20.1 and 1.21.1 branches.
- Preserve One Kick's established vanilla player-model rendering path unchanged.
- Do not add a mixin or depend on CPM internals.
- Accept that CPM models which replace or disable vanilla limb animation are unsupported.
- Build and test both branches, then refresh only the One Kick jars in the existing production test instances.
