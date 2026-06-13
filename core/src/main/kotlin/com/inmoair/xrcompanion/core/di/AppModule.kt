package com.inmoair.xrcompanion.core.di

import android.content.Context
import com.inmoair.xrcompanion.core.auth.PairingManager
import com.inmoair.xrcompanion.core.command.*
import com.inmoair.xrcompanion.core.service.WebSocketServerManager
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule
// All dependencies are provided via @Inject constructors with @Singleton — no explicit @Provides needed.
