package io.github.neronguyen.chat.core.network.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedOkHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeOkHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshOkHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl
