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

package com.sightsguru.app.sections.base

import android.media.ImageReader
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Size
import com.sightsguru.app.R
import com.sightsguru.app.sections.recognizer.CameraConnectionFragment

abstract class BaseCameraActivity : AppCompatActivity(), ImageReader.OnImageAvailableListener {

    var isDebug = false
        private set

    private var handler: Handler? = null
    private var handlerThread: android.os.HandlerThread? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        LOGGER.d("onCreate " + this)
        super.onCreate(null)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)

        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }
    }

    @Synchronized public override fun onStart() {
        LOGGER.d("onStart " + this)
        super.onStart()
    }

    @Synchronized public override fun onResume() {
        LOGGER.d("onResume " + this)
        super.onResume()

        handlerThread = android.os.HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized public override fun onPause() {
        LOGGER.d("onPause " + this)

        if (!isFinishing) {
            LOGGER.d("Requesting finish")
            finish()
        }

        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            LOGGER.e(e, "Exception!")
        }

        super.onPause()
    }

    @Synchronized public override fun onStop() {
        LOGGER.d("onStop " + this)
        super.onStop()
    }

    @Synchronized public override fun onDestroy() {
        LOGGER.d("onDestroy " + this)
        super.onDestroy()
    }

    @Synchronized protected fun runInBackground(r: Runnable) {
        if (handler != null) {
            handler!!.post(r)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST -> {
                if (grantResults.isNotEmpty()
                        && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    setFragment()
                } else {
                    requestPermission()
                }
            }
        }
    }

    private fun hasPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            return true
        }
    }

    private fun requestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                android.widget.Toast.makeText(this@BaseCameraActivity, "Camera AND storage permission are required for this demo", android.widget.Toast.LENGTH_LONG).show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA, PERMISSION_STORAGE), PERMISSIONS_REQUEST)
        }
    }

    protected fun setFragment() {
        val fragment = CameraConnectionFragment.newInstance(
                object : CameraConnectionFragment.ConnectionCallback {
                    override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                        this@BaseCameraActivity.onPreviewSizeChosen(size, cameraRotation)
                    }
                }, this, layoutId, desiredPreviewFrameSize)

        fragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
    }

    protected fun fillBytes(planes: Array<android.media.Image.Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity())
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i])
        }
    }

    fun requestRender() {
        val overlay = findViewById(R.id.debug_overlay) as org.tensorflow.demo.OverlayView
        overlay.postInvalidate()
    }

    fun addCallback(callback: org.tensorflow.demo.OverlayView.DrawCallback) {
        val overlay = findViewById(R.id.debug_overlay) as org.tensorflow.demo.OverlayView
        overlay.addCallback(callback)
    }

    open fun onSetDebug(debug: Boolean) {}

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            isDebug = !isDebug
            requestRender()
            onSetDebug(isDebug)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    protected abstract fun onPreviewSizeChosen(size: Size?, rotation: Int)
    protected abstract val layoutId: Int
    protected abstract val desiredPreviewFrameSize: Int

    companion object {
        private val LOGGER = org.tensorflow.demo.env.Logger()

        private val PERMISSIONS_REQUEST = 1

        private val PERMISSION_CAMERA = android.Manifest.permission.CAMERA
        private val PERMISSION_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
}
