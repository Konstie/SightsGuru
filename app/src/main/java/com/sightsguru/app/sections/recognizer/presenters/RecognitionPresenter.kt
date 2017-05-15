package com.sightsguru.app.sections.recognizer.presenters

import android.util.Log
import com.google.firebase.database.*
import com.sightsguru.app.data.models.Place
import java.util.*
import kotlin.collections.HashMap

class RecognitionPresenter {
    companion object {
        private val EXPERIMENTS_COUNT_LIMIT = 5
        private val DATABASE_NAME = "sights"
    }

    private var view: RecognitionView? = null
    private var recognitionEntropyList: MutableList<String> = ArrayList()
    private var database: FirebaseDatabase? = null
    private var databaseRef: DatabaseReference? = null

    init {
        database = FirebaseDatabase.getInstance()
        databaseRef = database?.getReference(DATABASE_NAME)
    }

    fun onResultsRetrieved(results: List<org.tensorflow.demo.Classifier.Recognition>) {
        Log.d("Presenter", "onResultsRetrieved!")
        if (recognitionEntropyList.size == EXPERIMENTS_COUNT_LIMIT) {
            Log.d("Presenter", "Experiments count limit reached!")
            defineMaxPossibleResult()
            return
        }
        val mostPossibleResult = if (results.isNotEmpty()) results[0].title else null
        Log.d("Presenter", "Most possible result: " + mostPossibleResult)
        if (mostPossibleResult != null) {
            recognitionEntropyList.add(mostPossibleResult)
        }
    }

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
        val mostPossibleResult = possibilitySortedResults[0]
        Log.d("Presenter", "Most possible result: " + mostPossibleResult)
        loadRecognitionResultData(mostPossibleResult)
    }

    private fun loadRecognitionResultData(mostPossibleResult: String) {
        var recognizedSightSeeing: Place? = null
        databaseRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot?) {
                recognizedSightSeeing = dataSnapshot?.child(mostPossibleResult)?.getValue(Place::class.java)
                Log.d("Presenter", "Place: " + recognizedSightSeeing.toString())
                view?.onSpotRecognized(recognizedSightSeeing)
            }
        })
    }

    fun attachView(view: RecognitionView) {
        this.view = view
    }

    fun detachView() {
        this.view = null
    }
}
