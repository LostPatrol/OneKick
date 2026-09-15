# CPM standard right-leg compatibility

- Support only geometry assigned to CPM's standard `RIGHT_LEG` root on Forge 1.20.1.
- Preserve One Kick's established vanilla player-model rendering path unchanged.
- Do not add a mixin or depend on CPM internals.
- Do not infer the visual meaning or placement of geometry outside `RIGHT_LEG`.
- Build and test the branch, then refresh only the One Kick jar in the existing
  production test instance.
