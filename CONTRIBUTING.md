# Contributing to Final-StandMarket

## Before development

1. Read [AGENTS.md](AGENTS.md) and [本地开发规范](docs/开发规范.md).
2. Locate the single requirement source: a Stage B item or confirmed PRD.
3. Use a workpack for non-trivial changes; use a confirmed Design when the architecture gate applies.
4. Do not commit local environment files, credentials, production data, or unredacted logs.

## Branch and change scope

- Do not push directly to `master`.
- Use a focused branch such as `feat/<topic>`, `fix/<topic>`, or `codex/<topic>`.
- Keep one workpack to one to three tightly related slices.
- Preserve unrelated local changes and stage only files owned by the workpack.

## Verification

Run the checks relevant to the change:

```bash
cd backend && mvn test
cd frontend/fashion-client && npm ci && npm run build
cd frontend/fashion-admin && npm ci && npm run build
cd agent-service && python -m pip install -r requirements-ci.txt && python -m pytest -q
git diff --check
```

The frontends currently have no unit-test, lint, or typecheck scripts. Do not claim those checks passed until the scripts exist and run successfully.

## Pull requests

- Complete the repository PR template.
- Link the requirement source, Design when applicable, workpack, independent review, and AC evidence.
- Wait for all required CI checks to pass.
- Resolve P0/P1 review findings before merge.
- Never bypass failed checks or expose secrets to make CI pass.
