package com.nexusnotes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An encrypted vault note. [payload] = AES-256-GCM(title + body), bound to [id].
 * Deliberately NOT linked to `notes`, folders, tags or the FTS index: nothing searchable.
 * Only timestamps are plaintext.
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val payload: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun equals(other: Any?) = other is VaultItemEntity && id == other.id && updatedAt == other.updatedAt && payload.contentEquals(other.payload)
    override fun hashCode() = id.hashCode()
}

/** Encrypted attachment: [payload] = AES-GCM(name, type, size); file bytes in vault/files/[fileName]. */
@Entity(
    tableName = "vault_attachments",
    foreignKeys = [ForeignKey(entity = VaultItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class VaultAttachmentEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val fileName: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val payload: ByteArray,
    val createdAt: Long,
) {
    override fun equals(other: Any?) = other is VaultAttachmentEntity && id == other.id && payload.contentEquals(other.payload)
    override fun hashCode() = id.hashCode()
}
