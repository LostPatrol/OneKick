# CPM standard right-leg compatibility

- Support only geometry assigned to CPM's standard `RIGHT_LEG` root on the 1.20.1 and
  1.21.1 branches.
- Preserve One Kick's established vanilla player-model rendering path unchanged.
- Do not add a mixin or depend on CPM internals.
- Do not infer the visual meaning or placement of geometry outside `RIGHT_LEG`.
- Build and test both branches, then refresh only the One Kick jars in the existing production test instances.
