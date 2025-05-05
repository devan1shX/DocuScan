package com.example.mc.home_fragment.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ScannedDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: ScannedDocument)

    @Update
    suspend fun update(document: ScannedDocument)

    @Query("SELECT * FROM scanned_documents ORDER BY scanTimestamp DESC")
    fun getAllDocuments(): LiveData<List<ScannedDocument>>

    @Query("DELETE FROM scanned_documents WHERE id = :docId")
    suspend fun deleteById(docId: Int)

    @Query("SELECT * FROM scanned_documents WHERE id = :docId")
    suspend fun getDocumentById(docId: Int): ScannedDocument?

    @Query("DELETE FROM scanned_documents")
    suspend fun deleteAll()
}