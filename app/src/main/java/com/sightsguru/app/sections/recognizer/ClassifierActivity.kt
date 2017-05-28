package com.sightsguru.app.sections.recognizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.Trace
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityOptionsCompat
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import com.bumptech.glide.Glide
import com.sightsguru.app.R
import com.sightsguru.app.data.models.Place
import com.sightsguru.app.sections.base.BaseCameraActivity
import com.sightsguru.app.sections.recognizer.presenters.RecognitionPresenter
import com.sightsguru.app.sections.recognizer.presenters.RecognitionView
import com.sightsguru.app.utils.IntentHelper
import com.sightsguru.app.utils.ViewUtils
import com.wang.avi.AVLoadingIndicatorView
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.tensorflow.demo.OverlayView.DrawCallback
import org.tensorflow.demo.env.ImageUtils

class ClassifierActivity : BaseCameraActivity(), ImageReader.OnImageAvailableListener, View.OnClickListener, RecognitionView {
    private val BOTTOM_SHEET_HEIGHT = 124

    private var sensorOrientation: Int? = null
    private var lastProcessingTimeMs: Long = 0
    private var computing = false
    private @Volatile var imageRecognized = false
    private var previewWidth = 0
    private var previewHeight = 0
    private var rgbBytes: IntArray? = null
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private lateinit var yuvBytes: Array<ByteArray?>

    private var borderedText: org.tensorflow.demo.env.BorderedText? = null
    private var presenter: RecognitionPresenter? = null
    private var classifier: org.tensorflow.demo.Classifier? = null

    @BindView(R.id.bottom_sheet_recognition_results) lateinit var bottomSheet: FrameLayout
    @BindView(R.id.layout_details_root) lateinit var layoutPlaceDetails: RelativeLayout
    @BindView(R.id.title_text_view) lateinit var titleTextView: TextView
    @BindView(R.id.address_text_view) lateinit var addressTextView: TextView
    @BindView(R.id.preview_image_view) lateinit var previewImageView: ImageView
    @BindView(R.id.btn_show_more) lateinit var buttonShowMore: TextView
    @BindView(R.id.btn_refresh) lateinit var buttonRefresh: FloatingActionButton
    @BindView(R.id.progress_bar) lateinit var progressIndicator: AVLoadingIndicatorView
    @BindView(R.id.progress_text_view) lateinit var progressTextView: TextView

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override val layoutId: Int
        get() = R.layout.camera_connection_fragment

    override val desiredPreviewFrameSize: Int
        get() = INPUT_SIZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ButterKnife.bind(this)

        presenter = RecognitionPresenter()

        setupPlaceInfoSheet()

        buttonRefresh.setOnClickListener(this)
        buttonShowMore.setOnClickListener(this)
        layoutPlaceDetails.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v) {
            buttonRefresh -> onRefreshPressed()
            buttonShowMore, layoutPlaceDetails -> presenter?.onRequestPlaceDetails()
        }
    }

    private fun setupPlaceInfoSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.setBottomSheetCallback(bottomSheetCallback)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet.setOnClickListener {
            presenter?.onRequestPlaceDetails()
        }
    }

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {}

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    buttonRefresh.animate().scaleX(0f).scaleY(0f).duration = 250
                }
                BottomSheetBehavior.STATE_EXPANDED -> {
                    buttonRefresh.animate().scaleX(1f).scaleY(1f).duration = 250
                    bottomSheetBehavior.peekHeight = ViewUtils.dpToPx(resources, BOTTOM_SHEET_HEIGHT)
                }
            }
        }
    }

    private fun onRefreshPressed() {
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        imageRecognized = false
        presenter?.onResetRecognitionResult()
    }

    override fun onOpenSpotDetails(spotTag: String?) {
        if (spotTag != null) {
            val intent = IntentHelper.getDetailsActivityIntent(this@ClassifierActivity, spotTag)
            val activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(this, previewImageView as View, "poster")
            startActivity(intent, activityOptions.toBundle());
        }
    }

    @Synchronized override fun onStart() {
        super.onStart()
        presenter?.attachView(this@ClassifierActivity)
        if (allPermissionsGranted()) {
            presenter?.retrieveCurrentLocation()
        }
        if (progressIndicator.visibility != View.VISIBLE) {
            showProgressData()
        }
    }

    override fun onPreviewSizeChosen(size: android.util.Size?, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, com.sightsguru.app.sections.recognizer.ClassifierActivity.Companion.TEXT_SIZE_DIP, resources.displayMetrics)
        borderedText = org.tensorflow.demo.env.BorderedText(textSizePx)
        borderedText?.setTypeface(android.graphics.Typeface.MONOSPACE)

        classifier = org.tensorflow.demo.TensorFlowImageClassifier.create(
                assets,
                MODEL_FILE,
                LABEL_FILE,
                INPUT_SIZE,
                IMAGE_MEAN,
                IMAGE_STD,
                INPUT_NAME,
                OUTPUT_NAME)

        previewWidth = size?.width ?: 0
        previewHeight = size?.height ?: 0

        val display = windowManager.defaultDisplay
        val screenOrientation = display.rotation

        LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation)

        sensorOrientation = rotation + screenOrientation

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight)
        rgbBytes = IntArray(previewWidth * previewHeight)
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(ClassifierActivity.Companion.INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)

        frameToCropTransform = org.tensorflow.demo.env.ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                INPUT_SIZE, INPUT_SIZE,
                sensorOrientation ?: 0, MAINTAIN_ASPECT)

        cropToFrameTransform = android.graphics.Matrix()
        frameToCropTransform?.invert(cropToFrameTransform)

        yuvBytes = arrayOfNulls<ByteArray>(3)

        addCallback(object : DrawCallback {
            override fun drawCallback(canvas: Canvas) {
                renderDebug(canvas)
            }
        })
    }

    override fun onPermissionGranted() {
        setFragment()
    }

    override fun onImageAvailable(reader: ImageReader) {
        var image: android.media.Image? = null

        try {
            image = reader.acquireLatestImage()

            if (image == null) {
                return
            }

            if (computing || imageRecognized) {
                image.close()
                return
            }
            computing = true

            Trace.beginSection("imageAvailable")

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
            LOGGER.e(e, "Exception!")
            android.os.Trace.endSection()
            return
        }

        prepareImageForTensorFlow()

        runInBackground(Runnable { processImageWithTensorFlow() })

        Trace.endSection()
    }

    private fun prepareImageForTensorFlow() {
        rgbFrameBitmap?.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)
        val canvas = Canvas(croppedBitmap)
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null)

        // For examining the actual TF input.
        if (Companion.SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap)
        }
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    private fun processImageWithTensorFlow() {
        val startTime = SystemClock.uptimeMillis()
        val results = async(CommonPool) {
            LOGGER.d("Starting image recognition!")
            classifier?.recognizeImage(croppedBitmap)
        }
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime

        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap)

        runBlocking {
            presenter?.onResultsRetrieved(results.await() ?: ArrayList())
        }
        requestRender()
        computing = false
    }

    private fun showProgressData() {
        progressIndicator.smoothToShow()
        progressTextView.visibility = View.VISIBLE
        progressTextView.alpha = 0f
        progressTextView.animate().setDuration(250).alpha(1f)
        updateProgressValue(0)
    }

    override fun onUpdateProgress(progressValue: Int) {
        updateProgressValue(progressValue)

    }

    private fun updateProgressValue(progressValue: Int) {
        runOnUiThread {
            progressTextView.text = getString(R.string.camera_recognition_progress, progressValue)
        }
    }

    override fun onSetDebug(debug: Boolean) {
        classifier?.enableStatLogging(debug)
    }

    override fun onSpotRecognized(place: Place?) {
        runOnUiThread {
            imageRecognized = true

            progressIndicator.smoothToHide()
            progressTextView.animate().setDuration(250).alpha(0f)
            titleTextView.text = place?.title
            addressTextView.text = place?.address
            if (place != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            LOGGER.d("Image URL: ${place?.imageUrl}")
            Glide.with(this@ClassifierActivity)
                    .load(place?.imageUrl)
                    .into(previewImageView)
        }
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
                val statString = classifier?.statString
                val statLines = statString?.split("\n".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
                lines += statLines ?: arrayOf("")
            }

            lines.add("Frame: " + previewWidth + "x" + previewHeight)
            lines.add("Crop: " + copy.width + "x" + copy.height)
            lines.add("View: " + canvas.width + "x" + canvas.height)
            lines.add("Rotation: " + sensorOrientation)
            lines.add("Inference time: " + lastProcessingTimeMs + "ms")

            borderedText?.drawLines(canvas, 10f, (canvas.height - 10).toFloat(), lines)
        }
    }

    @Synchronized public override fun onResume() {
        super.onResume()

        handlerThread = android.os.HandlerThread("inference")
        handlerThread?.start()
        handler = Handler(handlerThread?.looper)
    }

    @Synchronized public override fun onPause() {
        LOGGER.d("onPause " + this)

        if (!isFinishing) {
            LOGGER.d("Requesting finish")
            finish()
        }

        handlerThread?.quitSafely()
        try {
            handlerThread?.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            LOGGER.e(e, "Exception!")
        }

        super.onPause()
    }

    @Synchronized public override fun onStop() {
        presenter?.detachView()
        super.onStop()
    }

    @Synchronized public override fun onDestroy() {
        bottomSheetBehavior.setBottomSheetCallback(null)
        super.onDestroy()
    }

    companion object {
        private val LOGGER = org.tensorflow.demo.env.Logger()

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
