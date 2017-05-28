package com.sightsguru.app.sections.recognizer.presenters

import com.sightsguru.app.data.models.Place

interface RecognitionView {
    fun onSpotRecognized(place: Place?)
    fun onOpenSpotDetails(spotTag: String?)
    fun onUpdateProgress(progressValue: Int)
}