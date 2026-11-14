# feature:auth

## Overview
Authentication and onboarding user experience built with Jetpack Compose, executing client-side pre-validation and managing login/registration UI flows.

## Interface
- `[LoginScreen.kt:LoginScreen]`: Jetpack Compose screen for user login with contextual banners.
- `[LoginViewModel.kt:LoginViewModel]`: ViewModel managing login state, pre-validation, and submission.
- `[RegisterScreen.kt:RegisterScreen]`: Jetpack Compose screen for user registration.
- `[RegisterViewModel.kt:RegisterViewModel]`: ViewModel managing registration state and credential validation.
- `[VerifyEmailScreen.kt:VerifyEmailScreen]`: Jetpack Compose screen for 6-digit OTP email verification with `OtpInputField`.
- `[VerifyEmailViewModel.kt:VerifyEmailViewModel]`: ViewModel managing email verification, auto-submission on 6th digit, and declarative `SavedStateHandle` cooldown timer.
- `[ForgotPasswordScreen.kt:ForgotPasswordScreen]`: Jetpack Compose screen for password recovery email submission.
- `[ForgotPasswordViewModel.kt:ForgotPasswordViewModel]`: ViewModel managing password reset code dispatch.
- `[ResetPasswordScreen.kt:ResetPasswordScreen]`: Jetpack Compose screen for entering 6-digit OTP and new password with validation gates.
- `[ResetPasswordViewModel.kt:ResetPasswordViewModel]`: ViewModel managing OTP reset validation, mismatch protection, and cooldown timers.
- `[AuthNavigation.kt:AuthNavGraph]`: Navigation 3 entrypoint integrating authentication routes into the app navigation host.

## Invariants
- Depends only on `:core:ui`, `:core:model`, `:core:data` repository interfaces, and `:validation:auth`.
- Never imports wire DTOs (`:contract:*`) or database entities.
- Consumes visual tokens exclusively via `MaterialTheme.colorScheme`, `MaterialTheme.typography`,
  and `MaterialTheme.shapes` (never hardcoded `Color(...)` literals or raw `Chat*` color constants).
- Every `*Screen.kt` exposes `internal` `@Preview` functions wrapped in `ChatTheme` reused by
  `src/screenshotTest`.
- Validates inputs synchronously via `AuthValidator` before dispatching network requests.
- Exposes deterministic `testTag` identifiers for headless Robolectric testing.

## Side Effects
- Mutates UI state flows; triggers authentication network requests via `AuthRepository`.

## Verification

- Unit tests: `./gradlew :feature:auth:testDebugUnitTest --rerun-tasks`
- Validate screenshots against references:
  `./gradlew :feature:auth:testDebugScreenshotTestDefaultTestSuite`
- Update screenshot reference images:
  `./gradlew :feature:auth:updateDebugScreenshotTestDefaultTestSuite`
