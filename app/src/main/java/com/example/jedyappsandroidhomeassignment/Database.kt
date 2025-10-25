package com.example.jedyappsandroidhomeassignment

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteMovie(
    @PrimaryKey val imdbID: String,
    val title: String,
    val poster: String?,
    val type: String?
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY title ASC")
    fun getAll(): Flow<List<FavoriteMovie>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(movie: FavoriteMovie)

    @Query("DELETE FROM favorites WHERE imdbID = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE imdbID = :id)")
    suspend fun exists(id: String): Boolean
}

@Database(entities = [FavoriteMovie::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): FavoriteDao

    companion object {
        @Volatile private var INSTANCE: Db? = null
        fun get(ctx: Context): Db =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    Db::class.java,
                    "favorites.db"
                ).build().also { INSTANCE = it }
            }
    }
}