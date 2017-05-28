package com.sightsguru.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sightsguru.app.sections.details.DetailsActivity
import android.text.TextUtils
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.support.v4.content.ContextCompat.startActivity
import android.support.v4.app.ActivityOptionsCompat



class IntentHelper {
    companion object {
        val EXTRA_SPOT_TAG = "EXTRA_SPOT_TAG"
        val HTTP_SCHEME = "http://"
        val HTTPS_SCHEME = "https://"

        private val PREFIX_PLAY_MARKET = "market://search?q="
        private val PREFIX_PLAY_MARKET_PACKAGE = "market://details?id="
        private val PREFIX_PLAY_MARKET_PACKAGE_WEB = "http://play.google.com/store/apps/details?id="
        private val PREFIX_MAPS_ROUTES = "google.navigation:q="
        private val GOOGLE_MAPS_PACKAGE_NAME = "com.google.android.apps.maps"
        private val GOOGLE_MAPS_PARAMS_DELIMITER = ";"

        fun getDetailsActivityIntent(context: Context, placeTitle: String): Intent {
            val intent = Intent(context, DetailsActivity::class.java)
            intent.putExtra(EXTRA_SPOT_TAG, placeTitle)
            return intent
        }

        fun openExternalLinkInBrowser(context: Context, externalUrl: String?) {
            var url: String = externalUrl ?: ""
            if (url.isEmpty()) {
                return
            } else if (!url.startsWith(HTTP_SCHEME) and (!url.startsWith(HTTPS_SCHEME))) {
                url = "$HTTP_SCHEME$externalUrl"
            }
            Log.d("IntentHelper", "Url: $url")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }

        fun startGoogleMapsActivity(context: Context?, latitude: Double, longitude: Double) {
            if (context == null) {
                return
            }
            if (appInstalled(context, GOOGLE_MAPS_PACKAGE_NAME)) {
                val formattedCoordinates = TextUtils.join(GOOGLE_MAPS_PARAMS_DELIMITER, arrayOf(latitude.toString(), longitude.toString()))
                val gmmIntentUri = Uri.parse(PREFIX_MAPS_ROUTES + formattedCoordinates)
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.`package` = GOOGLE_MAPS_PACKAGE_NAME
                context.startActivity(mapIntent)
            } else {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PREFIX_PLAY_MARKET_PACKAGE + GOOGLE_MAPS_PACKAGE_NAME)))
                } catch (anfe: android.content.ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PREFIX_PLAY_MARKET_PACKAGE_WEB + GOOGLE_MAPS_PACKAGE_NAME)))
                }

            }
        }

        private fun appInstalled(context: Context?, packageName: String): Boolean {
            if (context == null) {
                return false
            }
            val packageManager = context.packageManager
            var applicationInfo: ApplicationInfo? = null
            try {
                applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(IntentHelper::class.java.simpleName, "Cannot find the app with package name " + packageName)
            }

            return applicationInfo != null
        }
    }
}