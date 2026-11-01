package io.github.neronguyen.chat.feature.chat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.neronguyen.chat.feature.chat.presentation.account.AccountRoute
import io.github.neronguyen.chat.feature.chat.presentation.account.AccountViewModel
import io.github.neronguyen.chat.feature.chat.presentation.conversation.ConversationRoute
import io.github.neronguyen.chat.feature.chat.presentation.conversation.ConversationViewModel
import io.github.neronguyen.chat.feature.chat.presentation.list.ChatListRoute
import io.github.neronguyen.chat.feature.chat.presentation.list.ChatListViewModel
import io.github.neronguyen.chat.feature.chat.presentation.newchat.NewChatRoute
import io.github.neronguyen.chat.feature.chat.presentation.newchat.NewChatViewModel

fun EntryProviderScope<NavKey>.chatSection(
    onOpenConversation: (roomId: String) -> Unit,
    onNewChatClick: () -> Unit,
    onAccountClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onChatCreated: (roomId: String) -> Unit = onOpenConversation,
) {
    entry<ChatRoute.ChatList> {
        val viewModel: ChatListViewModel = hiltViewModel()
        ChatListRoute(
            viewModel = viewModel,
            onOpenConversation = onOpenConversation,
            onNewChatClick = onNewChatClick,
            onAccountClick = onAccountClick,
        )
    }

    entry<ChatRoute.NewChat> {
        val viewModel: NewChatViewModel = hiltViewModel()
        NewChatRoute(
            viewModel = viewModel,
            onBackClick = onBackClick,
            onChatCreated = onChatCreated,
        )
    }

    entry<ChatRoute.Conversation> { route ->
        val viewModel =
            hiltViewModel<ConversationViewModel, ConversationViewModel.Factory> { factory ->
                factory.create(route.roomId)
            }
        ConversationRoute(
            viewModel = viewModel,
            onBackClick = onBackClick,
        )
    }

    entry<ChatRoute.Account> {
        val viewModel: AccountViewModel = hiltViewModel()
        AccountRoute(
            viewModel = viewModel,
            onBackClick = onBackClick,
        )
    }
}

@Composable
fun ChatNavGraph(
    modifier: Modifier = Modifier,
    startRoute: ChatRoute = ChatRoute.ChatList,
    onOpenConversation: (roomId: String) -> Unit = {},
    onAccountClick: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(startRoute)

    val entryProvider = remember(backStack, onOpenConversation, onAccountClick) {
        entryProvider {
            chatSection(
                onOpenConversation = { roomId ->
                    if (backStack.lastOrNull() != ChatRoute.Conversation(roomId)) {
                        backStack.add(ChatRoute.Conversation(roomId))
                    }
                    onOpenConversation(roomId)
                },
                onNewChatClick = {
                    if (backStack.lastOrNull() != ChatRoute.NewChat) {
                        backStack.add(ChatRoute.NewChat)
                    }
                },
                onAccountClick = {
                    if (backStack.lastOrNull() != ChatRoute.Account) {
                        backStack.add(ChatRoute.Account)
                    }
                    onAccountClick()
                },
                onBackClick = { backStack.pop() },
                onChatCreated = { roomId ->
                    backStack.pop()
                    if (backStack.lastOrNull() != ChatRoute.Conversation(roomId)) {
                        backStack.add(ChatRoute.Conversation(roomId))
                    }
                    onOpenConversation(roomId)
                },
            )
        }
    }

    val saveableStateHolderDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val decorators = remember(saveableStateHolderDecorator, viewModelStoreDecorator) {
        listOf(saveableStateHolderDecorator, viewModelStoreDecorator)
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = decorators,
        entryProvider = entryProvider,
    )

    NavDisplay(
        entries = entries,
        onBack = { backStack.pop() },
        modifier = modifier,
    )
}

private fun NavBackStack<NavKey>.pop() {
    if (size > 1) {
        removeAt(lastIndex)
    }
}
