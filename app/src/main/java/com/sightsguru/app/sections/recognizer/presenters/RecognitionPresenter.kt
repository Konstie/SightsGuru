package com.sightsguru.app.sections.recognizer.presenters

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import com.google.firebase.database.*
import com.sightsguru.app.data.models.Place
import com.sightsguru.app.sections.recognizer.listeners.LocationChangeListener
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.experimental.suspendCoroutine

class RecognitionPresenter {
    companion object {
        private val EXPERIMENTS_COUNT_LIMIT = 3
        private val DATABASE_NAME = "sights"
    }

    private var locationProviderEnabled = false
    private var location: Location? = null
    private var locationManager: LocationManager? = null
    private var view: RecognitionView? = null
    private var recognitionEntropyList: MutableList<String> = ArrayList()
    private var database: FirebaseDatabase? = null
    private var databaseRef: DatabaseReference? = null

    init {
        database = FirebaseDatabase.getInstance()
        databaseRef = database?.getReference(DATABASE_NAME)
    }

    fun retrieveCurrentLocation(context: Context?) {
        val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 1000f, locationChangeListener)
    }

    private val locationChangeListener = object : LocationListener by LocationChangeListener {
        override fun onLocationChanged(location: Location?) {
            this@RecognitionPresenter.location = location
        }
    }

    fun onResultsRetrieved(results: List<org.tensorflow.demo.Classifier.Recognition>) {
        Log.d("Presenter", "onResultsRetrieved!")
        if (recognitionEntropyList.size == EXPERIMENTS_COUNT_LIMIT) {
            Log.d("Presenter", "Experiments count limit reached!")
            defineMaxPossibleResult()
            return
        }
        val mostPossibleResult = if (results.isNotEmpty()) results[0].title else null
        if (mostPossibleResult != null) {
            recognitionEntropyList.add(mostPossibleResult)
        }
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    private fun defineMaxPossibleResult() {
        val frequenciesMap = recognitionEntropyList.fold(HashMap<String, Int>(), {
            frequenciesMap, item ->
            if (frequenciesMap.containsKey(item)) {
                var entriesCount = frequenciesMap[item] ?: 0
                frequenciesMap.put(item, ++entriesCount)
            } else frequenciesMap.put(item, 1); frequenciesMap
        })
        val possibilitySortedResults = frequenciesMap.toSortedMap(comparator = kotlin.Comparator { key, anotherKey ->
            frequenciesMap[key]?:0.compareTo(frequenciesMap[anotherKey]?:0)
        }).keys.toList()

        val placesMap = async(CommonPool) { getAllPlaces() }
        val mostPossibleResult = possibilitySortedResults[0]

        runBlocking {
            val allPlacesMap = placesMap.await()
            val resultPlacesMap = allPlacesMap.filter { recognitionEntropyList.contains(it.key) }
            Log.d("Presenter", "Result places map ${resultPlacesMap.keys}")
            val resultPlacesList = ArrayList(resultPlacesMap.values)
            if (locationProviderEnabled) {
                retrieveClosestPlace(resultPlacesList)
            } else {
                view?.onSpotRecognized(allPlacesMap[mostPossibleResult])
            }
        }
    }

    private fun retrieveClosestPlace(resultPlacesList: MutableList<Place>) {
        Log.d("Presenter", "Retrieving closest place ${resultPlacesList.size}")
        var closestPlace: Place? = null
        var closestDistance: Double = Double.MAX_VALUE
        resultPlacesList.forEach {
            val distance = getDistanceBetweenSpots(location?.latitude?: 0.0, location?.longitude?: 0.0, it.lat, it.lng)
            if (distance < closestDistance) {
                closestDistance = distance
                closestPlace = it
            }
        }
        view?.onSpotRecognized(closestPlace)
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    suspend fun getAllPlaces(): HashMap<String, Place> = suspendCoroutine { continuation ->
            val placesMap: HashMap<String, Place> = HashMap()
            databaseRef?.addListenerForSingleValueEvent(object : ValueEventListener {
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

    private fun getDistanceBetweenSpots(myLat: Double, myLong: Double, spotLat: Double, spotLong: Double): Double {
        return Math.sqrt(Math.pow(spotLat - myLat, 2.0)
                + Math.pow(spotLong - myLong, 2.0))
    }

    private fun stopLocationTracking() {
        locationManager?.removeUpdates(locationChangeListener)
    }

    fun onResetRecognitionResult() {
        recognitionEntropyList.clear()
    }

    fun attachView(view: RecognitionView) {
        this.view = view
    }

    fun detachView() {
        stopLocationTracking()
        this.view = null
    }
}
