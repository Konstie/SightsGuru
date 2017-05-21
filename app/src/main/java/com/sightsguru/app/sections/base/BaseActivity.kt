package com.sightsguru.app.sections.base

import android.os.Build
import android.os.Handler
import android.support.v7.app.AppCompatActivity

open abstract class BaseActivity : AppCompatActivity() {
    private var handler: Handler? = null
    private var handlerThread: android.os.HandlerThread? = null

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
                    onPermissionGranted()
                } else {
                    requestPermission()
                }
            }
        }
    }

    protected abstract fun onPermissionGranted()

    protected fun allPermissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(PERMISSION_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(PERMISSION_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            return true
        }
    }

    protected fun requestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_STORAGE) || shouldShowRequestPermissionRationale(PERMISSION_LOCATION)) {
                android.widget.Toast.makeText(this@BaseActivity, "Camera AND storage permission are required for this demo", android.widget.Toast.LENGTH_LONG).show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA, PERMISSION_STORAGE, PERMISSION_LOCATION), PERMISSIONS_REQUEST)
        }
    }

    companion object {
        private val LOGGER = org.tensorflow.demo.env.Logger()

        private val PERMISSIONS_REQUEST = 1

        private val PERMISSION_CAMERA = android.Manifest.permission.CAMERA
        private val PERMISSION_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        private val PERMISSION_LOCATION = android.Manifest.permission.ACCESS_FINE_LOCATION
    }
}