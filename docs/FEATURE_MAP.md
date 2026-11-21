# Android-ChatApp — User-Perspective Feature Map

> Quick-reference map connecting **user-facing journeys** to `NavKey` routes, `*Screen` / `*ViewModel` owners, primary `testTag`s, and automated verification suites across the 5-layer testing pyramid.

---

## 1. Root Shell (`:app`)

| User State | Host Component | Switch Trigger | Primary `testTag`s | Verification Suites |
| :--- | :--- | :--- | :--- | :--- |
| App Startup / Auth Gate | `MainActivity` | `AuthRepository.getAuthState()` (`Unauthenticated` ↔ `Authenticated`) | `login_screen`, `chat_list_screen` | `MainActivityNavigationTest` (Hilt + Robolectric) |

---

## 2. Authentication Journeys (`:feature:auth` — `AuthNavGraph`)

| User Screen / Journey | `NavKey` Route | Screen & ViewModel | Primary `testTag`s | Verification Suites |
| :--- | :--- | :--- | :--- | :--- |
| **Sign In** | `AuthRoute.Login(email, bannerMessage)` | `LoginRoute` / `LoginViewModel` | `login_screen`, `confirmation_banner`, `email_field`, `password_field`, `login_error_text`, `verify_email_prompt_button`, `login_button`, `forgot_password_button`, `register_button` | `LoginViewModelTest`, `LoginScreenTest`, `LoginScreenScreenshotTest`, `AuthFeatureTest`, `AuthNavGraphTest` |
| **Create Account** | `AuthRoute.Register` | `RegisterRoute` / `RegisterViewModel` | `register_screen`, `email_field`, `display_name_field`, `password_field`, `register_error_text`, `register_button` | `RegisterViewModelTest`, `RegisterScreenTest`, `RegisterScreenScreenshotTest`, `AuthFeatureTest`, `AuthNavGraphTest` |
| **Verify Email (6-Digit OTP)** | `AuthRoute.VerifyEmail(email)` | `VerifyEmailRoute` / `VerifyEmailViewModel` | `verify_email_screen`, `otp_input_field`, `verify_email_error_text`, `verify_email_info_text`, `retry_button`, `resend_button` | `VerifyEmailViewModelTest`, `VerifyEmailScreenTest`, `VerifyEmailScreenScreenshotTest`, `AuthFeatureTest`, `AuthNavGraphTest`, `OtpRecoveryE2ETest` |
| **Forgot Password** | `AuthRoute.ForgotPassword` | `ForgotPasswordRoute` / `ForgotPasswordViewModel` | `forgot_password_screen`, `email_field`, `forgot_password_error_text`, `submit_button`, `back_to_login_button` | `ForgotPasswordViewModelTest`, `ForgotPasswordScreenTest`, `ForgotPasswordScreenScreenshotTest`, `AuthFeatureTest`, `AuthNavGraphTest` |
| **Reset Password (6-Digit OTP)** | `AuthRoute.ResetPassword(email)` | `ResetPasswordRoute` / `ResetPasswordViewModel` | `reset_password_screen`, `otp_input_field`, `new_password_field`, `confirm_password_field`, `reset_password_error_text`, `reset_password_info_text`, `reset_password_button`, `resend_button` | `ResetPasswordViewModelTest`, `ResetPasswordScreenTest`, `ResetPasswordScreenScreenshotTest`, `AuthFeatureTest`, `AuthNavGraphTest` |

---

## 3. Messaging Journeys (`:feature:chat` — `ChatNavGraph`)

| User Screen / Journey | `NavKey` Route | Screen & ViewModel | Primary `testTag`s | Verification Suites |
| :--- | :--- | :--- | :--- | :--- |
| **Conversations List & Search** | `ChatRoute.ChatList` | `ChatListRoute` / `ChatListViewModel` | `chat_list_screen`, `chat_list_new_chat_fab`, `sync_status_bar`, `sync_status_retry_button`, `sync_status_dismiss_button` | `ChatListViewModelTest`, `ChatListScreenTest`, `ChatListScreenScreenshotTest`, `ChatFeatureTest`, `ChatNavGraphTest`, `DirectChatE2ETest` |
| **New Direct or Group Chat** | `ChatRoute.NewChat` | `NewChatRoute` / `NewChatViewModel` | `new_chat_screen`, `new_chat_back_button`, `new_chat_mode_direct`, `new_chat_mode_group`, `new_chat_email_field`, `new_chat_search_button`, `new_chat_error_banner`, `new_chat_searched_participant_card`, `new_chat_participant_chip_<id>`, `new_chat_remove_participant_<id>`, `new_chat_first_message_field`, `new_chat_group_name_field`, `new_chat_submit_direct_button`, `new_chat_submit_group_button` | `NewChatViewModelTest`, `NewChatScreenTest`, `NewChatScreenScreenshotTest`, `ChatFeatureTest`, `ChatNavGraphTest`, `DirectChatE2ETest` |
| **Conversation (Messages, Retry/Delete, Group Actions)** | `ChatRoute.Conversation(roomId)` | `ConversationRoute` / `ConversationViewModel` | `conversation_message_input`, `conversation_send_button`, `sync_status_bar` | `ConversationViewModelTest`, `ConversationScreenTest`, `ConversationScreenScreenshotTest`, `ChatFeatureTest`, `ChatNavGraphTest`, `DirectChatE2ETest` |
| **Account & Sign Out** | `ChatRoute.Account` | `AccountRoute` / `AccountViewModel` | `account_screen`, `account_sign_out_button`, `account_sign_out_dialog`, `account_sign_out_confirm_button` | `AccountViewModelTest`, `AccountScreenTest`, `AccountScreenScreenshotTest`, `ChatFeatureTest`, `MainActivityNavigationTest` |

---

## 4. Background Sync & Architecture Invariants

- **Realtime & Catch-Up State Machines (`:core:data`)**: `RealtimeConnectionManagerStateMachineTest`, `ChatSyncManagerStateMachineTest`
- **Static Architecture Guardrails (`:app`)**: `ArchitectureKonsistTest` (module isolation, `hiltViewModel()` scoping, `:contract:*` isolation, `:core:testing` isolation, `@Preview` + `testTag` + `*ScreenTest` + `*ScreenshotTest` + `*FeatureTest` parity)
