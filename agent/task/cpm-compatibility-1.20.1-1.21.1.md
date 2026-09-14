# CPM compatibility repair

- Fix first-person kick rendering for Customizable Player Models on branches
  `1.20.1` and `1.21.1`.
- Keep the no-CPM rendering path unchanged.
- Use CPM's supported API; do not add a One Kick mixin.
- Minimize the compatibility bridge and isolate optional CPM classes.
- Build and verify both branches, then prepare the user's two HMCL instances for
  manual production testing without removing existing mods.
