# Repository Guidelines

## Project Structure & Module Organization

PulseOJ is a Java 17 Maven reactor with three modules: `common` defines serializable DTOs, enums, RMI interfaces, and network utilities; `backend` owns database access, RMI/UDP networking, judging, and controllers; `frontend` contains Swing screens and app launchers. Keep these boundaries intact: backend code must not depend on Swing, and UI panels should call backend controllers rather than open sockets or issue SQL. Sources follow `module/src/main/java`; add tests under `module/src/test/java`. Root-level `schema.sql`, `.env.example`, `RULES_AND_TASKS.md`, and `tasks/` hold database, configuration, architecture, and work notes.

## Build, Test, and Development Commands

- `mvn clean verify` - compile all modules and run configured tests.
- `mvn install` - build and install the reactor modules locally.
- `mvn -pl frontend exec:java` - launch the configured Master UI after installing modules. Run `ClientAppLauncher` or `WorkerAppLauncher` from the IDE for those roles.
- `mvn -pl backend -am test` - run backend tests and build required modules.

The frontend Maven exec plugin defaults to `MasterAppLauncher`. No test sources or coverage threshold are currently present/configured.

## Coding Style & Naming Conventions

Use Java 17, UTF-8, four-space indentation, and the existing `com.vku.pulseoj` package layout. Name classes in `UpperCamelCase`, methods and fields in `lowerCamelCase`, and constants in `UPPER_SNAKE_CASE`. Keep shared network contracts in `common`; DTOs must implement `Serializable` and declare `serialVersionUID`. Keep blocking RMI work off Swing's event thread and dispatch UI updates with `SwingUtilities.invokeLater`, following the project rules in `RULES_AND_TASKS.md`.

## Testing Guidelines

JUnit 5 is declared for backend tests. Add focused tests named `*Test.java` in the matching module's `src/test/java` tree, then run `mvn test` or the module-specific command above. Include tests for changes to judging, persistence, and protocol behavior when practical.

## Commit & Pull Request Guidelines

Git history was unavailable in this checkout, so no existing commit convention could be confirmed. Use concise imperative subjects with conventional prefixes, such as `feat: add worker heartbeat` or `fix: avoid blocking the UI`. PRs should summarize behavior and affected modules, link related tasks/issues, report build/test results, and include screenshots for visible Swing changes.

## Security & Configuration

Copy `.env.example` to `.env` for local configuration; never commit credentials. Only the Master backend should connect to Neon PostgreSQL. Keep secrets and database operations out of frontend code, and avoid including real credentials or personal data in logs or examples.
