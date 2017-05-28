package com.sightsguru.app.sections.details.presenters

import com.sightsguru.app.InstantSightsApp
import com.sightsguru.app.data.dao.PlacesRepository
import com.sightsguru.app.data.models.Place
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import javax.inject.Inject

class DetailsPresenter(private val tag: String?) {
    private var place: Place? = null
    private var detailsView: DetailsView? = null
    @Inject lateinit var placesRepository: PlacesRepository

    init {
        InstantSightsApp.databaseComponent.inject(this)
    }

    fun fetchPlaceInfo() {
        val placeRetrievalTask = async(CommonPool) { placesRepository.findAllPlaces() }

        runBlocking {
            val placesMap = placeRetrievalTask.await()
            if (tag != null) {
                place = placesRepository.findPlaceByTag(placesMap, tag)
                launch(UI) { setupPlaceInfoOnScreen(place) }
            } else {
                launch(UI) { detailsView?.onPlaceNotFound() }
            }
        }
    }

    private fun setupPlaceInfoOnScreen(place: Place?) {
        detailsView?.onCreatePlacePoster(place?.imageUrl)
        detailsView?.onCreatePlaceExternalLink(place?.wikiUrl)
        detailsView?.onShowPlaceDetails(place?.title, place?.address, place?.year)
        detailsView?.onShowPlaceDescription(place?.description)
        detailsView?.onLocationDataReady()
    }

    fun setupGeoData() {
        detailsView?.onShowMapView(place?.lat ?: 0.0, place?.lng ?: 0.0)
    }

    fun attachView(view: DetailsView) {
        this.detailsView = view
    }

    fun detachView() {
        this.detailsView = null
    }
}