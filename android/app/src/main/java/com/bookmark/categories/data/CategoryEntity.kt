package com.bookmark.categories.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookmark.core.model.Category
import com.bookmark.core.model.CategoryWithCount

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    /** Unique case-insensitively, max 40 chars (spec 4.2). */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val colorHex: String,
    val iconKey: String?,
    val sortOrder: Int,
    val isDefault: Boolean,
    val createdAt: Long,
    /**
     * Sync bookkeeping, absent from the domain [Category] model -- didn't exist
     * before schema v3 (backfilled from [createdAt] on migration). `sortOrder`
     * is deliberately not synced (device-local), so it never needs to bump this.
     */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long,
    val syncedUpdatedAt: Long? = null,
) {
    fun toDomain() = Category(
        id = id,
        name = name,
        colorHex = colorHex,
        iconKey = iconKey,
        sortOrder = sortOrder,
        isDefault = isDefault,
        createdAt = createdAt,
    )
}

/**
 * [updatedAt] defaults to now (a fresh write always needs pushing);
 * [syncedUpdatedAt] defaults to null ("never synced"). Pass both explicitly
 * when carrying an existing entity's sync state forward.
 */
fun Category.toEntity(updatedAt: Long = System.currentTimeMillis(), syncedUpdatedAt: Long? = null) = CategoryEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncedUpdatedAt = syncedUpdatedAt,
)

/** Projection for the filter row and the Categories screen. */
data class CategoryWithCountEntity(
    @Embedded val category: CategoryEntity,
    val bookmarkCount: Int,
) {
    fun toDomain() = CategoryWithCount(category.toDomain(), bookmarkCount)
}
