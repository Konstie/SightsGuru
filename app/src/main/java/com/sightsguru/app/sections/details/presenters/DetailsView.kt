package com.sightsguru.app.sections.details.presenters

interface DetailsView {
    fun onCreatePlacePoster(posterUrl: String?)
    fun onCreatePlaceExternalLink(externalUrl: String?)
    fun onPlaceNotFound()
    fun onShowPlaceDetails(placeTitle: String?, placeAddress: String?, yearOfEst: Int?)
    fun onShowPlaceDescription(placeDescription: String?)
    fun onLocationDataReady()
    fun onShowMapView(latitude: Double, longitude: Double)
}