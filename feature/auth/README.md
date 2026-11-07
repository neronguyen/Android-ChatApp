# feature:auth

## Overview
Authentication and onboarding user experience built with Jetpack Compose, executing client-side pre-validation and managing login/registration UI flows.

## Interface
- `[LoginScreen.kt:LoginScreen]`: Jetpack Compose screen for user login.
- `[LoginViewModel.kt:LoginViewModel]`: ViewModel managing login state, pre-validation, and submission.
- `[RegisterScreen.kt:RegisterScreen]`: Jetpack Compose screen for user registration.
- `[RegisterViewModel.kt:RegisterViewModel]`: ViewModel managing registration state and credential validation.
- `[AuthNavigation.kt:AuthNavGraph]`: Navigation 3 entrypoint integrating authentication routes into the app navigation host.

## Invariants
- Depends only on `:core:ui`, `:core:model`, `:core:data` repository interfaces, and `:validation:auth`.
- Never imports wire DTOs (`:contract:*`) or database entities.
- Validates inputs synchronously via `AuthValidator` before dispatching network requests.
- Exposes deterministic `testTag` identifiers for headless Robolectric testing.

## Side Effects
- Mutates UI state flows; triggers authentication network requests via `AuthRepository`.

## Verification
- Run: `./gradlew :feature:auth:testDebugUnitTest --rerun-tasks`
