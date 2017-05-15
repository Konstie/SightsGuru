package com.sightsguru.app.data.models

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName
import java.io.Serializable

@IgnoreExtraProperties
data class Place(var address: String, var description: String,
                 var lat: Double, var long: Double, var title: String,
                 @PropertyName("wiki_url") var wikiUrl: String,
                 @PropertyName("image_url") var imageUrl: String,
                 var year: Int) : Serializable {
    constructor(): this("", "", 0.0, 0.0, "", "", "", 0)
}