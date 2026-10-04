package com.saravana.localtracklogger.di

import android.content.Context
import com.saravana.localtracklogger.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context) = TrackDatabase.create(context)
    @Provides fun dao(db: TrackDatabase) = db.trackDao()
    @Provides @Singleton fun repository(dao: TrackDao) = TrackRepository(dao)
}
