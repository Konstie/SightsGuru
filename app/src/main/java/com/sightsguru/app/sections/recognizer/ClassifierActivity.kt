/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sightsguru.app.sections.recognizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ImageReader
import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import android.support.design.widget.BottomSheetBehavior
import android.view.View
import butterknife.BindView
import butterknife.ButterKnife
import com.sightsguru.app.R
import com.sightsguru.app.sections.base.BaseCameraActivity
import com.sightsguru.app.sections.recognizer.presenters.RecognitionPresenter
import com.sightsguru.app.sections.recognizer.presenters.RecognitionView
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.tensorflow.demo.OverlayView.DrawCallback

class ClassifierActivity : BaseCameraActivity(), ImageReader.OnImageAvailableListener, RecognitionView {

    private var classifier: org.tensorflow.demo.Classifier? = null

    private var sensorOrientation: Int? = null

    private var previewWidth = 0
    private var previewHeight = 0
    private lateinit var yuvBytes: Array<ByteArray?>
    private var rgbBytes: IntArray? = null
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null

    private var computing = false

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private var resultsView: org.tensorflow.demo.ResultsView? = null

    private var borderedText: org.tensorflow.demo.env.BorderedText? = null

    private var lastProcessingTimeMs: Long = 0

    private var presenter: RecognitionPresenter? = null

    @BindView(R.id.bottom_sheet_recognition_results) lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override val layoutId: Int
        get() = com.sightsguru.app.R.layout.camera_connection_fragment

    override val desiredPreviewFrameSize: Int
        get() = com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.INPUT_SIZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ButterKnife.bind(this)

        presenter = RecognitionPresenter()

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onPreviewSizeChosen(size: android.util.Size?, rotation: Int) {
        val textSizePx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.TEXT_SIZE_DIP, resources.displayMetrics)
        borderedText = org.tensorflow.demo.env.BorderedText(textSizePx)
        borderedText!!.setTypeface(android.graphics.Typeface.MONOSPACE)

        classifier = org.tensorflow.demo.TensorFlowImageClassifier.create(
                assets,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.MODEL_FILE,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.LABEL_FILE,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.INPUT_SIZE,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.IMAGE_MEAN,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.IMAGE_STD,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.INPUT_NAME,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.OUTPUT_NAME)

        resultsView = findViewById(com.sightsguru.app.R.id.results) as org.tensorflow.demo.ResultsView
        previewWidth = size!!.width
        previewHeight = size.height

        val display = windowManager.defaultDisplay
        val screenOrientation = display.rotation

        com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation)

        sensorOrientation = rotation + screenOrientation

        com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight)
        rgbBytes = IntArray(previewWidth * previewHeight)
        rgbFrameBitmap = android.graphics.Bitmap.createBitmap(previewWidth, previewHeight, android.graphics.Bitmap.Config.ARGB_8888)
        croppedBitmap = android.graphics.Bitmap.createBitmap(com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.INPUT_SIZE, com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.INPUT_SIZE, android.graphics.Bitmap.Config.ARGB_8888)

        frameToCropTransform = org.tensorflow.demo.env.ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.INPUT_SIZE, com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.INPUT_SIZE,
                sensorOrientation!!, com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.MAINTAIN_ASPECT)

        cropToFrameTransform = android.graphics.Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)

        yuvBytes = arrayOfNulls<ByteArray>(3)

        addCallback(
                DrawCallback { canvas -> renderDebug(canvas) })
    }

    override fun onImageAvailable(reader: android.media.ImageReader) {
        var image: android.media.Image? = null

        try {
            image = reader.acquireLatestImage()

            if (image == null) {
                return
            }

            if (computing) {
                image.close()
                return
            }
            computing = true

            android.os.Trace.beginSection("imageAvailable")

            val planes = image.planes
            fillBytes(planes, yuvBytes)

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            org.tensorflow.demo.env.ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    rgbBytes,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false)

            image.close()
        } catch (e: Exception) {
            if (image != null) {
                image.close()
            }
            com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.LOGGER.e(e, "Exception!")
            android.os.Trace.endSection()
            return
        }

        prepareImageForTensorFlow()

        runInBackground(
                Runnable {
                    processImageWithTensorFlow()
                })

        Trace.endSection()
    }

    private fun prepareImageForTensorFlow() {
        rgbFrameBitmap!!.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)

        // For examining the actual TF input.
        if (Companion.SAVE_PREVIEW_BITMAP) {
            org.tensorflow.demo.env.ImageUtils.saveBitmap(croppedBitmap)
        }
    }

    private fun processImageWithTensorFlow() {
        val startTime = SystemClock.uptimeMillis()
        val results = async(CommonPool) {
            LOGGER.d("Starting image recognition!")
            classifier!!.recognizeImage(croppedBitmap)
        }
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime

        cropCopyBitmap = android.graphics.Bitmap.createBitmap(croppedBitmap)

        // todo: show results in a bottom sheets
        runBlocking {
            LOGGER.d("Retrieved recognition results: " + results.await())
            resultsView!!.setResults(results.await())
        }
        requestRender()
        computing = false
    }

    override fun onSetDebug(debug: Boolean) {
        classifier!!.enableStatLogging(debug)
    }

    private fun renderDebug(canvas: android.graphics.Canvas) {
        if (!isDebug) {
            return
        }
        val copy = cropCopyBitmap
        if (copy != null) {
            val matrix = android.graphics.Matrix()
            val scaleFactor = 2f
            matrix.postScale(scaleFactor, scaleFactor)
            matrix.postTranslate(
                    canvas.width - copy.width * scaleFactor,
                    canvas.height - copy.height * scaleFactor)
            canvas.drawBitmap(copy, matrix, android.graphics.Paint())

            val lines = java.util.Vector<String>()
            if (classifier != null) {
                val statString = classifier!!.statString
                val statLines = statString.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                lines += statLines
            }

            lines.add("Frame: " + previewWidth + "x" + previewHeight)
            lines.add("Crop: " + copy.width + "x" + copy.height)
            lines.add("View: " + canvas.width + "x" + canvas.height)
            lines.add("Rotation: " + sensorOrientation!!)
            lines.add("Inference time: " + lastProcessingTimeMs + "ms")

            borderedText!!.drawLines(canvas, 10f, (canvas.height - 10).toFloat(), lines)
        }
    }

    companion object {
        private val LOGGER = org.tensorflow.demo.env.Logger()

        // These are the settings for the original v1 Inception model. If you want to
        // use a model that's been produced from the TensorFlow for Poets codelab,
        // you'll need to set IMAGE_SIZE = 299, IMAGE_MEAN = 128, IMAGE_STD = 128,
        // INPUT_NAME = "Mul:0", and OUTPUT_NAME = "final_result:0".
        // You'll also need to update the MODEL_FILE and LABEL_FILE paths to point to
        // the ones you produced.
        //
        // To use v3 Inception model, strip the DecodeJpeg Op from your retrained
        // model first:
        //
        // python strip_unused.py \
        // --input_graph=<retrained-pb-file> \
        // --output_graph=<your-stripped-pb-file> \
        // --input_node_names="Mul" \
        // --output_node_names="final_result" \
        // --input_binary=true
        private val INPUT_SIZE = 299
        private val IMAGE_MEAN = 128
        private val IMAGE_STD = 128f
        private val INPUT_NAME = "Mul"
        private val OUTPUT_NAME = "final_result"

        private val MODEL_FILE = "file:///android_asset/retrained_graph_optimized.pb"
        private val LABEL_FILE = "file:///android_asset/retrained_labels.txt"

        private val SAVE_PREVIEW_BITMAP = false

        private val MAINTAIN_ASPECT = true

        private val TEXT_SIZE_DIP = 10f
    }
}
