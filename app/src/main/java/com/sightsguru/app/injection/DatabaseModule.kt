package com.sightsguru.app.injection

import android.content.Context
import android.location.LocationManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.sightsguru.app.InstantSightsApp
import com.sightsguru.app.data.dao.PlacesRepository
import com.sightsguru.app.data.dao.PlacesRepositoryImpl
import com.sightsguru.app.sections.details.presenters.DetailsPresenter
import com.sightsguru.app.sections.recognizer.presenters.RecognitionPresenter
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DatabaseModule(private val application: InstantSightsApp) {
    private val DATABASE_NAME = "sights"

    @Provides
    @Singleton
    fun provideContext(): Context {
        return application
    }

    @Provides
    @Singleton
    fun provideLocationManager(context: Context): LocationManager {
        return context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Provides
    @Singleton
    fun provideDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance()
    }

    @Provides
    @Singleton
    fun provideDatabaseRef(database: FirebaseDatabase): DatabaseReference {
        return database.getReference(DATABASE_NAME)
    }

    @Provides
    @Singleton
    fun providePlacesRepository(databaseRef: DatabaseReference): PlacesRepository {
        return PlacesRepositoryImpl(databaseRef)
    }
}

@Singleton
@Component(modules = arrayOf(DatabaseModule::class))
interface DatabaseComponent {
    fun inject(recognitionPresenter: RecognitionPresenter)
    fun inject(detailsPresenter: DetailsPresenter)
    fun locationManager(context: Context)
    fun databaseRef(): DatabaseReference
    fun placesRepository(): PlacesRepository
}