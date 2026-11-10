package io.github.neronguyen.chat.sync.work

import io.github.neronguyen.chat.core.model.error.DataError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSyncWorkerTest {

    @Test
    fun `isRetryable correctly identifies transient network and server errors`() {
        assertTrue(DataError.Connectivity.NoInternet.isRetryable())
        assertTrue(DataError.Connectivity.Timeout.isRetryable())
        assertTrue(DataError.Remote.RateLimited().isRetryable())
        assertTrue(DataError.Remote.Unavailable.isRetryable())

        assertFalse(DataError.Remote.Unauthorized.isRetryable())
        assertFalse(DataError.Remote.Forbidden.isRetryable())
        assertFalse(DataError.Unexpected.Serialization.isRetryable())
        assertFalse(DataError.Unexpected.UnknownCode(500).isRetryable())
        assertFalse(DataError.Local.NotAuthenticated.isRetryable())
    }
}
