package com.sightsguru.app.sections.recognizer.presenters

import java.util.*
import kotlin.collections.HashMap

class RecognitionPresenter {
    companion object {
        val EXPERIMENTS_COUNT_LIMIT = 5
    }

    private var view: RecognitionView? = null
    private var recognitionEntropyList: MutableList<String> = ArrayList()

    fun onResultsRetrieved(results: List<org.tensorflow.demo.Classifier.Recognition>) {
        if (recognitionEntropyList.size == EXPERIMENTS_COUNT_LIMIT) {
            defineMaxPossibleResult()
            return
        }
        val mostPossibleResult = results.sortedBy { it.confidence }.take(0)[0].title
        recognitionEntropyList.add(mostPossibleResult)
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
        loadRecognitionResultData(mostPossibleResult)
    }

    private fun loadRecognitionResultData(mostPossibleResult: String) {

    }

    fun attachView(view: RecognitionView) {
        this.view = view
    }

    fun detachView() {
        this.view = null
    }
}