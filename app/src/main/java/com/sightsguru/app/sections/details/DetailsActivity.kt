package com.sightsguru.app.sections.details

import android.os.Bundle
import android.support.design.widget.CollapsingToolbarLayout
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.CardView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import com.bumptech.glide.Glide
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.sightsguru.app.R
import com.sightsguru.app.sections.base.BaseActivity
import com.sightsguru.app.sections.details.presenters.DetailsPresenter
import com.sightsguru.app.sections.details.presenters.DetailsView
import com.sightsguru.app.utils.IntentHelper

class DetailsActivity : BaseActivity(), DetailsView, OnMapReadyCallback {
    private val MAP_ZOOM_LEVEL = 18f

    private var detailsPresenter: DetailsPresenter? = null
    private var googleMap: GoogleMap? = null

    @BindView(R.id.detail_toolbar) lateinit var toolbar: Toolbar
    @BindView(R.id.toolbar_layout) lateinit var toolbarLayout: CollapsingToolbarLayout
    @BindView(R.id.poster_view) lateinit var posterView: ImageView
    @BindView(R.id.btn_external_details) lateinit var buttonDetails: FloatingActionButton
    @BindView(R.id.place_data_card_view) lateinit var placeDataCardView: CardView
    @BindView(R.id.place_description_card_view) lateinit var placeDescriptionCardView: CardView
    @BindView(R.id.place_map_card_view) lateinit var mapCardView: CardView
    @BindView(R.id.address_text_view) lateinit var addressTextView: TextView
    @BindView(R.id.year_text_view) lateinit var yearTextView: TextView
    @BindView(R.id.description_text_view) lateinit var descriptionTextView: TextView
    @BindView(R.id.map_view) lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_detail)
        ButterKnife.bind(this)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        mapView.onCreate(savedInstanceState)

        toolbarLayout.title = ""

        val placeTag = intent?.getStringExtra(IntentHelper.EXTRA_SPOT_TAG)
        detailsPresenter = DetailsPresenter(placeTag)
        detailsPresenter?.attachView(this)

        setupPlaceInfo()
    }

    private fun setupPlaceInfo() {
        detailsPresenter?.fetchPlaceInfo()
    }

    override fun onCreatePlacePoster(posterUrl: String?) {
        Glide.with(this).load(posterUrl).into(posterView)
    }

    override fun onCreatePlaceExternalLink(externalUrl: String?) {
        buttonDetails.isClickable = true
        buttonDetails.setOnClickListener {
            IntentHelper.openExternalLinkInBrowser(this@DetailsActivity, externalUrl)
        }
    }

    override fun onPlaceNotFound() {
        placeDataCardView.visibility = View.VISIBLE
        addressTextView.setText(R.string.details_error)
        yearTextView.visibility = View.GONE
    }

    override fun onShowPlaceDetails(placeTitle: String?, placeAddress: String?, yearOfEst: Int?) {
        placeDataCardView.visibility = View.VISIBLE
        toolbarLayout.title = placeTitle
        addressTextView.text = getString(R.string.details_address, placeAddress ?: "")
        yearTextView.text = getString(R.string.details_year, yearOfEst)
    }

    override fun onShowPlaceDescription(placeDescription: String?) {
        placeDescriptionCardView.visibility = View.VISIBLE
        descriptionTextView.text = placeDescription
    }

    override fun onLocationDataReady() {
        mapCardView.visibility = View.VISIBLE
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap?) {
        googleMap = map
        MapsInitializer.initialize(this)
        detailsPresenter?.setupGeoData()
    }

    override fun onShowMapView(latitude: Double, longitude: Double) {
        Log.d("DetailsActivity", "Lat: $latitude, long: $longitude")
        val coordinates = LatLng(latitude, longitude)
        googleMap?.addMarker(MarkerOptions().position(coordinates))
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates, MAP_ZOOM_LEVEL))
        googleMap?.mapType = GoogleMap.MAP_TYPE_HYBRID
        mapCardView.setOnClickListener {
            IntentHelper.startGoogleMapsActivity(this, latitude, longitude)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onPermissionGranted() {}

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        detailsPresenter?.detachView()
        super.onDestroy()
    }
}