# Codebase-First UI Architecture (`Android-ChatApp`)

| Metadata           | Value                                                                   |
|--------------------|-------------------------------------------------------------------------|
| **Author**         | Nero Nguyen                                                             |
| **Status**         | Draft                                                                   |
| **Date**           | 2026-11-14                                                              |
| **Role**           | UI Governance & Verification Harness Specification                      |
| **Target Modules** | `Android-ChatApp`: `:core:ui`, `:feature:auth`, `:feature:chat`, `:app` |

---

## Overview

This specification defines the migration of `Android-ChatApp` from an external cloud-backed UI
governance model (Model Context Protocol (MCP) Stitch Single Source of Truth) to a **Codebase-First
UI** architecture.

Under this model, the visual Single Source of Truth (SSOT) lives inside the Git repository ([
`design-system.md`](./design-system.md) and `:core:ui` Compose theme tokens). AI agents and
developers author and verify UI changes directly inside the codebase through three mechanisms:

1. **Static Architectural Guardrails (`Konsist`)**: Unit-time AST and import rules in
   `ArchitectureKonsistTest.kt` that block hardcoded color literals, raw `Chat*` color token imports
   in feature modules, unconfigured `MaterialTheme { ... }` preview wrappers, and missing
   `ChatTheme`-wrapped `@Preview` functions.
2. **Shared `internal` Compose Previews (`@Preview`)**: Every feature `*Screen.kt` exposes
   `internal` `@Preview` functions wrapped in `ChatTheme` for local visual inspection (via IDE or
   the Android CLI `android preview render` command) and direct reuse by screenshot tests.
3. **Host-Side Screenshot Regression (AGP 9.5.1 `screenshotTests` suites) + Robolectric `testTag`
   Semantics Tests**: Automated visual golden comparison and behavioral assertions across
   `:core:ui`, `:feature:auth`, and `:feature:chat`.

---

## The Problem

1. **External Cloud SSOT Dependency and Inner-Loop Overhead**:
   Requiring a pre-design and post-execution MCP Stitch audit (`projects/10647833259558812227`) for
   every UI task introduces external network round-trips and out-of-repo state drift. Cloud mockups
   omit distributed-system runtime states (`SENDING`, `FAILED`, retry affordances, offline sync
   banners), creating authority conflicts between external mockups and the repository's data/sync
   architecture.
2. **Inconsistent Theme Consumption and Broken Previews Across Feature Modules**:
    - `LoginScreen.kt` bypasses `MaterialTheme.colorScheme` by importing raw `ChatBorder`,
      `ChatPrimary`, and `ChatSurface` tokens directly from
      `io.github.neronguyen.chat.core.ui.theme`, and no static rule blocks future hardcoded
      `Color(...)` literals.
    - All 6 existing `@Preview` functions across `:feature:chat` (`AccountScreen.kt`,
      `ConversationScreen.kt`, `ChatListScreen.kt`, `NewChatScreen.kt`) wrap their composables in
      stock `MaterialTheme { ... }` without parameters instead of `ChatTheme { ... }`. As a result,
      previews render Material 3's default purple baseline palette (`#6750A4`) rather than the
      *Quiet Messaging* Deep Teal palette (`#0F766E`) and Roboto / platform sans-serif typography
      defined in [`design-system.md`](./design-system.md).
3. **Missing Preview, Robolectric, and Screenshot Regression Coverage in Feature Modules**:
    - `:core:ui` runs AGP 9.5.1 `screenshotTests` (`testDebugScreenshotTestDefaultTestSuite`),
      whereas `:feature:auth` has **0 `@Preview` functions** across all 5 screens (`LoginScreen`,
      `RegisterScreen`, `ForgotPasswordScreen`, `ResetPasswordScreen`, `VerifyEmailScreen`), lacks
      Robolectric/Compose UI test dependencies in `feature/auth/build.gradle.kts`, and neither
      `:feature:auth` nor `:feature:chat` has host-side screenshot regression tests.
    - Duplicating preview composable bodies between `src/main` and `src/screenshotTest` across 9
      feature screens would duplicate `UiState` sample fixtures and allow `src/main` previews to
      drift from `src/screenshotTest` golden baselines.

---

## Goals

1. **Remove External Design Tool Coupling**: Delete all MCP Stitch references, screen URLs, audit
   steps, and workflows from `Android-ChatApp` documentation and agent governance gates
   (`AGENTS.md`, `core/ui/README.md`, `docs/designs/design-system.md`,
   `docs/designs/presentation-sync-ui-integration.md`).
2. **Enforce In-Repo Visual SSOT via Governance Docs & `Konsist`**:
    - Anchor all visual tokens and rules in [`design-system.md`](./design-system.md) and `:core:ui`
      (`Theme.kt`, `Color.kt`, `Shape.kt`, `Type.kt`), while `:feature:auth` and `:feature:chat`
      consume tokens exclusively through `MaterialTheme.colorScheme`, `MaterialTheme.typography`,
      and `MaterialTheme.shapes`.
    - Codify the preview and screenshot workflow in `AGENTS.md` Section 1.1 and module contracts
      (`feature/auth/README.md`, `feature/chat/README.md`, `core/ui/README.md`), and back it with 3
      new unit-time `Konsist` rules in `ArchitectureKonsistTest.kt` (bringing the suite from 5
      existing boundary rules to 8 total):
        - Ban hardcoded `Color(...)` calls and framework `Color.*` constants (allowing only
          `Color.Transparent` and `Color.Unspecified`) outside `:core:ui` theme files.
        - Ban direct imports of raw `Chat*` color tokens, wildcard `core.ui.theme.*` imports, and
          `MaterialTheme { ... }` composable wrapper calls in `:feature:*`.
        - Require every `*Screen.kt` in `:feature:*` to declare at least one `@Preview`-annotated
          function whose body invokes `ChatTheme`.
3. **Unify Feature `src/main` Previews and `src/screenshotTest` Golden Suites**:
    - Define `internal` `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` functions
      wrapped in `ChatTheme` inside all 9 feature `*Screen.kt` files (5 in `:feature:auth`, 4 in
      `:feature:chat`).
    - Enable AGP 9.5.1 `screenshotTests` suites on `:feature:auth` and `:feature:chat` (and add
      Robolectric + Compose UI test dependencies to `:feature:auth`), where each
      `*ScreenScreenshotTest` class delegates directly to the `internal` preview functions in
      `src/main`. Leave `:core:ui`'s existing working screenshot tests untouched (only narrowing
      `OtpInputField.kt` previews from `public` to `private`).
4. **Maintain Two-Tier Verification Speed**:
    - Keep Tier 1 (`./scripts/verify.sh`) scoped to fast unit/Robolectric/Konsist checks (triggering
      `:app:testDebugUnitTest --tests "*.ArchitectureKonsistTest"` when `core/ui/*` or `feature/*`
      changes) targeting a `<10s` warm loop.
    - Enforce `testDebugScreenshotTestDefaultTestSuite` across `:core:ui`, `:feature:auth`, and
      `:feature:chat` in Tier 2 (`./scripts/verify.sh clean` / CI).

---

## Non-Goals

1. **Wrapping Standard Material 3 Primitives in `:core:ui`**: Feature modules will continue using
   Material 3 primitives (`Scaffold`, `TopAppBar`, `OutlinedTextField`, `Button`) directly via
   `MaterialTheme` rather than creating custom wrapper composables (`ChatButton`, `ChatTextField`).
   Only shared multi-module or stateful widgets (`SyncStatusBar`, `OtpInputField`) reside in
   `:core:ui`.
2. **Dark Theme Implementation**: Dark mode palette and dark screenshot variants remain out of scope
   per [`design-system.md`](./design-system.md).
3. **Cross-Repository Wire or Sync Protocol Changes**: This design does not modify
   `KMP-ChatContract`, `KMP-ChatBackend`, or Android data/sync lifecycles (no Archify
   `.archify.json` diagram changes).

---

## Critical User Journeys (CUJs)

### CUJ 1: Agent Authors or Updates a Feature Screen In-Repo

* **Given**: A task requires adding or modifying UI in `:feature:auth` or `:feature:chat`.
* **When**: The agent builds the screen using Material 3 primitives and `MaterialTheme` tokens,
  defines or updates `internal` `@Preview` functions wrapped in `ChatTheme` inside `*Screen.kt`, and
  optionally renders the preview locally via `android preview render` (when the `android` CLI is
  available).
* **Then**: The agent verifies layout, text truncation, and contrast against [
  `design-system.md`](./design-system.md), asserts interactive semantics via Robolectric `testTag`
  unit tests, and passes `ArchitectureKonsistTest` in Tier 1 (`./scripts/verify.sh`) without calling
  any external design service.

### CUJ 2: Intentional Visual Update vs. Non-UI Refactor Under the Screenshot Gate

* **Given**: AGP 9.5.1 `screenshotTests` suites (`testDebugScreenshotTestDefaultTestSuite`) guard
  `:core:ui`, `:feature:auth`, and `:feature:chat`, with feature suites delegating to the `internal`
  preview functions in `src/main`.
* **When**:
    * **Case A (Intentional UI Change)**: The user requests a visual or layout modification in
      `:core:ui`, `:feature:auth`, or `:feature:chat`, and the agent updates the composable and/or
      its `internal` preview and runs
      `./scripts/verify.sh :<module>:updateDebugScreenshotTestDefaultTestSuite` (including
      `:feature:auth` and `:feature:chat` when shared `:core:ui` tokens or widgets change).
    * **Case B (Non-UI Logic/Refactor Task)**: The task modifies ViewModels, repositories, or
      non-visual state logic without requesting visual changes.
* **Then**:
    * In **Case A**, the updated reference PNGs under `<module>/src/screenshotTestDebug/reference/`
      are inspected and presented for user review, and `./scripts/verify.sh clean` passes.
    * In **Case B**, running `updateDebugScreenshotTestDefaultTestSuite` is prohibited, and any
      visual diff detected by `testDebugScreenshotTestDefaultTestSuite` during
      `./scripts/verify.sh clean` fails the build as an unintended regression.

---

## Technical Plan (Architecture)

```text
+-----------------------------------------------------------------------------------+
|                        IN-REPO VISUAL SSOT (:core:ui & docs)                      |
|  - docs/designs/design-system.md (Semantic Mapping & Quiet Messaging Rules)       |
|  - :core:ui/theme/{Color,Shape,Theme,Type}.kt (Executable Compose Tokens)         |
|  - :core:ui/component/{SyncStatusBar,OtpInputField}.kt (Shared Stateful Widgets)  |
+-----------------------------------------+-----------------------------------------+
                                          |
                 Consumes via MaterialTheme.{colorScheme,typography,shapes}
                                          v
+-----------------------------------------------------------------------------------+
|                   FEATURE PRESENTATION MODULES (:feature:auth, :feature:chat)     |
|  - Stateless *Screen(...) composables separated from *Route(...) ViewModels       |
|  - internal @Preview @Composable fun <Screen>Preview...() wrapped in ChatTheme    |
+-------------------+-------------------------------------------+-------------------+
                    |                                           |
     Tier 1 (<10s)  | ./scripts/verify.sh        Tier 2 (CI)    | ./scripts/verify.sh clean
                    v                                           v
+---------------------------------------+   +---------------------------------------+
|     TIER 1: INNER-LOOP VERIFICATION   |   |    TIER 2: CLEAN & GOLDEN GATE        |
|  1. ArchitectureKonsistTest (:app)    |   |  1. Full testDebugUnitTest + lintDebug|
|     - No Color(...) / Color.<Palette> |   |  2. AGP 9.5.1 screenshotTests         |
|     - No raw Chat* color imports      |   |     (0.0.1-alpha17 Layoutlib Engine)  |
|     - No MaterialTheme { ... } wrapper|   |     - *ScreenshotTest delegates to    |
|     - Every *Screen.kt has @Preview   |   |       internal *Preview() in src/main |
|       function invoking ChatTheme     |   |     - Compares against committed      |
|  2. Robolectric testTag Semantics     |   |       src/screenshotTestDebug/        |
|  3. Optional: android preview render  |   |       reference/**/*.png (Linux OS)   |
+---------------------------------------+   +---------------------------------------+
```

### 1. Visual SSOT and Module Boundary Rules

* **Authoritative Specification**: [`design-system.md`](./design-system.md) defines the semantic
  mapping and *Quiet Messaging* visual rules (flat `0.dp` elevation,
  `surfaceTint = Color.Transparent`, quiet connected state, non-blocking sync banners when cached
  data exists). `:core:ui` (`Color.kt`, `Shape.kt`, `Theme.kt`, `Type.kt`) provides the single
  executable token implementation.
* **Token Consumption Boundary**:
    * `:app` wraps the root navigation graph in `ChatTheme`.
    * `:feature:auth` and `:feature:chat` build screens with standard Material 3 primitives
      (`Scaffold`, `TopAppBar`, `OutlinedTextField`, `OutlinedSecureTextField`, `Button`,
      `TextButton`) and consume tokens exclusively via `MaterialTheme.colorScheme`,
      `MaterialTheme.typography`, and `MaterialTheme.shapes`.
    * In `LoginScreen.kt`, the three direct token imports (`ChatSurface`, `ChatBorder`,
      `ChatPrimary`) are replaced with `MaterialTheme.colorScheme.surfaceVariant` (`#F3F4F6`),
      `MaterialTheme.colorScheme.outline` (`#D1D5DB`), and `MaterialTheme.colorScheme.primary`
      (`#0F766E`).
    * In `:feature:chat` (`AccountScreen.kt`, `ConversationScreen.kt`, `ChatListScreen.kt`,
      `NewChatScreen.kt`), all 6 existing `@Preview` functions replace their unconfigured
      `MaterialTheme { ... }` wrapper with `ChatTheme { ... }`.

### 2. Static Architectural Guardrails (`ArchitectureKonsistTest.kt`)

Three new unit-time Konsist tests in
`app/src/test/kotlin/io/github/neronguyen/chat/architecture/ArchitectureKonsistTest.kt` (joining the
5 existing boundary rules for 8 rules total) enforce the UI boundaries. To avoid false positives on
comments, KDoc, or string literals, text-pattern checks run over comment-and-string-stripped source
text or Konsist AST declarations:

1. **`presentation and app modules do not hardcode Color literals outside core ui theme`**:
    * *Scope*: All files under `/src/main/` in `:feature:*`, `:core:ui`, and `:app`, excluding
      `/core/ui/src/main/kotlin/io/github/neronguyen/chat/core/ui/theme/`.
    * *Invariant*: Stripped code must not invoke the `Color(...)` constructor/factory
      (`\bColor\s*\(`) or reference `Color.<Constant>` (`\bColor\.([A-Z]\w*)`) other than
      `Color.Transparent` and `Color.Unspecified`.
2. **`feature modules consume theme colors via MaterialTheme instead of raw Chat color tokens`**:
    * *Scope*: All files under `/feature/*/src/main/`.
    * *Invariant*:
        * No wildcard import `import io.github.neronguyen.chat.core.ui.theme.*`.
        * No `file.imports` entry starting with `io.github.neronguyen.chat.core.ui.theme.Chat`
          except `io.github.neronguyen.chat.core.ui.theme.ChatTheme` in files that declare a
          `@Preview` function.
        * No composable wrapper invocation `MaterialTheme(` or `MaterialTheme {`
          (`\bMaterialTheme\s*[({]`) in stripped code, preventing feature screens or previews from
          instantiating a default Material 3 theme instead of `ChatTheme`.
3. **`every feature Screen file declares at least one Preview function wrapped in ChatTheme`**:
    * *Scope*: All files ending with `Screen.kt` under `/feature/*/src/main/`.
    * *Invariant*: Using Konsist AST (`file.functions()`), each `*Screen.kt` file must declare at
      least one function annotated with `@Preview` (`hasAnnotationWithName("Preview")`), and every
      `@Preview`-annotated function in the file must invoke `ChatTheme` inside its function body
      (`function.text`). (`internal` visibility for screenshot delegation is enforced directly by
      the Kotlin compiler when `src/screenshotTest` compiles against `src/main`.)

### 3. Shared `internal` `@Preview` Delegation and AGP 9.5.1 `screenshotTests` Suites

* **Gradle Test Suite & Robolectric Configuration**:
    * `gradle/libs.versions.toml` already defines `screenshot = "0.0.1-alpha17"`,
      `androidx-compose-ui-tooling`, `screenshot-validation-api`, `robolectric`,
      `androidx-compose-ui-test-junit4`, and `androidx-compose-ui-test-manifest`.
    * Both `feature/auth/build.gradle.kts` and `feature/chat/build.gradle.kts` register the
      `"screenshotTest"` suite under `android.testOptions.screenshotTests.create("screenshotTest")`
      with `engineVersion = libs.versions.screenshot.get()`, `targetVariants.add("debug")`, and test
      suite dependencies on `libs.androidx.compose.ui.tooling` and `libs.screenshot.validation.api`
      (matching `core/ui/build.gradle.kts`).
    * `feature/auth/build.gradle.kts` additionally enables
      `testOptions.unitTests.isIncludeAndroidResources = true` and adds
      `testImplementation(libs.robolectric)`, `testImplementation(libs.androidx.compose.ui.test)`,
      and `debugImplementation(libs.androidx.compose.ui.testManifest)` (matching
      `feature/chat/build.gradle.kts`) so CUJ 1 Robolectric `testTag` tests can run in
      `:feature:auth`.
* **Zero-Duplication `internal` Delegation Pattern**:
    * Each `*Screen.kt` in `/feature/*/src/main/` declares its preview functions as
      `internal @Composable fun <Name>Preview()` annotated with
      `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` and wrapped in `ChatTheme`.
    * Each corresponding `*ScreenshotTest.kt` class in `src/screenshotTest/kotlin/...` (sharing the
      same package as the screen) annotates a test method with `@PreviewTest` and
      `@Preview(name = "...", showBackground = true, widthDp = 390, heightDp = 844)` and calls the
      `internal` `*Preview()` function from `src/main` directly.
    * Existing working `:core:ui` screenshot tests (`SyncStatusBarScreenshotTest.kt`,
      `OtpInputFieldScreenshotTest.kt`) and their committed golden PNGs remain untouched; only
      `OtpInputField.kt`'s preview functions in `src/main` are narrowed from `public` to `private`
      so they do not leak into `:core:ui`'s public API.
* **Screen & State Matrix (15 Feature Screenshot Golden States + 8 Existing `:core:ui` States)**:
    * `:feature:auth` (5 screens, 7 preview states):
        * `LoginScreen`: `LoginScreenDefaultPreview` (populated email),
          `LoginScreenErrorAndBannerPreview` (`AuthError.EmailNotVerified` from `:core:model` with
          verify-email prompt + password-reset confirmation banner).
        * `RegisterScreen`: `RegisterScreenDefaultPreview` (populated fields),
          `RegisterScreenValidationErrorPreview` (`PasswordValidationError.TooShort` from
          `:validation:auth`).
        * `ForgotPasswordScreen`: `ForgotPasswordScreenDefaultPreview` (populated email).
        * `ResetPasswordScreen`: `ResetPasswordScreenDefaultPreview` (6-digit code + matching
          passwords, CTA enabled).
        * `VerifyEmailScreen`: `VerifyEmailScreenDefaultPreview` (partial OTP entry),
          `VerifyEmailScreenCooldownPreview` (`resendCooldownSeconds = 45` + `infoMessage`).
    * `:feature:chat` (4 screens, 8 preview states):
        * `ChatListScreen`: `ChatListScreenPopulatedPreview` (direct + group rooms,
          deleted/outgoing/incoming previews, `ChatSyncState.Synced` from `:core:model`),
          `ChatListScreenOfflinePreview` (`ChatSyncState.Offline` with
          `isInitialLoading = false, isBannerDismissed = false` so computed `showSyncBanner` is
          `true`).
        * `ConversationScreen`: `ConversationScreenDirectPreview` (incoming/outgoing `SENT`
          messages + date header), `ConversationScreenDeliveryStatesOfflinePreview`
          (`MessageDeliveryStatus.SENDING` and `MessageDeliveryStatus.FAILED` outgoing messages +
          `ChatSyncState.Offline` with `isBannerDismissed = false`).
        * `NewChatScreen`: `NewChatDirectPreview` (selected direct participant + first message),
          `NewChatGroupPreview` (group mode with selected participant chips).
        * `AccountScreen`: `AccountScreenVerifiedConnectedPreview` (verified badge +
          `ChatSyncState.Synced`), `AccountScreenUnverifiedOfflinePreview` (unverified +
          `ChatSyncState.Offline` banner).
* **Layoutlib Determinism & Canonical Host OS**:
    * All preview states pass static strings and pre-formatted timestamps (`"10:42 AM"`,
      `"Yesterday"`) to stateless `*Screen` composables; no preview uses `Clock.System.now()` or
      indeterminate `isLoading = true` progress spinners.
    * In `:feature:chat`, existing `SyncStatusBar` code already renders a static cloud-off icon for
      `ChatSyncState.Offline` (avoiding `rememberInfiniteTransition` used by `Connecting`/
      `Syncing`), and existing `AnimatedVisibility(visible = uiState.showSyncBanner)` in
      `ChatListScreen.kt` and `ConversationScreen.kt` initializes its state to `true` on frame 0
      when `showSyncBanner` is `true`—requiring no production code changes in `SyncStatusBar` or
      screen banner visibility.
    * All preview `ChatRoomUiModel` and `ParticipantUiModel` instances pass `avatarUrl = null`,
      which takes the existing deterministic initials branch in `ChatAvatar` without invoking Coil
      `AsyncImage`.
    * **Canonical Golden Environment**: Because host-side Layoutlib
      (`screenshot-validation-junit-engine` `0.0.1-alpha17`) can exhibit sub-pixel font
      anti-aliasing differences across host operating systems, **Linux** (matching the local
      development workspace and GitHub Actions `ubuntu-latest` CI runner) is the canonical
      environment for generating and verifying reference PNGs.

### 4. Two-Tier Verification Cadence & Golden Image Policy

* **Tier 1 (`./scripts/verify.sh`, target budget `<10s` warm)**:
    * Runs `testDebugUnitTest` on Git-modified modules.
    * When changes touch `core/ui/*`, `feature/auth/*`, or `feature/chat/*`, `scripts/verify.sh`
      also appends `:app:testDebugUnitTest --tests "*.ArchitectureKonsistTest"` so only the
      architectural guardrail suite in `:app` runs during inner-loop UI iteration.
    * Does not execute `testDebugScreenshotTestDefaultTestSuite`.
* **Tier 2 (`./scripts/verify.sh clean` / CI)**:
    * Executes
      `testDebugUnitTest lintDebug :core:ui:testDebugScreenshotTestDefaultTestSuite :feature:auth:testDebugScreenshotTestDefaultTestSuite :feature:chat:testDebugScreenshotTestDefaultTestSuite --rerun-tasks`.
    * Verifies `git status --porcelain` is empty after execution.
* **Golden Baseline Update Policy**:
    * *Permitted*: Only when a task explicitly modifies UI layouts, theme tokens, or `@Preview`
      states (`./scripts/verify.sh :<module>:updateDebugScreenshotTestDefaultTestSuite`), followed
      by visual inspection of the diffed PNGs under `<module>/src/screenshotTestDebug/reference/`.
      When shared tokens or components in `:core:ui` change visually, the developer/agent must also
      run `updateDebugScreenshotTestDefaultTestSuite` for `:feature:auth` and `:feature:chat` and
      inspect all cascading baseline diffs.
    * *Prohibited*: Never run `updateDebugScreenshotTestDefaultTestSuite` to silence unexpected
      failures during non-UI refactors, ViewModel/repository changes, or dependency upgrades.

---

## Alternatives Considered

### Alternative A: Retain MCP Stitch as an Optional Reference in `AGENTS.md`

* **Pros**: Preserves links to historical cloud mockups (`projects/10647833259558812227`) for human
  visual browsing.
* **Cons**: Creates dual-SSOT ambiguity for AI agents regarding whether to query MCP Stitch or trust
  in-repo tokens (`design-system.md` and `:core:ui`), and leaves stale external links that diverge
  from distributed-system UI states (`SENDING`, `FAILED`, offline banners).
* **Reason for Rejection**: Complete removal of MCP Stitch from governance docs eliminates dual-SSOT
  conflicts and external tool latency during inner-loop UI tasks.

### Alternative B: Restrict AGP `screenshotTests` to `:core:ui` Only

* **Pros**: Avoids adding `screenshotTests` configuration and 15 reference PNG baselines to
  `:feature:auth` and `:feature:chat`.
* **Cons**: Leaves all 9 feature screens (`LoginScreen`, `RegisterScreen`, `ForgotPasswordScreen`,
  `ResetPasswordScreen`, `VerifyEmailScreen`, `ChatListScreen`, `ConversationScreen`,
  `NewChatScreen`, `AccountScreen`) without automated visual regression protection when agents
  modify screen layouts or theme consumption.
* **Reason for Rejection**: Expanding AGP 9.5.1 `screenshotTests` suites to `:feature:auth` and
  `:feature:chat` while scoping execution to Tier 2 (`./scripts/verify.sh clean`) and standardizing
  on Linux host rendering protects all 9 feature screens without slowing down Tier 1
  (`./scripts/verify.sh`).

### Alternative C: Wrap All Material 3 Primitives in `:core:ui` (`ChatButton`, `ChatTextField`,
`ChatScaffold`)

* **Pros**: Prevents feature modules from importing `androidx.compose.material3.*` primitives
  directly.
* **Cons**: Introduces pass-through wrapper boilerplate across `:core:ui` and forces custom
  parameter forwarding for standard Material 3 components when `ChatTheme` + `MaterialTheme` +
  `Konsist` already enforce token consumption.
* **Reason for Rejection**: Contradicts [
  `PHILOSOPHY.md`](../../../KMP-ChatContract/docs/PHILOSOPHY.md) Section 3 (*Reject Cosmetic
  Bloat*); only shared multi-module or stateful widgets (`SyncStatusBar`, `OtpInputField`) belong in
  `:core:ui`.

### Alternative D: Keep Separate `private` `@Preview` Functions in `src/main` and Duplicated
`@PreviewTest` Bodies in `src/screenshotTest`

* **Pros**: Allows developers to edit `src/main` `@Preview` sample data without triggering golden
  diffs in `src/screenshotTest`.
* **Cons**: Duplicates `UiState` sample fixtures across `src/main` and `src/screenshotTest` for 9
  feature screens and allows IDE/CLI previews (`android preview render`) to drift from the states
  verified by `testDebugScreenshotTestDefaultTestSuite`.
* **Reason for Rejection**: Declaring `internal` `@Preview` functions in `:feature:*` `src/main` and
  calling them directly from `src/screenshotTest` eliminates fixture duplication and ensures local
  previews and golden screenshot tests render the same composable state.

---

## Non-Functional Requirements

* **Performance & Verification Latency**:
    * Tier 1 (`./scripts/verify.sh`) warm incremental execution targets a `<10s` budget by running
      unit/Robolectric tests and scoping `:app` to `--tests "*.ArchitectureKonsistTest"` without
      invoking Layoutlib screenshot rendering.
    * Tier 2 (`./scripts/verify.sh clean`) runs full unit tests, `lintDebug`, and
      `testDebugScreenshotTestDefaultTestSuite` across `:core:ui`, `:feature:auth`, and
      `:feature:chat`.
* **Repository Storage Footprint**:
    * Adding 15 mobile-viewport (`390x844` dp) reference PNGs across `:feature:auth` (7 images) and
      `:feature:chat` (8 images) adds ~300–600 KB of compressed PNG assets under
      `src/screenshotTestDebug/reference/`.

---

## Detailed Implementation Plan

> [!NOTE]
> Per Unified Elephant-and-Goldfish Model (EGM) Workflow Gate Phase 2 & Phase 3, this file-by-file
checklist provides an unambiguous blueprint for implementation and will be pruned when transitioning
the document to `Status: Implemented` after `./scripts/verify.sh clean` passes.

### Target Files to Create/Modify

#### 1. [`AGENTS.md`](../../AGENTS.md) (Modify — Applied in Workspace)

* **Type / Scope**: Repository Agent Governance Rules
* **Proposed Changes**: Replace Section `1.1 UI & Design System Gate (MCP Stitch SSOT)` with
  `1.1 Codebase-First UI & Design System Gate` establishing `docs/designs/design-system.md` and
  `:core:ui` as the visual SSOT, defining the `MaterialTheme`-only token policy for `:feature:*`,
  requiring `internal` `@Preview` functions wrapped in `ChatTheme`, and documenting the AGP 9.5.1
  `testDebugScreenshotTestDefaultTestSuite` / `updateDebugScreenshotTestDefaultTestSuite` workflow
  (including Linux canonical host and cascading `:core:ui` updates). Update Section `2.2` rule #6
  accordingly.

#### 2. [`docs/README.md`](../README.md) (Modify — Applied in Workspace)

* **Type / Scope**: Unified Repository Documentation Index
* **Proposed Changes**: Register [
  `./codebase-first-ui-architecture.md`](./codebase-first-ui-architecture.md) in the EGM Technical
  Design Documents table.

#### 3. [`docs/designs/design-system.md`](./design-system.md) (Modify — Applied in Workspace)

* **Type / Scope**: Visual & Token SSOT Specification
* **Proposed Changes**: Add Section `6. Codebase-First Enforcement & Component Extraction Policy`
  specifying the `MaterialTheme` consumption rule for `:feature:*`, component extraction criteria
  into `:core:ui`, and the static (`Konsist`) + visual (`screenshotTests`) enforcement loop.

#### 4. [
`docs/designs/presentation-sync-ui-integration.md`](./presentation-sync-ui-integration.md) (Modify — Applied in Workspace)

* **Type / Scope**: EGM Technical Design Document
* **Proposed Changes**: Remove all MCP Stitch screen URLs, Stitch audit requirements, and
  `Alternative F` Stitch pixel-comparison references so historical design docs do not point agents
  back to external cloud mockups.

#### 5. [`core/ui/README.md`](../../core/ui/README.md) (Modify — Applied in Workspace)

* **Type / Scope**: Module Contract Documentation
* **Proposed Changes**: Remove `(MCP Stitch projects/10647833259558812227)` from the module overview
  and reference `../docs/designs/design-system.md` as the visual specification.

#### 6. [`feature/auth/README.md`](../../feature/auth/README.md) (Modify)

* **Type / Scope**: Module Contract Documentation
* **Proposed Changes**: Update `## Invariants` to note `internal` `@Preview` functions wrapped in
  `ChatTheme` and `MaterialTheme`-only token consumption; update `## Verification` to include
  `:feature:auth:testDebugScreenshotTestDefaultTestSuite` and
  `:feature:auth:updateDebugScreenshotTestDefaultTestSuite`.

#### 7. [`feature/chat/README.md`](../../feature/chat/README.md) (Modify)

* **Type / Scope**: Module Contract Documentation
* **Proposed Changes**: Update `## Invariants` to note `internal` `@Preview` functions wrapped in
  `ChatTheme` and `MaterialTheme`-only token consumption; update `## Verification` to include
  `:feature:chat:testDebugScreenshotTestDefaultTestSuite` and
  `:feature:chat:updateDebugScreenshotTestDefaultTestSuite`.

#### 8. [`feature/auth/build.gradle.kts`](../../feature/auth/build.gradle.kts) (Modify)

* **Type / Scope**: Gradle Module Build Configuration
* **Proposed Changes**:
    * Add `testOptions` configuring `unitTests.isIncludeAndroidResources = true` and
      `screenshotTests.create("screenshotTest")` with
      `engineVersion = libs.versions.screenshot.get()`, `targetVariants.add("debug")`,
      `implementation(libs.androidx.compose.ui.tooling)`, and
      `implementation(libs.screenshot.validation.api)`.
    * Add `testImplementation(libs.robolectric)`,
      `testImplementation(libs.androidx.compose.ui.test)`, and
      `debugImplementation(libs.androidx.compose.ui.testManifest)` in `dependencies` (matching
      `feature/chat/build.gradle.kts`).

#### 9. [`feature/chat/build.gradle.kts`](../../feature/chat/build.gradle.kts) (Modify)

* **Type / Scope**: Gradle Module Build Configuration
* **Proposed Changes**: Inside `android.testOptions`, add `screenshotTests.create("screenshotTest")`
  with `engineVersion = libs.versions.screenshot.get()`, `targetVariants.add("debug")`,
  `implementation(libs.androidx.compose.ui.tooling)`, and
  `implementation(libs.screenshot.validation.api)`.

#### 10. [`scripts/verify.sh`](../../scripts/verify.sh) (Modify)

* **Type / Scope**: Verification & CI Gate Script
* **Proposed Changes**:
    * In the `clean` gate block, append `:feature:auth:testDebugScreenshotTestDefaultTestSuite` and
      `:feature:chat:testDebugScreenshotTestDefaultTestSuite` alongside
      `:core:ui:testDebugScreenshotTestDefaultTestSuite`.
    * In the Tier 1 affected-module `case` branches for `core/ui/*`, `feature/auth/*`, and
      `feature/chat/*`, trigger `:app:testDebugUnitTest --tests "*.ArchitectureKonsistTest"` so the
      architectural guardrails run whenever any UI or feature module changes.

#### 11. [
`app/src/test/kotlin/io/github/neronguyen/chat/architecture/ArchitectureKonsistTest.kt`](../../app/src/test/kotlin/io/github/neronguyen/chat/architecture/ArchitectureKonsistTest.kt) (Modify)

* **Type / Scope**: Static Architectural Unit Test (`Konsist`)
* **Proposed Changes**: Add a helper that strips comments and string literals from Kotlin source
  text, plus three `@Test` methods (bringing the suite from 5 to 8 tests):
    * `presentation and app modules do not hardcode Color literals outside core ui theme`: Asserts
      stripped text has no `Color(...)` calls and no `Color.<Constant>` references other than
      `Color.Transparent` and `Color.Unspecified` in `/src/main/` of `:feature:*`, `:core:ui`, and
      `:app` outside `/core/ui/src/main/kotlin/io/github/neronguyen/chat/core/ui/theme/`.
    * `feature modules consume theme colors via MaterialTheme instead of raw Chat color tokens`:
      Asserts files in `/feature/*/src/main/` have no wildcard
      `io.github.neronguyen.chat.core.ui.theme.*` import, no `Chat*` theme import except `ChatTheme`
      in files with `@Preview` functions, and no `MaterialTheme(` or `MaterialTheme {` composable
      wrapper calls in stripped text.
    * `every feature Screen file declares at least one Preview function wrapped in ChatTheme`: Using
      `file.functions()`, asserts every `*Screen.kt` under `/feature/*/src/main/` declares at least
      one `@Preview`-annotated function and every `@Preview` function in the file invokes
      `ChatTheme` in its body.

#### 12. [
`core/ui/src/main/kotlin/io/github/neronguyen/chat/core/ui/component/OtpInputField.kt`](../../core/ui/src/main/kotlin/io/github/neronguyen/chat/core/ui/component/OtpInputField.kt) (Modify)

* **Type / Scope**: Shared Compose UI Component (`:core:ui`)
* **Proposed Changes**: Change visibility of `OtpInputFieldEmptyPreview()`,
  `OtpInputFieldPartialPreview()`, `OtpInputFieldFullPreview()`, and `OtpInputFieldErrorPreview()`
  from `public` to `private` so preview functions do not leak into `:core:ui`'s public API.

#### 13. [
`feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/login/LoginScreen.kt`](../../feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/login/LoginScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:auth`)
* **Proposed Changes**:
    * Remove raw imports `ChatBorder`, `ChatPrimary`, and `ChatSurface`; replace their usages in the
      confirmation banner with `MaterialTheme.colorScheme.outline`,
      `MaterialTheme.colorScheme.primary`, and `MaterialTheme.colorScheme.surfaceVariant`.
    * Add two `internal` `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` functions
      wrapped in `ChatTheme`: `LoginScreenDefaultPreview()` and
      `LoginScreenErrorAndBannerPreview()`.

#### 14. [
`feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/register/RegisterScreen.kt`](../../feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/register/RegisterScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:auth`)
* **Proposed Changes**: Add two `internal`
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` functions wrapped in `ChatTheme`:
  `RegisterScreenDefaultPreview()` and `RegisterScreenValidationErrorPreview()`.

#### 15. [
`feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/forgot/ForgotPasswordScreen.kt`](../../feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/forgot/ForgotPasswordScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:auth`)
* **Proposed Changes**: Add `internal`
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` function
  `ForgotPasswordScreenDefaultPreview()` wrapped in `ChatTheme`.

#### 16. [
`feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/reset/ResetPasswordScreen.kt`](../../feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/reset/ResetPasswordScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:auth`)
* **Proposed Changes**: Add `internal`
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` function
  `ResetPasswordScreenDefaultPreview()` wrapped in `ChatTheme`.

#### 17. [
`feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/verify/VerifyEmailScreen.kt`](../../feature/auth/src/main/kotlin/io/github/neronguyen/chat/feature/auth/presentation/verify/VerifyEmailScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:auth`)
* **Proposed Changes**: Add two `internal`
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` functions wrapped in `ChatTheme`:
  `VerifyEmailScreenDefaultPreview()` and `VerifyEmailScreenCooldownPreview()`.

#### 18. [
`feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/list/ChatListScreen.kt`](../../feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/list/ChatListScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:chat`)
* **Proposed Changes**: Change `ChatListScreenPreview` to
  `internal fun ChatListScreenPopulatedPreview()` with
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` and replace its
  `MaterialTheme { ... }` wrapper with `ChatTheme { ... }`. Add
  `internal fun ChatListScreenOfflinePreview()` with
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` wrapped in `ChatTheme`
  (`syncState = ChatSyncState.Offline`, `isInitialLoading = false`, `isBannerDismissed = false`).

#### 19. [
`feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/conversation/ConversationScreen.kt`](../../feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/conversation/ConversationScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:chat`)
* **Proposed Changes**: Change `ConversationScreenDirectPreview()` from `private` to `internal` with
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` and replace its
  `MaterialTheme { ... }` wrapper with `ChatTheme { ... }`. Add
  `internal fun ConversationScreenDeliveryStatesOfflinePreview()` with
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` wrapped in `ChatTheme` covering
  `MessageDeliveryStatus.SENDING`, `MessageDeliveryStatus.FAILED`, and `ChatSyncState.Offline`
  (`isBannerDismissed = false`).

#### 20. [
`feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/newchat/NewChatScreen.kt`](../../feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/newchat/NewChatScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:chat`)
* **Proposed Changes**: Change `NewChatDirectPreview()` and `NewChatGroupPreview()` from `private`
  to `internal` (retaining `@Preview(showBackground = true, widthDp = 390, heightDp = 844)`) and
  replace their `MaterialTheme { ... }` wrappers with `ChatTheme { ... }`.

#### 21. [
`feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/account/AccountScreen.kt`](../../feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/account/AccountScreen.kt) (Modify)

* **Type / Scope**: Feature Screen Composable (`:feature:chat`)
* **Proposed Changes**: Change `AccountScreenPreview_VerifiedConnected` and
  `AccountScreenPreview_UnverifiedOffline` to `internal fun AccountScreenVerifiedConnectedPreview()`
  and `internal fun AccountScreenUnverifiedOfflinePreview()` with
  `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` and replace their
  `MaterialTheme { ... }` wrappers with `ChatTheme { ... }`.

#### 22.
`feature/auth/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/auth/presentation/login/LoginScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:auth`)
* **Proposed Changes**: Create `LoginScreenScreenshotTest` with `@PreviewTest` methods
  `loginScreenDefault()` and `loginScreenErrorAndBanner()` delegating to
  `LoginScreenDefaultPreview()` and `LoginScreenErrorAndBannerPreview()`.

#### 23.
`feature/auth/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/auth/presentation/register/RegisterScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:auth`)
* **Proposed Changes**: Create `RegisterScreenScreenshotTest` with `@PreviewTest` methods
  `registerScreenDefault()` and `registerScreenValidationError()` delegating to
  `RegisterScreenDefaultPreview()` and `RegisterScreenValidationErrorPreview()`.

#### 24.
`feature/auth/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/auth/presentation/forgot/ForgotPasswordScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:auth`)
* **Proposed Changes**: Create `ForgotPasswordScreenScreenshotTest` with `@PreviewTest` method
  `forgotPasswordScreenDefault()` delegating to `ForgotPasswordScreenDefaultPreview()`.

#### 25.
`feature/auth/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/auth/presentation/reset/ResetPasswordScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:auth`)
* **Proposed Changes**: Create `ResetPasswordScreenScreenshotTest` with `@PreviewTest` method
  `resetPasswordScreenDefault()` delegating to `ResetPasswordScreenDefaultPreview()`.

#### 26.
`feature/auth/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/auth/presentation/verify/VerifyEmailScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:auth`)
* **Proposed Changes**: Create `VerifyEmailScreenScreenshotTest` with `@PreviewTest` methods
  `verifyEmailScreenDefault()` and `verifyEmailScreenCooldown()` delegating to
  `VerifyEmailScreenDefaultPreview()` and `VerifyEmailScreenCooldownPreview()`.

#### 27.
`feature/chat/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/chat/presentation/list/ChatListScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:chat`)
* **Proposed Changes**: Create `ChatListScreenScreenshotTest` with `@PreviewTest` methods
  `chatListScreenPopulated()` and `chatListScreenOffline()` delegating to
  `ChatListScreenPopulatedPreview()` and `ChatListScreenOfflinePreview()`.

#### 28.
`feature/chat/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/chat/presentation/conversation/ConversationScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:chat`)
* **Proposed Changes**: Create `ConversationScreenScreenshotTest` with `@PreviewTest` methods
  `conversationScreenDirect()` and `conversationScreenDeliveryStatesOffline()` delegating to
  `ConversationScreenDirectPreview()` and `ConversationScreenDeliveryStatesOfflinePreview()`.

#### 29.
`feature/chat/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/chat/presentation/newchat/NewChatScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:chat`)
* **Proposed Changes**: Create `NewChatScreenScreenshotTest` with `@PreviewTest` methods
  `newChatDirect()` and `newChatGroup()` delegating to `NewChatDirectPreview()` and
  `NewChatGroupPreview()`.

#### 30.
`feature/chat/src/screenshotTest/kotlin/io/github/neronguyen/chat/feature/chat/presentation/account/AccountScreenScreenshotTest.kt` (Create)

* **Type / Scope**: Host-Side Screenshot Test (`:feature:chat`)
* **Proposed Changes**: Create `AccountScreenScreenshotTest` with `@PreviewTest` methods
  `accountScreenVerifiedConnected()` and `accountScreenUnverifiedOffline()` delegating to
  `AccountScreenVerifiedConnectedPreview()` and `AccountScreenUnverifiedOfflinePreview()`.

#### 31. `feature/auth/src/screenshotTestDebug/reference/` &
`feature/chat/src/screenshotTestDebug/reference/` (Create)

* **Type / Scope**: Golden Reference PNG Baselines
* **Proposed Changes**: Generate and visually inspect the 15 reference PNG files on Linux (7 in
  `:feature:auth`, 8 in `:feature:chat`) via
  `./scripts/verify.sh :feature:auth:updateDebugScreenshotTestDefaultTestSuite :feature:chat:updateDebugScreenshotTestDefaultTestSuite`.

---

### Verification Plan

* **Zero Stitch References**:
  `grep -rni "stitch" AGENTS.md README.md docs/designs/design-system.md docs/designs/presentation-sync-ui-integration.md core/ feature/ app/`
  returns 0 source file matches.
* **Unit, Robolectric & Static Architectural Tests (Tier 1)**: `./scripts/verify.sh` and
  `./gradlew :app:testDebugUnitTest :core:ui:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:chat:testDebugUnitTest`
  pass all 8 `ArchitectureKonsistTest` rules (5 existing, 3 new) and all Robolectric `testTag`
  semantics tests.
* **Static Analysis & Linting**: `./gradlew lintDebug` passes with 0 errors.
* **Screenshot Regression & Full Clean Gate (Tier 2)**: `./scripts/verify.sh clean` executes
  `testDebugUnitTest`, `lintDebug`, and
  `:core:ui:testDebugScreenshotTestDefaultTestSuite :feature:auth:testDebugScreenshotTestDefaultTestSuite :feature:chat:testDebugScreenshotTestDefaultTestSuite --rerun-tasks`
  and passes with an empty `git status --porcelain`.

---

*Drafted with EGM*
