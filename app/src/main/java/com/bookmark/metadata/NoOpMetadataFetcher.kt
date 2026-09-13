package com.bookmark.metadata

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder until M3. Every bookmark stays PENDING, so titles come from the
 * fallback chain (spec 8.2) and thumbnails from generated monogram tiles
 * (spec 8.3) -- which is exactly the fallback state the design draws.
 */
@Singleton
class NoOpMetadataFetcher @Inject constructor() : MetadataFetcher {
    override suspend fun fetch(url: String): MetadataResult = MetadataResult.Pending
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataModule {
    @Binds
    abstract fun bindMetadataFetcher(impl: NoOpMetadataFetcher): MetadataFetcher
}
