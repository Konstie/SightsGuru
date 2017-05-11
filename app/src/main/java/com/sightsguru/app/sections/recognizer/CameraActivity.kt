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

import android.util.Size

abstract class CameraActivity : android.app.Activity(), android.media.ImageReader.OnImageAvailableListener {

    var isDebug = false
        private set

    private var handler: android.os.Handler? = null
    private var handlerThread: android.os.HandlerThread? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("onCreate " + this)
        super.onCreate(null)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(com.sightsguru.app.R.layout.activity_camera)

        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }
    }

    @Synchronized public override fun onStart() {
        com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("onStart " + this)
        super.onStart()
    }

    @Synchronized public override fun onResume() {
        com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("onResume " + this)
        super.onResume()

        handlerThread = android.os.HandlerThread("inference")
        handlerThread!!.start()
        handler = android.os.Handler(handlerThread!!.looper)
    }

    @Synchronized public override fun onPause() {
        com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("onPause " + this)

        if (!isFinishing) {
            com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("Requesting finish")
            finish()
        }

        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.e(e, "Exception!")
        }

        super.onPause()
    }

    @Synchronized public override fun onStop() {
        com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("onStop " + this)
        super.onStop()
    }

    @Synchronized public override fun onDestroy() {
        com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("onDestroy " + this)
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
            com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSIONS_REQUEST -> {
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
            return checkSelfPermission(com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSION_CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED && checkSelfPermission(com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSION_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            return true
        }
    }

    private fun requestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSION_STORAGE)) {
                android.widget.Toast.makeText(this@CameraActivity, "Camera AND storage permission are required for this demo", android.widget.Toast.LENGTH_LONG).show()
            }
            requestPermissions(arrayOf(com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSION_CAMERA, com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSION_STORAGE), com.sightsguru.app.sections.recognizer.CameraActivity.Companion.PERMISSIONS_REQUEST)
        }
    }

    protected fun setFragment() {
        val fragment = CameraConnectionFragment.Companion.newInstance(
                object : CameraConnectionFragment.ConnectionCallback {
                    override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                        this@CameraActivity.onPreviewSizeChosen(size, cameraRotation)
                    }
                }, this, layoutId, desiredPreviewFrameSize)

        fragmentManager
                .beginTransaction()
                .replace(com.sightsguru.app.R.id.container, fragment)
                .commit()
    }

    protected fun fillBytes(planes: Array<android.media.Image.Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                com.sightsguru.app.sections.recognizer.CameraActivity.Companion.LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity())
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i])
        }
    }

    fun requestRender() {
        val overlay = findViewById(com.sightsguru.app.R.id.debug_overlay) as org.tensorflow.demo.OverlayView
        overlay.postInvalidate()
    }

    fun addCallback(callback: org.tensorflow.demo.OverlayView.DrawCallback) {
        val overlay = findViewById(com.sightsguru.app.R.id.debug_overlay) as org.tensorflow.demo.OverlayView
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

    protected abstract fun onPreviewSizeChosen(size: android.util.Size?, rotation: Int)
    protected abstract val layoutId: Int
    protected abstract val desiredPreviewFrameSize: Int

    companion object {
        private val LOGGER = org.tensorflow.demo.env.Logger()

        private val PERMISSIONS_REQUEST = 1

        private val PERMISSION_CAMERA = android.Manifest.permission.CAMERA
        private val PERMISSION_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
}
