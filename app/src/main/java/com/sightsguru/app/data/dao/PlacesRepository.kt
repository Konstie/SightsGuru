package com.sightsguru.app.data.dao

import com.sightsguru.app.data.models.Place

interface PlacesRepository {
    suspend fun findAllPlaces(): HashMap<String, Place>
    fun findPlaceByTag(placesMap: HashMap<String, Place>, tag: String): Place?
}