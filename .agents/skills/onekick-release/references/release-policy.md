<!-- One Kick version ownership and channel-aware release-note policy. -->
# Release version and changelog policy

## Version ownership

The user decides every version value, release tag, channel, and publication time. The release skill executes those decisions; it does not calculate or apply increments.

- Treat an exact version in the current request or active task as authoritative.
- Otherwise read each selected branch's existing full `mod_version`. Do not modify it automatically.
- Require the full version to begin with the selected Minecraft branch, for example `1.20.1-0.1.2` or `1.21.1-0.1.3`.
- Derive a tag only when the prepared versions and `publish/changelog-<tag>.md` identify it unambiguously. If branches differ or metadata disagrees, stop before build/publication and ask for the exact tag or version.
- Accept equal, different, or non-consecutive branch version suffixes exactly as supplied. Reject reuse of a published full artifact version for changed bytes; existing-version requests use recovery and verification.

Platform version numbers and display names use the complete branch version: `1.21.1-0.1.3` and `One Kick 1.21.1-0.1.3`.

## Determine the audience baseline

For each selected branch, inspect the latest actually published version on GitHub Releases, CurseForge, and Modrinth. Record each predecessor in the task report. A Git tag, another Minecraft branch, or publication on a different platform does not prove that a destination's users received it.

Compare target code with each destination's predecessor and retain only user-facing changes present on that target branch. If a destination skipped a version, include every applicable missed change in the next notes. If no predecessor exists, write a concise current feature summary rather than inventing a delta.

## Compose release notes

The GitHub Release body is forwarded to both platforms, so it must cover every selected branch and destination audience.

- Use the repository's concise player-facing changelog convention: a `# <tag>-CHANGELOG` title, only necessary `# ADD`, `# CHANGE`, and `# FIX` sections, and `+` bullets.
- Each new feature or bug fix occupies one top-level bullet. Describe what players receive, not internal implementation details.
- For a single branch with a shared baseline, one concise changelog is sufficient.
- For multiple branches with different histories or changes, use explicit `## Minecraft 1.20.1` and `## Minecraft 1.21.1` sections and describe each branch's cumulative audience delta.
- Do not imply that an omitted branch was released. Repeating an item for a destination with a newer predecessor is acceptable when needed to cover a platform that skipped it.

Before publication, create a coverage matrix for every `(Minecraft branch, destination)` pair, record its predecessor, and confirm all applicable player-visible changes are included. The published body must match the corresponding `publish/changelog-<tag>.md` exactly.
