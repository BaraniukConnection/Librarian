package com.oxygen.librarian.di

import android.content.Context
import androidx.room.Room
import com.oxygen.librarian.data.BookRepository
import com.oxygen.librarian.data.db.BookDao
import com.oxygen.librarian.data.db.BookmarkDao
import com.oxygen.librarian.data.db.HighlightDao
import com.oxygen.librarian.data.db.LibraryDatabase
import com.oxygen.librarian.data.prefs.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, "librarian.db").build()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context) = AppPreferences(context)

    @Provides
    @Singleton
    fun repository(@ApplicationContext context: Context, books: BookDao) = BookRepository(context, books)

    /** Для записів, що мають пережити екран: місце читання зберігається й тоді, коли з книги вже вийшли. */
    @Provides
    @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides fun books(db: LibraryDatabase): BookDao = db.books()
    @Provides fun bookmarks(db: LibraryDatabase): BookmarkDao = db.bookmarks()
    @Provides fun highlights(db: LibraryDatabase): HighlightDao = db.highlights()
}
