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

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt,
)

/** Projection for the filter row and the Categories screen. */
data class CategoryWithCountEntity(
    @Embedded val category: CategoryEntity,
    val bookmarkCount: Int,
) {
    fun toDomain() = CategoryWithCount(category.toDomain(), bookmarkCount)
}
