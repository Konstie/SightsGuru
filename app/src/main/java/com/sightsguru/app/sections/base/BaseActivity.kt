package com.sightsguru.app.sections.base

import android.os.Build
import android.os.Handler
import android.support.v7.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    protected var handler: Handler? = null
    protected var handlerThread: android.os.HandlerThread? = null

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
        private val PERMISSIONS_REQUEST = 1

        private val PERMISSION_CAMERA = android.Manifest.permission.CAMERA
        private val PERMISSION_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        private val PERMISSION_LOCATION = android.Manifest.permission.ACCESS_FINE_LOCATION
    }
}