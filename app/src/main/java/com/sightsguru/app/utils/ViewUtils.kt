package com.sightsguru.app.utils

import android.content.res.Resources

class ViewUtils {
    companion object {
        fun dpToPx(resources: Resources, dpValue: Int): Int {
            return (dpValue * resources.displayMetrics.density).toInt()
        }
    }
}