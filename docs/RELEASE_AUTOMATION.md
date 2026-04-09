# Release Automation (GitHub + Modrinth)

This project includes a workflow at `.github/workflows/release-matrix.yml` that can build and publish multiple Minecraft/Fabric targets automatically.

## 1) Define your supported range

Edit `.github/release-targets.json` and add one object per target:

```json
[
  {
    "minecraft_version": "1.21.11",
    "yarn_mappings": "1.21.11+build.4",
    "loader_version": "0.18.4",
    "fabric_version": "0.141.3+1.21.11",
    "java": 21
  }
]
```

## 2) GitHub settings required

Repository settings -> Secrets and variables -> Actions

- Secret: `MODRINTH_TOKEN`
- Variable: `MODRINTH_PROJECT_ID` (example: `cmdpalette`)

## 3) Modrinth token scopes (minimum safe)

When creating your Modrinth Personal Access Token, use the minimum scopes:

- `Create versions` (required)
- `View versions` (optional, useful)

Do not grant unrelated scopes.

## 4) Security checklist

- Never commit token values in files, scripts, or README.
- Do not print token values in logs.
- Set a token expiration date and rotate periodically.
- Revoke and regenerate immediately if token exposure is suspected.

## 5) How to run

### Option A: tag push (recommended)

1. Create and push a tag:
   - `git tag -a v1.0.1 -m "CmdPalette v1.0.1"`
   - `git push origin v1.0.1`
2. Workflow runs automatically.

### Option B: manual dispatch

Run `Release Matrix` from GitHub Actions with:

- `release_tag`: existing tag (example `v1.0.1`)
- `publish_github_release`: true/false
- `publish_modrinth`: true/false
- `version_type`: release/beta/alpha

## 6) Output naming

Artifacts are generated per target and uploaded as:

- `cmdpalette-<modVersion>-mc<MINECRAFT>-fabric<LOADER>.jar`

This keeps binary naming consistent and release pages easy to browse.
