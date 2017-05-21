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
import android.util.Size
import com.sightsguru.app.R
import com.sightsguru.app.sections.recognizer.CameraConnectionFragment

abstract class BaseCameraActivity : BaseActivity(), ImageReader.OnImageAvailableListener {

    var isDebug = false
        private set

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        LOGGER.d("onCreate " + this)
        super.onCreate(null)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)

        if (allPermissionsGranted()) {
            setFragment()
        } else {
            requestPermission()
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
    }
}
