package com.bookmark.core.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * `NetworkModule`'s [OkHttpClient] disables redirects on purpose -- `HtmlFetcher`
 * follows them by hand for spec 7.2's 5-hop cap. Coil has no redirect logic of
 * its own, so thumbnails behind a redirect (common for CDN `og:image`s and
 * YouTube thumbnails) would silently fail to load if that client were reused
 * as-is. This qualifies the separate, redirect-following client image loading
 * needs.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageHttpClient

/** Spec 9: 25% memory-cache share, 250MB disk cache. */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    @ImageHttpClient
    fun provideImageOkHttpClient(okHttpClient: OkHttpClient): OkHttpClient =
        // Shares the connection pool/dispatcher (cheap) rather than building a
        // second client from scratch.
        okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @ImageHttpClient imageOkHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
        }
        .build()
}
