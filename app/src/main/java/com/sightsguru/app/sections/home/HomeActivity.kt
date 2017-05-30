package com.sightsguru.app.sections.home

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import com.sightsguru.app.R
import com.sightsguru.app.sections.base.BaseActivity
import com.sightsguru.app.sections.recognizer.ClassifierActivity

class HomeActivity : BaseActivity() {
    @BindView(R.id.skyline_image_view) lateinit var skylineImageView: ImageView
    @BindView(R.id.btn_start) lateinit var buttonStartRecognition: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        ButterKnife.bind(this)

        buttonStartRecognition.setOnClickListener {
            onStartPressed()
        }
    }

    private fun onStartPressed() {
        val intent = Intent(this, ClassifierActivity::class.java)
        startActivity(intent)
    }

    override fun onPermissionGranted() {}
}