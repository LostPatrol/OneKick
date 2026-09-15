---
name: onekick-release
description: "Complete One Kick releases end to end: build selected Minecraft branches, publish verified GitHub Release assets, synchronize CurseForge and Modrinth, and verify every requested destination."
---

<!-- One-invocation release orchestration; Codex owns GitHub publication and platform follow-through. -->
# One Kick release

## Invocation contract

A request to run this skill for a release authorizes the complete chain: prepare release metadata, build, commit and push release-relevant changes on existing branches, create/upload/publish the GitHub Release, trigger platform synchronization, and verify the results. Perform those steps yourself; do not hand the user routine draft creation, asset upload, publication, or workflow buttons. Do not stop at a draft or a dispatched workflow and call it complete.

A request to edit, explain, or dry-run this skill is not a request to publish a real version. Honor narrower scope, including GitHub-only or preparation-only requests.

Read repository instructions and local `MEMORY.md` when it exists. Read [references/release-policy.md](references/release-policy.md) whenever preparing or reviewing a release. For GitHub operations, also read [references/github-release.md](references/github-release.md).

## Resolve the release

- Before publishing, inspect recent releases on every requested destination for the same Minecraft branch. Match their display-name, version-number, channel, and changelog conventions; record the reference version in the task report. The established platform name is `One Kick <minecraft>-<mod-version>`, with loaders kept in compatibility metadata.
- Use the requested branch set; otherwise use the active task's established branch, falling back to the current Minecraft branch. Never silently add the other branch. `1.20.1` uses Forge / Java 17; `1.21.1` uses NeoForge / Java 21.
- The user owns version changes, tag selection, prerelease/stable status, and release timing. Never invent or increment a version. Each branch stores a full `mod_version` such as `1.21.1-0.1.3`; confirm that its Minecraft prefix matches the branch. Use an already prepared exact version and matching `publish/changelog-<tag>.md` only when they make the requested tag unambiguous. Otherwise ask for the missing exact value before changing version metadata or publishing.
- Branch versions may differ. A GitHub tag points to one selected commit, while its attachments can contain builds from other selected branches. Never move an existing tag or reuse an already published full artifact version for changed bytes.
- Build the GitHub Release body and platform release notes according to `references/release-policy.md`. Base them on actual branch changes and each destination's last published version. Never implement TODO items during release preparation.

## Build and publish GitHub automatically

1. Inspect local changes and remote heads; preserve unrelated work. Update only release-relevant metadata and player-facing changelogs. Use existing branches and worktrees; do not invent branches or merge unrelated implementations.
2. Reuse a successful, unexpired Build artifact only when its source SHA matches the intended release commit. Otherwise build with the Gradle wrapper or dispatch `build.yml` through GitHub API on each selected branch. Verify the run's `head_sha` equals the intended commit; a branch race requires re-evaluation.
3. Select one production JAR per branch. Validate its file name, embedded `onekick` mod ID, full version, loader metadata, and SHA-256. Reuse the repository release-plan and JAR validation logic in a staging directory under ignored `agent/codex`; record branch, commit, build run, file name, and hash.
4. Check the tag and Release before creating anything. Resume only a matching draft. An existing published Release uses verification/recovery, not recreation or asset replacement.
5. Create the GitHub draft through API with an explicit target commit SHA and exact changelog body. Upload every intended JAR, re-fetch and verify the complete asset set, sizes and hashes, then publish the draft yourself.

## Follow through to CurseForge and Modrinth

- The `release.published` event starts `publish-platforms.yml`; the workflow and scripts must exist at the tagged commit. Keep release facilities on both Minecraft branches because manual dispatch is resolved from the default branch.
- Before a real release, verify repository variables `CURSEFORGE_PROJECT_ID=1665148` and `MODRINTH_PROJECT_ID=e0eDTtot`, plus secret names `CURSEFORGE_TOKEN` and `MODRINTH_TOKEN`. Never read, print, or request the secret values through logs. If a secret is missing, stop before publishing the GitHub Release unless the user explicitly narrowed the release to GitHub only.
- Match the publishing run to the tag and publication time. Wait for each requested branch/platform job, inspect failures and output URLs, and confirm successful jobs produced platform version/file identifiers. A skipped job is not success.
- Releases made with a workflow's built-in `GITHUB_TOKEN` may not trigger another release workflow. Prefer the available user-authorized GitHub API credential. If the event is suppressed or a historical tag lacks the workflow, inspect existing runs and platform files, then dispatch only confirmed missing destinations.
- On partial failure, inspect remote platform versions before retrying. Retry a confirmed missing upload only for the failed branch/platform, at most once per skill invocation after correcting a recoverable cause. If acceptance is ambiguous after a timeout, stop retrying that destination and report the ambiguity.
- Report GitHub and each platform's observed result with links. Distinguish API acceptance, review/pending state, and public availability. Maintain the ignored local task report and project memory with versions, source commits, artifact hashes, workflow URLs, and unresolved failures.

## Maintenance validation

For skill edits, run the skill-creator validator using the project's Python environment. For release-selection changes, run `node --test .github/scripts/release-plan.test.cjs`; for workflow edits, run actionlint. Use dry runs for configuration checks. Do not create a live Release merely to test the skill.
