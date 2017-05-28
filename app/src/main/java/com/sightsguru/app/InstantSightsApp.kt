package com.sightsguru.app

import android.app.Application
import com.sightsguru.app.injection.DaggerDatabaseComponent
import com.sightsguru.app.injection.DatabaseComponent
import com.sightsguru.app.injection.DatabaseModule

class InstantSightsApp : Application() {
    companion object {
        lateinit var databaseComponent: DatabaseComponent
    }

    override fun onCreate() {
        super.onCreate()
        databaseComponent = DaggerDatabaseComponent.builder()
                .databaseModule(DatabaseModule(this))
                .build()
    }
}
