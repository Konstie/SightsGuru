/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.sightsguru.app.sections.recognizer

class RecognitionScoreView(context: android.content.Context, set: android.util.AttributeSet) : android.view.View(context, set), org.tensorflow.demo.ResultsView {
    private var results: List<org.tensorflow.demo.Classifier.Recognition>? = null
    private val textSizePx: Float
    private val fgPaint: android.graphics.Paint
    private val bgPaint: android.graphics.Paint

    init {
        textSizePx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, com.sightsguru.app.sections.recognizer.RecognitionScoreView.Companion.TEXT_SIZE_DIP, resources.displayMetrics)
        fgPaint = android.graphics.Paint()
        fgPaint.textSize = textSizePx

        bgPaint = android.graphics.Paint()
        bgPaint.color = 0xcc4285f4.toInt()
    }

    override fun setResults(results: List<org.tensorflow.demo.Classifier.Recognition>) {
        this.results = results
        postInvalidate()
    }

    public override fun onDraw(canvas: android.graphics.Canvas) {
        val x = 10
        var y = (fgPaint.textSize * 1.5f).toInt()

        canvas.drawPaint(bgPaint)

        if (results != null) {
            for (recog in results!!) {
                canvas.drawText(recog.title + ": " + recog.confidence, x.toFloat(), y.toFloat(), fgPaint)
                y += (fgPaint.textSize * 1.5f).toInt()
            }
        }
    }

    companion object {
        private val TEXT_SIZE_DIP = 24f
    }
}
