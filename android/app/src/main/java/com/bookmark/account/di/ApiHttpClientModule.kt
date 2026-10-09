package com.bookmark.account.di

import com.bookmark.account.data.AuthTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * The sync/account backend's client -- derived from the app's one
 * [OkHttpClient] the same way `ImageLoaderModule` derives `@ImageHttpClient`.
 * Redirects are followed (the base client disables them for `HtmlFetcher`'s
 * own hand-rolled cap), and every request gets a bearer token attached when
 * one is available.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiHttpClient

/** Adds `Authorization: Bearer <token>` when signed in; a no-op otherwise. */
class AuthInterceptor @Inject constructor(
    private val authTokenStore: AuthTokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authTokenStore.currentTokenOrNull()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ApiHttpClientModule {

    @Provides
    @Singleton
    @ApiHttpClient
    fun provideApiOkHttpClient(
        okHttpClient: OkHttpClient,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient = okHttpClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(authInterceptor)
        .build()
}
