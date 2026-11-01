package io.github.neronguyen.chat.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.neronguyen.chat.core.model.error.DataError

@Composable
fun DataError.asUiText(): String = when (this) {
    DataError.Connectivity.NoInternet -> stringResource(R.string.error_no_internet)
    DataError.Connectivity.Timeout -> stringResource(R.string.error_timeout)
    DataError.Remote.Unauthorized, DataError.Local.NotAuthenticated -> stringResource(R.string.error_unauthorized)
    DataError.Remote.Forbidden -> stringResource(R.string.error_forbidden)
    is DataError.Remote.RateLimited -> stringResource(R.string.error_rate_limited)
    DataError.Remote.Unavailable -> stringResource(R.string.error_server_unavailable)
    is DataError.Unexpected.RequestRejected -> {
        if (fieldErrors.isNotEmpty()) {
            val fields = fieldErrors.keys.joinToString(", ")
            stringResource(R.string.error_fields_rejected, fields)
        } else {
            stringResource(R.string.error_unknown)
        }
    }
    is DataError.Unexpected -> stringResource(R.string.error_unknown)
}
