package com.sightsguru.app.sections.recognizer.presenters

class RecognitionPresenter {
    private var view: RecognitionView? = null
    private

    fun setupRecognitionResult(results: List<org.tensorflow.demo.Classifier.Recognition>) {

    }

    fun attachView(view: RecognitionView) {
        this.view = view
    }

    fun detachView() {
        this.view = null
    }
}