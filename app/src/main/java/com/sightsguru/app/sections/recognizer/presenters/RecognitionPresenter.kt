package com.sightsguru.app.sections.recognizer.presenters

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import com.sightsguru.app.InstantSightsApp
import com.sightsguru.app.data.dao.PlacesRepository
import com.sightsguru.app.data.models.Place
import com.sightsguru.app.sections.recognizer.listeners.LocationChangeListener
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class RecognitionPresenter {
    companion object {
        private val EXPERIMENTS_COUNT_LIMIT = 3
        private val PROGRESS_STEP = 20
    }

    private var progressValue = 0
    private var locationProviderEnabled = false
    private var location: Location? = null
    private var view: RecognitionView? = null
    private var recognitionEntropyList: MutableList<String> = ArrayList()
    private @Volatile var recognizedPlace: Place? = null
    @Inject lateinit var placesRepository: PlacesRepository
    @Inject lateinit var locationManager: LocationManager

    init {
        InstantSightsApp.databaseComponent.inject(this)
    }

    fun retrieveCurrentLocation() {
        locationProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 1000f, locationChangeListener)
    }

    private val locationChangeListener = object : LocationListener by LocationChangeListener {
        override fun onLocationChanged(location: Location?) {
            this@RecognitionPresenter.location = location
            updateProgress()
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
        updateProgress()
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    private fun defineMaxPossibleResult() {
        val possibilitySortedResults = getPossibilitySortedResultsAsList()

        val placesMapTask = async(CommonPool) { placesRepository.findAllPlaces() }
        val mostPossibleResult = possibilitySortedResults[0]

        runBlocking {
            val allPlacesMap = placesMapTask.await()
            val resultPlacesMap = HashMap(allPlacesMap.filter { recognitionEntropyList.contains(it.key) })
            Log.d("Presenter", "Result places map ${resultPlacesMap.keys}")
            val resultPlacesList = ArrayList(resultPlacesMap.values)
            if (locationProviderEnabled) {
                retrieveClosestPlace(resultPlacesList)
            } else {
                recognizedPlace = placesRepository.findPlaceByTag(resultPlacesMap, mostPossibleResult)
                updateProgress()
                view?.onSpotRecognized(recognizedPlace)
            }
        }
    }

    private fun getPossibilitySortedResultsAsList(): List<String> {
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
        return possibilitySortedResults
    }

    private fun retrieveClosestPlace(resultPlacesList: MutableList<Place>) {
        Log.d("Presenter", "Retrieving closest place ${resultPlacesList.size}")
        var closestDistance: Double = Double.MAX_VALUE
        resultPlacesList.forEach {
            val distance = getDistanceBetweenSpots(location?.latitude?: 0.0, location?.longitude?: 0.0, it.lat, it.lng)
            if (distance < closestDistance) {
                closestDistance = distance
                recognizedPlace = it
            }
        }
        updateProgress()
        view?.onSpotRecognized(recognizedPlace)
    }

    private fun getDistanceBetweenSpots(myLat: Double, myLong: Double, spotLat: Double, spotLong: Double): Double {
        return Math.sqrt(Math.pow(spotLat - myLat, 2.0)
                + Math.pow(spotLong - myLong, 2.0))
    }

    fun onRequestPlaceDetails() {
        view?.onOpenSpotDetails(recognizedPlace?.tag)
    }

    private fun stopLocationTracking() {
        locationManager.removeUpdates(locationChangeListener)
    }

    private fun updateProgress() {
        progressValue += PROGRESS_STEP
        if (progressValue > 100) {
            progressValue = 100
        }
        view?.onUpdateProgress(progressValue)
    }

    fun onResetRecognitionResult() {
        recognitionEntropyList.clear()
        progressValue = 0
    }

    fun attachView(view: RecognitionView) {
        this.view = view
    }

    fun detachView() {
        stopLocationTracking()
        this.view = null
    }
}
