<!-- GitHub API operations used by the One Kick release skill. -->
# GitHub execution reference

Repository: `LostPatrol/OneKick`. Use PowerShell, Git, and GitHub REST APIs; `gh` is not installed. Keep temporary manifests, downloaded artifacts, request bodies, and logs in ignored `agent/codex`. Never save credentials there.

## Authentication and preflight

Use an available authenticated GitHub connector when it supports the operation, otherwise use the existing Git credential helper. Capture `git credential fill` output in memory using `protocol=https`, `host=github.com`, and a terminating blank line. Parse the password without printing the credential response. Send authentication only to GitHub API/upload endpoints and never forward it to redirected signed artifact URLs.

Use JSON request bodies serialized with `ConvertTo-Json`, `Accept: application/vnd.github+json`, and `X-GitHub-Api-Version: 2022-11-28`. Upload binaries with `Invoke-WebRequest -InFile`, not JSON or multipart wrapping. Do not interpolate release text into executable command strings.

- Check repository permissions and selected remote branch SHAs before writing.
- List repository Actions secrets and variables to verify names and project IDs. Secret values are not retrievable, and presence does not prove platform authorization.
- Existing GitHub credentials are separate from platform tokens. Report actual authorization failures instead of asking for another GitHub token pre-emptively.

## Build and artifact operations

- Dispatch `POST /repos/LostPatrol/OneKick/actions/workflows/build.yml/dispatches` with the selected branch as `ref`.
- Find the new `workflow_dispatch` run through `/actions/workflows/build.yml/runs`, matching branch, expected `head_sha`, and dispatch time. Poll with bounded waits and provide concise progress updates.
- Require `conclusion=success`, then locate the unexpired `onekick-{run_id}` artifact and retrieve its ZIP. Follow the signed artifact redirect without the API Authorization header.
- Extract under a unique ignored staging directory. Match one production JAR to the branch's `gradle.properties`; exclude sources, dev, and javadoc artifacts.

## Draft, upload, and publish

1. Look up `/releases/tags/{encoded_tag}` and authenticated draft releases. Resolve any existing tag to its commit. Do not treat authorization or server failures as proof that a tag or Release is absent.
2. Create a draft using `POST /repos/LostPatrol/OneKick/releases` with the exact `tag_name`, full `target_commitish` SHA, release `name`, exact `body`, user-selected `prerelease`, and `generate_release_notes=false`.
3. Remove the URI-template suffix from `upload_url`, append a URI-encoded JAR name, and POST raw `application/java-archive` bytes. Before resuming an interrupted upload, inspect existing assets and hashes. Never delete and replace an ambiguous upload blindly.
4. Re-fetch the draft and assets. Verify the full expected production asset set, exact body, uploaded states, sizes, and SHA-256 hashes. Only then PATCH the release with `draft=false`.
5. Verify publication and record the Release URL and `published_at` value.

## Platform synchronization

Inspect `/actions/workflows/publish-platforms.yml/runs` and each run's jobs, correlating the tag, event, time, and expected branch/platform matrix. Inspect logs for platform IDs, URLs, and failures. Query public platform APIs/pages to distinguish accepted, pending-review, and publicly downloadable states.

For targeted recovery, dispatch `publish-platforms.yml` from a branch containing the workflow with inputs such as `tag=0.1.4`, `branch=1.21.1`, `platform=modrinth`, and `dry_run=false`. Match the new run before waiting. Never rerun destinations already confirmed successful.

Official references: [GitHub Releases](https://docs.github.com/en/rest/releases/releases), [release assets](https://docs.github.com/en/rest/releases/assets), [workflow dispatch](https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event), and [artifacts](https://docs.github.com/en/rest/actions/artifacts).
