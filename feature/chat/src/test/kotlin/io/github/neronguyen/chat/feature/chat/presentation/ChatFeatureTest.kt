package io.github.neronguyen.chat.feature.chat.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import arrow.core.Either
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyen.chat.core.testing.FakeChatMessageRepository
import io.github.neronguyen.chat.core.testing.FakeChatRoomRepository
import io.github.neronguyen.chat.core.testing.FakeChatSyncManager
import io.github.neronguyen.chat.core.testing.TestFixtures
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyen.chat.feature.chat.R
import io.github.neronguyen.chat.feature.chat.presentation.account.AccountRoute
import io.github.neronguyen.chat.feature.chat.presentation.account.AccountViewModel
import io.github.neronguyen.chat.feature.chat.presentation.conversation.ConversationRoute
import io.github.neronguyen.chat.feature.chat.presentation.conversation.ConversationViewModel
import io.github.neronguyen.chat.feature.chat.presentation.list.ChatListRoute
import io.github.neronguyen.chat.feature.chat.presentation.list.ChatListViewModel
import io.github.neronguyen.chat.feature.chat.presentation.newchat.NewChatRoute
import io.github.neronguyen.chat.feature.chat.presentation.newchat.NewChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import kotlin.time.Instant

/**
 * Tier 1/2 Feature Tests integrating Compose `*Route` screens, real ViewModels, and Fake chat
 * repositories / sync manager on the JVM under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class ChatFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun str(resId: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(resId, *args)

    @Test
    fun newChatRoute_displaysSearchError_thenFindsPeerAndCreatesDirectRoom() {
        val fakeAuthRepo = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(TestFixtures.user(), "access-token"),
        )
        val fakeRoomRepo = FakeChatRoomRepository()
        val bobId = "00000000-0000-0000-0000-000000000002"
        val bob = ChatParticipant(
            userId = bobId,
            email = "bob@example.com",
            displayName = "Bob",
        )
        fakeRoomRepo.findParticipantHandler = { email ->
            if (email == "bob@example.com") {
                Either.Right(bob)
            } else {
                Either.Left(ChatError.ParticipantNotFound)
            }
        }
        val createdRooms = mutableListOf<String>()
        val viewModel = NewChatViewModel(fakeRoomRepo, fakeAuthRepo)

        composeTestRule.setContent {
            ChatTheme {
                NewChatRoute(
                    viewModel = viewModel,
                    onBackClick = {},
                    onChatCreated = { createdRooms.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_email_field")
            .performTextReplacement("unknown@example.com")
        composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.chat_error_participant_not_found))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_chat_email_field")
            .performTextReplacement("bob@example.com")
        composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_first_message_field")
            .performScrollTo()
            .performTextInput("Hello Bob")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_submit_direct_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("room-$bobId"), createdRooms)
    }

    @Test
    fun conversationRoute_sendsMessage_andReactsToOfflineSyncRecovery() {
        val room = ChatRoom(
            id = "room-1",
            type = ChatRoomType.DIRECT,
            creator = ChatParticipant("user-me", "alice@example.com", "Alice"),
            participants = listOf(
                ChatParticipant("user-me", "alice@example.com", "Alice"),
                ChatParticipant("user-bob", "bob@example.com", "Bob"),
            ),
        )
        val fakeAuthRepo = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(TestFixtures.user(), "access-token"),
        )
        val fakeRoomRepo = FakeChatRoomRepository(initialRooms = listOf(room))
        val fakeMessageRepo = FakeChatMessageRepository()
        val fakeSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)

        val viewModel = ConversationViewModel(
            roomId = "room-1",
            chatRoomRepository = fakeRoomRepo,
            chatMessageRepository = fakeMessageRepo,
            chatSyncManager = fakeSyncManager,
            authRepository = fakeAuthRepo,
        ).apply {
            nowProvider = { Instant.parse("2026-11-16T12:00:00Z") }
            zoneIdProvider = { ZoneId.of("UTC") }
        }

        composeTestRule.setContent {
            ChatTheme {
                ConversationRoute(
                    viewModel = viewModel,
                    onBackClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("conversation_message_input")
            .performTextReplacement("Hello from JVM")
        composeTestRule.onNodeWithTag("conversation_send_button")
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("room-1" to "Hello from JVM"), fakeMessageRepo.sentMessages)
        composeTestRule.onNodeWithText("Hello from JVM").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.conversation_status_sent)).assertIsDisplayed()

        // Offline transition surfaces sync banner and disables send while preserving draft
        composeTestRule.onNodeWithTag("conversation_message_input")
            .performTextReplacement("Draft while offline")
        fakeSyncManager.syncStateFlow.value = ChatSyncState.Offline
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()

        // Recovering to Synced hides sync banner and re-enables send button
        fakeSyncManager.syncStateFlow.value = ChatSyncState.Synced
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sync_status_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("conversation_send_button").assertIsEnabled()
    }

    @Test
    fun accountRoute_displaysSyncState_andConfirmsSignOut() {
        val fakeAuthRepo = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(TestFixtures.user(), "access-token"),
        )
        val fakeSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)
        val viewModel = AccountViewModel(fakeAuthRepo, fakeSyncManager)

        composeTestRule.setContent {
            ChatTheme {
                AccountRoute(
                    viewModel = viewModel,
                    onBackClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_sign_out_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_sign_out_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_sign_out_confirm_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, fakeAuthRepo.logoutCallCount)
    }

    @Test
    fun chatListRoute_filtersBySearch_transitionsEmptyToPopulated_andRetriesSyncFailure() {
        val fakeAuthRepo = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(TestFixtures.user(), "access-token"),
        )
        val fakeRoomRepo = FakeChatRoomRepository()
        val fakeSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)
        val viewModel = ChatListViewModel(
            chatRoomRepository = fakeRoomRepo,
            authRepository = fakeAuthRepo,
            chatSyncManager = fakeSyncManager,
        ).apply {
            zoneIdProvider = { ZoneId.of("UTC") }
        }

        composeTestRule.setContent {
            ChatTheme {
                ChatListRoute(
                    viewModel = viewModel,
                    onOpenConversation = {},
                    onNewChatClick = {},
                    onAccountClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // Empty state is displayed initially
        composeTestRule.onNodeWithText(str(R.string.empty_chats_title)).assertIsDisplayed()

        // Populating rooms transitions to populated list
        val bobRoom = TestFixtures.directRoom(
            id = "room-bob",
            peer = TestFixtures.participant("user-bob", "bob@example.com", "Bob"),
        )
        val carolRoom = TestFixtures.directRoom(
            id = "room-carol",
            peer = TestFixtures.participant("user-carol", "carol@example.com", "Carol"),
        )
        fakeRoomRepo.roomsFlow.value = listOf(bobRoom, carolRoom)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carol").assertIsDisplayed()

        // Search filtering narrows visible rooms
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Bob")
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText("Bob") and !hasSetTextAction()).assertIsDisplayed()
        composeTestRule.onNodeWithText("Carol").assertDoesNotExist()

        // SyncStatusBar surfaces on Offline / Failed and triggers retry
        fakeSyncManager.syncStateFlow.value = ChatSyncState.Offline
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()

        fakeSyncManager.syncStateFlow.value = ChatSyncState.Failed(DataError.Connectivity.Timeout)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sync_status_retry_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, fakeSyncManager.retryCallCount)
    }

    @Test
    fun newChatRoute_groupMode_managesChips_validatesRoomSize_andCreatesGroup() {
        val fakeAuthRepo = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(TestFixtures.user(), "access-token"),
        )
        val fakeRoomRepo = FakeChatRoomRepository()
        val bob = TestFixtures.participant(
            userId = "00000000-0000-0000-0000-000000000002",
            email = "bob@example.com",
            displayName = "Bob",
        )
        val carol = TestFixtures.participant(
            userId = "00000000-0000-0000-0000-000000000003",
            email = "carol@example.com",
            displayName = "Carol",
        )
        fakeRoomRepo.findParticipantHandler = { email ->
            when (email) {
                "bob@example.com" -> Either.Right(bob)
                "carol@example.com" -> Either.Right(carol)
                else -> Either.Left(ChatError.ParticipantNotFound)
            }
        }

        val createdRooms = mutableListOf<String>()
        val viewModel = NewChatViewModel(fakeRoomRepo, fakeAuthRepo)

        composeTestRule.setContent {
            ChatTheme {
                NewChatRoute(
                    viewModel = viewModel,
                    onBackClick = {},
                    onChatCreated = { createdRooms.add(it) },
                )
            }
        }

        // Switch to GROUP mode
        composeTestRule.onNodeWithTag("new_chat_mode_group").performClick()
        composeTestRule.waitForIdle()

        // Search and add Bob, verify chip appears, then remove Bob and verify chip disappears
        composeTestRule.onNodeWithTag("new_chat_email_field").performTextReplacement("bob@example.com")
        composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_participant_chip_${bob.userId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_remove_participant_${bob.userId}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_participant_chip_${bob.userId}").assertDoesNotExist()

        // Re-add Bob alone and verify submit triggers client-side TooFewGroupParticipants validation
        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_submit_group_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.new_chat_error_no_group_participants))
            .performScrollTo()
            .assertIsDisplayed()

        // Add Carol, enter optional group name, verify server InvalidRoomSize error, then succeed
        composeTestRule.onNodeWithTag("new_chat_email_field")
            .performScrollTo()
            .performTextReplacement("carol@example.com")
        composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_group_name_field")
            .performScrollTo()
            .performTextInput("Platform Guild")

        val defaultGroupHandler = fakeRoomRepo.createGroupRoomHandler
        fakeRoomRepo.createGroupRoomHandler = { _, _ -> Either.Left(ChatError.InvalidRoomSize) }
        composeTestRule.onNodeWithTag("new_chat_submit_group_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.chat_error_invalid_room_size))
            .performScrollTo()
            .assertIsDisplayed()

        fakeRoomRepo.createGroupRoomHandler = defaultGroupHandler
        composeTestRule.onNodeWithTag("new_chat_submit_group_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(
                listOf(bob.userId, carol.userId) to "Platform Guild",
                listOf(bob.userId, carol.userId) to "Platform Guild",
            ),
            fakeRoomRepo.createGroupRoomCalls,
        )
        assertEquals(listOf("group-1"), createdRooms)
    }

    @Test
    fun conversationRoute_groupActions_addsParticipant_leavesRoom_andHandlesFailedAndSentMessages() {
        val groupRoom = TestFixtures.groupRoom(id = "group-1", name = "Platform")
        val failedMessage = TestFixtures.message(
            id = "msg-failed",
            roomId = "group-1",
            senderId = "user-me",
            content = "Retry me",
            deliveryStatus = MessageDeliveryStatus.FAILED,
        )
        val sentMessage = TestFixtures.message(
            id = "msg-sent",
            roomId = "group-1",
            senderId = "user-me",
            content = "Delete me",
            deliveryStatus = MessageDeliveryStatus.SENT,
        )

        val fakeAuthRepo = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(TestFixtures.user(), "access-token"),
        )
        val fakeRoomRepo = FakeChatRoomRepository(initialRooms = listOf(groupRoom))
        val dave = TestFixtures.participant(
            userId = "00000000-0000-0000-0000-000000000004",
            email = "dave@example.com",
            displayName = "Dave",
        )
        fakeRoomRepo.findParticipantHandler = { email ->
            if (email == "dave@example.com") Either.Right(dave) else Either.Left(ChatError.ParticipantNotFound)
        }
        val fakeMessageRepo = FakeChatMessageRepository(
            initialMessages = listOf(failedMessage, sentMessage),
        )
        val fakeSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)
        var backClickCount = 0

        val viewModel = ConversationViewModel(
            roomId = "group-1",
            chatRoomRepository = fakeRoomRepo,
            chatMessageRepository = fakeMessageRepo,
            chatSyncManager = fakeSyncManager,
            authRepository = fakeAuthRepo,
        ).apply {
            zoneIdProvider = { ZoneId.of("UTC") }
        }

        composeTestRule.setContent {
            ChatTheme {
                ConversationRoute(
                    viewModel = viewModel,
                    onBackClick = { backClickCount++ },
                )
            }
        }

        composeTestRule.waitForIdle()

        // Retry FAILED message
        composeTestRule.onNodeWithText(str(R.string.conversation_tap_to_retry)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("msg-failed"), fakeMessageRepo.retriedMessageIds)

        // Long-press SENT message and delete via context menu
        composeTestRule.onNodeWithText("Delete me").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.conversation_delete_message)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("msg-sent"), fakeMessageRepo.deletedMessageIds)

        // Open options menu -> Add participant
        composeTestRule.onNodeWithContentDescription(str(R.string.conversation_more_options)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.conversation_add_participant)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
            .performTextInput("dave@example.com")
        composeTestRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
            .performImeAction()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Dave").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.conversation_add_participant_confirm)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("group-1" to listOf(dave.userId)), fakeRoomRepo.addParticipantsCalls)

        // Open options menu -> Leave room triggers back navigation
        composeTestRule.onNodeWithContentDescription(str(R.string.conversation_more_options)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.conversation_leave_room)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("group-1"), fakeRoomRepo.leaveRoomCalls)
        assertEquals(1, backClickCount)
    }
}
