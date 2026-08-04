package com.securechat.app.di

import android.content.Context
import androidx.room.Room
import com.securechat.app.data.local.AppDatabase
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.local.GroupDao
import com.securechat.app.data.local.GroupSenderKeyDao
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.local.MusicMetadataDao
import com.securechat.app.data.local.PollDao
import com.securechat.app.data.local.ProfileManager
import com.securechat.app.data.local.StatusDao
import com.securechat.app.data.local.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val dbName = ProfileManager.dbName(context)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_25_26, AppDatabase.MIGRATION_26_27, AppDatabase.MIGRATION_27_28, AppDatabase.MIGRATION_28_29, AppDatabase.MIGRATION_29_30, AppDatabase.MIGRATION_30_31, AppDatabase.MIGRATION_31_32, AppDatabase.MIGRATION_32_33, AppDatabase.MIGRATION_33_34, AppDatabase.MIGRATION_34_35, AppDatabase.MIGRATION_35_36, AppDatabase.MIGRATION_36_37, AppDatabase.MIGRATION_37_38)
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideContactDao(database: AppDatabase): ContactDao {
        return database.contactDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideStatusDao(database: AppDatabase): StatusDao {
        return database.statusDao()
    }

    @Provides
    @Singleton
    fun providePollDao(database: AppDatabase): PollDao {
        return database.pollDao()
    }

    @Provides
    @Singleton
    fun provideGroupDao(database: AppDatabase): GroupDao = database.groupDao()

    @Provides
    @Singleton
    fun provideGroupSenderKeyDao(database: AppDatabase): GroupSenderKeyDao = database.groupSenderKeyDao()

    @Provides
    @Singleton
    fun provideMusicMetadataDao(database: AppDatabase): MusicMetadataDao = database.musicMetadataDao()
}
