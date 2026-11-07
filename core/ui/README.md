# core:ui

## Overview
Shared Jetpack Compose design system components, application color palette, typography, and common UI helpers.

## Interface
- `[DataErrorToString.kt:asUiText]`: Compose extension function mapping domain `DataError` models to localized UI text.
- `[rememberMinLoading.kt:rememberMinLoading]`: Compose state utility ensuring minimum loading indicator duration to prevent UI flickering.

## Invariants
- Depends exclusively on `:core:model`.
- Must never depend on `:core:network`, `:core:database`, `:core:datastore`, or `:core:data` repository implementations.

## Side Effects
- None (pure Compose rendering components).

## Verification
- Run: `./gradlew :core:ui:testDebugUnitTest --rerun-tasks`
