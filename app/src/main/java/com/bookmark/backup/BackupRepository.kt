package com.bookmark.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.categories.data.CategoryDao
import com.bookmark.core.data.AppDatabase
import com.bookmark.core.data.IoDispatcher
import com.bookmark.core.model.Category
import com.bookmark.core.util.UrlNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ImportMode { MERGE, REPLACE }

data class ParsedBackup(
    val manifest: BackupManifestDto,
    val bookmarks: List<BookmarkBackupDto>,
    val categories: List<CategoryBackupDto>,
)

data class ImportPreview(
    val fileName: String,
    val bookmarksInFile: Int,
    val newToDevice: Int,
    val alreadySaved: Int,
    val categoriesInFile: Int,
    val parsed: ParsedBackup,
)

sealed interface ExportResult {
    data class Success(val bookmarkCount: Int, val categoryCount: Int) : ExportResult
    data object Failure : ExportResult
}

sealed interface ImportPreviewResult {
    data class Success(val preview: ImportPreview) : ImportPreviewResult
    data class Failure(val message: String) : ImportPreviewResult
}

sealed interface ImportCommitResult {
    data object Success : ImportCommitResult
    data class Failure(val message: String) : ImportCommitResult
}

/**
 * Spec 5.6's backup feature: a single `.zip` (`manifest.json`, `bookmarks.json`,
 * `categories.json`, `thumbnails/`) via the Storage Access Framework. The
 * `Uri` itself is always handed in already-picked -- SAF's picker needs an
 * Activity-scoped launcher, which a plain repository cannot own (see
 * `BookmarkNavHost`, where `rememberLauncherForActivityResult` lives).
 */
@Singleton
class BackupRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val categoryDao: CategoryDao,
    private val database: AppDatabase,
    private val bookmarkRepository: BookmarkRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(destination: Uri): ExportResult = withContext(io) {
        runCatching {
            val bookmarks = bookmarkDao.getAll()
            val categories = categoryDao.getAll()
            val manifest = BackupManifestDto(
                exportedAt = System.currentTimeMillis(),
                bookmarkCount = bookmarks.size,
                categoryCount = categories.size,
            )
            val opened = context.contentResolver.openOutputStream(destination)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.writeJsonEntry("manifest.json", json.encodeToString(manifest))
                    zip.writeJsonEntry("bookmarks.json", json.encodeToString(bookmarks.map { it.toBackupDto() }))
                    zip.writeJsonEntry("categories.json", json.encodeToString(categories.map { it.toBackupDto() }))
                    bookmarkRepository.thumbnailDir().listFiles()?.forEach { file ->
                        zip.putNextEntry(ZipEntry("thumbnails/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                true
            }
            if (opened == true) {
                ExportResult.Success(bookmarks.size, categories.size)
            } else {
                ExportResult.Failure
            }
        }.getOrDefault(ExportResult.Failure)
    }

    /** Parses only the (small) JSON entries -- thumbnail bytes are never held in memory. */
    suspend fun previewImport(source: Uri): ImportPreviewResult = withContext(io) {
        runCatching {
            val parsed = readBackupJson(source)
                ?: return@withContext ImportPreviewResult.Failure(
                    "This doesn't look like a Bookmarks backup.",
                )
            val existingUrls = bookmarkDao.allUrls().map(UrlNormalizer::normalize).toSet()
            val newCount = parsed.bookmarks.count { UrlNormalizer.normalize(it.url) !in existingUrls }
            ImportPreviewResult.Success(
                ImportPreview(
                    fileName = displayNameOf(source),
                    bookmarksInFile = parsed.bookmarks.size,
                    newToDevice = newCount,
                    alreadySaved = parsed.bookmarks.size - newCount,
                    categoriesInFile = parsed.categories.size,
                    parsed = parsed,
                ),
            )
        }.getOrElse { ImportPreviewResult.Failure("This doesn't look like a Bookmarks backup.") }
    }

    suspend fun commitImport(source: Uri, parsed: ParsedBackup, mode: ImportMode): ImportCommitResult =
        withContext(io) {
            runCatching {
                val inserted = when (mode) {
                    ImportMode.MERGE -> mergeImport(parsed)
                    ImportMode.REPLACE -> replaceImport(parsed)
                }
                writeThumbnails(source, inserted)
                ImportCommitResult.Success
            }.getOrElse { ImportCommitResult.Failure("Couldn't import this backup.") }
        }

    /** A bookmark whose URL is already saved is skipped -- the same dedupe rule [BookmarkRepository.save] enforces. */
    private suspend fun mergeImport(parsed: ParsedBackup): List<BookmarkBackupDto> =
        database.withTransaction {
            val existingUrls = bookmarkDao.allUrls().map(UrlNormalizer::normalize).toSet()
            parsed.categories.forEach { dto ->
                if (categoryDao.findById(dto.id) == null) categoryDao.insert(dto.toEntity())
            }
            val toInsert = parsed.bookmarks.filter { UrlNormalizer.normalize(it.url) !in existingUrls }
            toInsert.forEach { dto -> bookmarkDao.insert(dto.toEntity()) }
            toInsert
        }

    /**
     * Wipes bookmarks and every category except the seeded [Category.UNSORTED_ID]
     * row, then restores the file's contents verbatim. `Unsorted` is never
     * deleted-and-reinserted -- the FK safety net (`ON DELETE SET DEFAULT`)
     * needs it to always exist -- its colour/icon are updated from the file
     * instead, mirroring [com.bookmark.categories.data.CategoryRepository]'s own
     * treatment of that row as a special case.
     */
    private suspend fun replaceImport(parsed: ParsedBackup): List<BookmarkBackupDto> =
        database.withTransaction {
            bookmarkDao.deleteAll()
            categoryDao.deleteAllExcept(Category.UNSORTED_ID)
            parsed.categories.forEach { dto ->
                if (dto.id == Category.UNSORTED_ID) {
                    categoryDao.update(dto.toEntity())
                } else {
                    categoryDao.insert(dto.toEntity())
                }
            }
            parsed.bookmarks.forEach { dto -> bookmarkDao.insert(dto.toEntity()) }
            bookmarkRepository.thumbnailDir().listFiles()?.forEach { it.delete() }
            parsed.bookmarks
        }

    /** Re-opens [source] to stream just the thumbnails [inserted] actually reference. */
    private fun writeThumbnails(source: Uri, inserted: List<BookmarkBackupDto>) {
        val wanted = inserted.mapNotNull { it.thumbnailPath }.toSet()
        if (wanted.isEmpty()) return
        val dir = bookmarkRepository.thumbnailDir()
        dir.mkdirs()
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("thumbnails/")
                    if (entry.name.startsWith("thumbnails/") && name in wanted) {
                        File(dir, name).outputStream().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun readBackupJson(uri: Uri): ParsedBackup? {
        val entries = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name in JSON_ENTRY_NAMES) {
                        entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: return null

        val bookmarksJson = entries["bookmarks.json"] ?: return null
        val categoriesJson = entries["categories.json"] ?: return null
        val bookmarks = json.decodeFromString<List<BookmarkBackupDto>>(bookmarksJson)
        val categories = json.decodeFromString<List<CategoryBackupDto>>(categoriesJson)
        val manifest = entries["manifest.json"]?.let { json.decodeFromString<BackupManifestDto>(it) }
            ?: BackupManifestDto(exportedAt = 0L, bookmarkCount = bookmarks.size, categoryCount = categories.size)
        return ParsedBackup(manifest, bookmarks, categories)
    }

    private fun displayNameOf(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment ?: "backup.zip"
    }

    private fun ZipOutputStream.writeJsonEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private companion object {
        val JSON_ENTRY_NAMES = setOf("manifest.json", "bookmarks.json", "categories.json")
    }
}
