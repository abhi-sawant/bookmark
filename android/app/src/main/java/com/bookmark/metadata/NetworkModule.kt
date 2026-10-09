package com.bookmark.metadata

import com.bookmark.metadata.http.HtmlFetcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * The one HTTP client the app owns (spec 7.2).
 *
 * Redirects are followed by [HtmlFetcher] rather than OkHttp: OkHttp's limit is
 * hardcoded at 20 and the spec caps it at 5, and following them by hand is also
 * what makes the post-redirect URL available for resolving relative images and
 * lets each hop be upgraded off cleartext.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        // Hard ceiling across the whole call, so a slow trickle cannot pin a Worker.
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()
}

/**
 * M0-M2 bound [NoOpMetadataFetcher] here so every bookmark stayed PENDING. M3
 * swaps it for the real engine; no screen changed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataModule {
    @Binds
    abstract fun bindMetadataFetcher(impl: DefaultMetadataFetcher): MetadataFetcher
}
