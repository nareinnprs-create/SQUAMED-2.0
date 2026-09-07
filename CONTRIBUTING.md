# Contributing to SQUAMED

## Branching

- `main` — production-ready, protected
- `develop` — integration branch
- `feature/*` — new features (from develop)
- `fix/*` — bug fixes

## Commit conventions

Use conventional commits:
`feat(domain): description`, `fix(module): description`, `docs: description`, `refactor: ...`, `test: ...`, `chore: ...`

## Development workflow

1. Create a feature branch from develop
2. Implement changes
3. Run lint + typecheck + tests
4. Open a PR with a detailed description
5. CI must pass before merge

## Code standards

- TypeScript strict mode
- Kotlin code style (ktlint)
- 80% test coverage on new code
- Accessibility: WCAG 2.1 AA
- All new features answer the 12 Master Development Rule questions
