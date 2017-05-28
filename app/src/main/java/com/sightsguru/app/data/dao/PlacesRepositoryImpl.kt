package com.sightsguru.app.data.dao

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.sightsguru.app.data.models.Place
import kotlin.coroutines.experimental.suspendCoroutine

class PlacesRepositoryImpl(private var databaseRef: DatabaseReference) : PlacesRepository {
    private @Volatile var placesMap: HashMap<String, Place> = HashMap()

    override fun findPlaceByTag(placesMap: HashMap<String, Place>, tag: String): Place? {
        return placesMap[tag]
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    override suspend fun findAllPlaces(): HashMap<String, Place> = suspendCoroutine { continuation ->
        if (placesMap.isNotEmpty()) {
            continuation.resume(placesMap)
            return@suspendCoroutine
        }
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {
                continuation.resumeWithException(databaseError?.toException()?.cause ?:
                        Throwable("Could not read results from database"))
            }

            override fun onDataChange(dataSnapshot: DataSnapshot?) {
                dataSnapshot?.children?.forEach {
                    val place = it.getValue(Place::class.java)
                    placesMap.put(it.key, place)
                }
                continuation.resume(placesMap)
            }
        })
    }
}