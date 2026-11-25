package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexusnotes.data.entity.VaultAttachmentEntity
import com.nexusnotes.data.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY updatedAt DESC")
    fun observeItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items")
    suspend fun allItems(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_attachments")
    suspend fun allAttachments(): List<VaultAttachmentEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getItem(id: String): VaultItemEntity?

    @Query("SELECT COUNT(*) FROM vault_items")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: VaultItemEntity)

    @Query("UPDATE vault_items SET payload = :payload, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateItem(id: String, payload: ByteArray, updatedAt: Long): Int

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("SELECT * FROM vault_attachments WHERE itemId = :itemId ORDER BY createdAt")
    fun observeAttachments(itemId: String): Flow<List<VaultAttachmentEntity>>

    @Query("SELECT * FROM vault_attachments WHERE itemId = :itemId ORDER BY createdAt")
    suspend fun getAttachments(itemId: String): List<VaultAttachmentEntity>

    @Query("SELECT * FROM vault_attachments WHERE id = :id")
    suspend fun getAttachment(id: String): VaultAttachmentEntity?

    @Query("SELECT COUNT(*) FROM vault_attachments WHERE itemId = :itemId")
    suspend fun countAttachments(itemId: String): Int

    @Query("SELECT fileName FROM vault_attachments")
    suspend fun allFileNames(): List<String>

    @Insert
    suspend fun insertAttachment(attachment: VaultAttachmentEntity)

    @Query("DELETE FROM vault_attachments WHERE id = :id")
    suspend fun deleteAttachment(id: String)

    @Query("DELETE FROM vault_attachments")
    suspend fun deleteAllAttachments()

    @Query("DELETE FROM vault_items")
    suspend fun deleteAllItems()
}
