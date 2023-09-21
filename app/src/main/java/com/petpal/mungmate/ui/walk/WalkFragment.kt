package com.petpal.mungmate.ui.walk

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils.formatElapsedTime
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceTypes
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.petpal.mungmate.MainActivity
import com.petpal.mungmate.R
import com.petpal.mungmate.databinding.FragmentWalkBinding
import com.petpal.mungmate.model.Favorite
import com.petpal.mungmate.model.KakaoSearchResponse
import com.petpal.mungmate.model.PlaceData
import kotlinx.coroutines.Dispatchers
import com.petpal.mungmate.model.ReceiveUser
import com.petpal.mungmate.model.Review
import com.petpal.mungmate.utils.LastKnownLocation
import com.petpal.mungmate.utils.onWalk.onWalk
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.daum.mf.map.api.MapPOIItem
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapView
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WalkFragment : Fragment(), net.daum.mf.map.api.MapView.POIItemEventListener,
    net.daum.mf.map.api.MapView.CurrentLocationEventListener,
    net.daum.mf.map.api.MapView.MapViewEventListener {

    private lateinit var fragmentWalkBinding: FragmentWalkBinding
    private lateinit var mainActivity: MainActivity
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: WalkViewModel by viewModels { WalkViewModelFactory(WalkRepository()) }
    private lateinit var kakaoSearchResponse: KakaoSearchResponse
    private var isLocationPermissionGranted = false
    private var isFavorited1 = false
    private var latestReviews: List<Review>? = null
    private val currentMarkers: MutableList<MapPOIItem> = mutableListOf()
    private val avgRatingBundle = Bundle()
    private lateinit var initialBottomSheetView: View
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var onWalkBottomSheetView: View
    private lateinit var onWalkbottomSheetDialog: BottomSheetDialog
    private val auth = Firebase.auth
    private lateinit var userId: String
    private lateinit var userNickname: String
    private var lastLocation: Location? = null
    private var totalDistance = 0.0
    private var elapsedTime = 0L
    private var userLocationMarker: MapPOIItem? = null
    private var startTimestamp: String = "0"
    private val handler = Handler(Looper.getMainLooper())
    private var nearbyUsers: List<ReceiveUser>?=null



    val timerRunnable = object : Runnable {
        override fun run() {
            elapsedTime += 1
            fragmentWalkBinding.textViewWalkTime.text = formatElapsedTime(elapsedTime)
            handler.postDelayed(this, 1000)
        }
    }


    companion object {
        const val REQUEST_LOCATION_PERMISSION = 1

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {


        mainActivity = activity as MainActivity
        fragmentWalkBinding = FragmentWalkBinding.inflate(inflater)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mainActivity)

        initialBottomSheetView = inflater.inflate(R.layout.row_walk_bottom_sheet_place, null)
        bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(initialBottomSheetView)

        onWalkBottomSheetView=inflater.inflate(R.layout.row_walk_bottom_sheet_user,null)
        onWalkbottomSheetDialog=BottomSheetDialog(requireContext())
        onWalkbottomSheetDialog.setContentView(onWalkBottomSheetView)


        setupMapView()
        setupButtonListeners()
        observeViewModel()
        val user = auth.currentUser
        userId = user?.uid.toString()

        if(onWalk==false){
            viewModel.updateOnWalkStatusFalse(userId)
        }

        viewModel.fetchUserNickname(userId)

        viewModel.userNickname.observe(viewLifecycleOwner) {
            userNickname = it!!
        }

        return fragmentWalkBinding.root
    }

    private fun toggleVisibility(viewToShow: View, viewToHide: View) {
        viewToShow.visibility = View.VISIBLE
        viewToHide.visibility = View.GONE
    }

    private fun setupMapView() {
        //필터 드로어 제어
        fragmentWalkBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        fragmentWalkBinding.mapView.setPOIItemEventListener(this)
        fragmentWalkBinding.mapView.setCurrentLocationEventListener(this)
        fragmentWalkBinding.mapView.setMapViewEventListener(this)
        requestLocationPermissionIfNeeded()


    }

    private fun setupButtonListeners() {
        fragmentWalkBinding.buttonWalk.setOnClickListener {
            onWalk=true
            observeViewModelonWalk()
            viewModel.observeUsersOnWalk()
            
            toggleVisibility(
                fragmentWalkBinding.LinearLayoutOnWalk,
                fragmentWalkBinding.LinearLayoutOffWalk
            )

            fragmentWalkBinding.mapView.removeAllPOIItems()
            fragmentWalkBinding.imageViewWalkToggle.setImageResource(R.drawable.dog_walk)
            handler.post(timerRunnable)
            updateCurrentLocationOnce()
            startLocationUpdates()
            startTimestamp = timestampToString(System.currentTimeMillis())
        }

        fragmentWalkBinding.chipMapFilter.setOnClickListener {
            fragmentWalkBinding.drawerLayout.setScrimColor(Color.parseColor("#FFFFFF"))
            fragmentWalkBinding.drawerLayout.openDrawer(GravityCompat.END)

        }

        fragmentWalkBinding.buttonFilterSubmit.setOnClickListener {
            val isAnyFilterSelected = fragmentWalkBinding.filterDistanceGroup.checkedChipId != -1 || fragmentWalkBinding.filterUserGenderGroup.checkedChipId != -1 || fragmentWalkBinding.filterAgeRangeGroup.checkedChipId != -1 || fragmentWalkBinding.filterPetGenderGroup.checkedChipId != -1 || fragmentWalkBinding.filterPetPropensityGroup.checkedChipId != -1 || fragmentWalkBinding.filterNeuterStatusGroup.checkedChipId != -1
            fragmentWalkBinding.chipMapFilter.isChecked = isAnyFilterSelected
            fragmentWalkBinding.drawerLayout.closeDrawer(GravityCompat.END)
            showSnackbar("필터가 적용되었습니다.")

        }

        fragmentWalkBinding.buttonStopWalk.setOnClickListener {
            onWalk=false
            fragmentWalkBinding.mapView.removeAllPOIItems()
            toggleVisibility(
                fragmentWalkBinding.LinearLayoutOffWalk,
                fragmentWalkBinding.LinearLayoutOnWalk
            )
            fragmentWalkBinding.imageViewWalkToggle.setImageResource(R.drawable.dog_home)

            val endTimestamp=timestampToString(System.currentTimeMillis())
            val bundle=Bundle()
            bundle.putString("walkRecorduid",userId)
            bundle.putString("walkRecordDate",getCurrentDate())
            bundle.putString("walkRecordStartTime",startTimestamp)
            bundle.putString("walkRecordEndTime",endTimestamp)
            bundle.putLong("walkDuration",elapsedTime)
            bundle.putString("walkDistance",totalDistance.toString())
            bundle.putString("walkMatchingId","idid")
            stopLocationUpdates()
            handler.removeCallbacks(timerRunnable)
            elapsedTime = 0L
            fragmentWalkBinding.textViewWalkTime.text = formatElapsedTime(elapsedTime)
            viewModel.updateOnWalkStatusFalse(userId)
            mainActivity.navigate(R.id.action_mainFragment_to_WriteWalkReviewFragment, bundle)
            getCurrentLocation()
        }


        fragmentWalkBinding.imageViewMylocation.setOnClickListener {
            if(onWalk==true) {
                getCurrentLocationOnWalk()
            }else {
                getCurrentLocation()
            }
            LastKnownLocation.latitude = null
            LastKnownLocation.longitude = null
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun timestampToString(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return formatter.format(date)
    }
    private fun observeViewModelonWalk(){
        viewModel.usersOnWalk.observe(viewLifecycleOwner, Observer { users ->
            users.forEach { user ->
                user.nickname?.let { Log.d("전체유저", it) }
            }
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLocation=Location("currentLocation").apply{
                            latitude=it.latitude ?: 0.0
                            longitude=it.longitude ?:0.0
                        }
                        nearbyUsers = users.filter { user ->
                            // user의 위치를 가져옵니다.
                            val userLocation = Location("userLocation").apply {
                                latitude = user.location?.get("latitude") ?: 0.0
                                longitude = user.location?.get("longitude") ?: 0.0
                            }

                            user.uid != userId && currentLocation.distanceTo(userLocation) <= 2000
                        }

                        for (user in nearbyUsers!!) {
                            // 마커 추가
                            user.nickname?.let { it1 -> Log.d("진짜", it1) }
                            val mapPoint = MapPoint.mapPointWithGeoCoord(
                                user.location?.get("latitude") ?: 0.0, user.location?.get("longitude")
                                    ?: 0.0)
                            val poiItem = MapPOIItem().apply {
                                itemName = user.nickname
                                tag = user.nickname.hashCode()
                                this.mapPoint = mapPoint
                                markerType = MapPOIItem.MarkerType.CustomImage
                                customImageResourceId = R.drawable.userlocation // 여기에 원하는 이미지 리소스 지정
                                isCustomImageAutoscale = true
                                setCustomImageAnchor(0.5f, 1.0f)
                            }
                            fragmentWalkBinding.mapView.addPOIItem(poiItem)
                        }
                    }
                }
            }

        })
    }
    private fun observeViewModel() {

        viewModel.searchResults.observe(viewLifecycleOwner) { response ->
            //검색 결과로 씀
            kakaoSearchResponse = response
            Log.d("sizeee", (kakaoSearchResponse.documents.size).toString())
            val newMarkers = kakaoSearchResponse.documents.map { place ->
                val mapPoint = MapPoint.mapPointWithGeoCoord(place.y, place.x)
                MapPOIItem().apply {
                    itemName = place.place_name
                    tag = place.id.hashCode() //id로 태그
                    this.mapPoint = mapPoint
                    markerType = MapPOIItem.MarkerType.CustomImage
                    customImageResourceId = R.drawable.favorite_pin
                    //마커 크기 자동조정
                    isCustomImageAutoscale = true
                    //마커 위치 조정
                    setCustomImageAnchor(0.5f, 1.0f)
                }
            }

            //기존 마커 중 새로운 검색 결과에 없는 것은 지도에서 제거하고 currentMarkers에서도 제거
            val markersToRemove = currentMarkers.filter { marker ->
                newMarkers.none { it.tag == marker.tag }
            }
            for (marker in markersToRemove) {
                fragmentWalkBinding.mapView.removePOIItem(marker)
                currentMarkers.remove(marker)
            }

            //새로운 검색 결과 중 현재 지도에 없는 마커들을 추가
            val markersToAdd = newMarkers.filter { marker ->
                currentMarkers.none { it.tag == marker.tag }
            }
            for (marker in markersToAdd) {
                fragmentWalkBinding.mapView.addPOIItem(marker)
                currentMarkers.add(marker)
            }
        }
    }

    //메인화면 진입시 권한 요청/LastKnownLocation의 값에 따라 검색/마킹
    private fun requestLocationPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            // 권한이 승인되어있으면 위치 가져오기
            isLocationPermissionGranted = true
            Log.d("LastKnownLocation", LastKnownLocation.latitude.toString())
            if (LastKnownLocation.latitude != null || LastKnownLocation.longitude != null){
                getLastLocation()
            } else {
                getCurrentLocation()
            }
        }
    }
    private fun requestLocationPermissionIfNeededOnWalk() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            // 권한이 승인되어있으면 위치 가져오기
            isLocationPermissionGranted = true
            Log.d("LastKnownLocation",LastKnownLocation.latitude.toString())
            if (LastKnownLocation.latitude != null || LastKnownLocation.longitude != null){
                getLastLocationOnWalk()
            } else {
                getCurrentLocationOnWalk()
            }
        }
    }
    private fun getCurrentLocationOnWalk() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    LastKnownLocation.latitude=it.latitude
                    LastKnownLocation.longitude=it.longitude
                    viewModel.observeUsersOnWalk()
                    val mapPoint = MapPoint.mapPointWithGeoCoord(it.latitude, it.longitude)
                    showUserLocationOnMap(it)
                    fragmentWalkBinding.mapView.setMapCenterPoint(mapPoint, true)
                }
            }
        }
    }

    //내 현재 위치에서 검색/마킹
    private fun getCurrentLocation() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    LastKnownLocation.latitude = it.latitude
                    LastKnownLocation.longitude = it.longitude
                    viewModel.searchPlacesByKeyword(it.latitude, it.longitude, "동물")
                    showUserLocationOnMap1(location)
                    val mapPoint = MapPoint.mapPointWithGeoCoord(it.latitude, it.longitude)
                    fragmentWalkBinding.mapView.setMapCenterPoint(mapPoint, true)
                }
            }
        }
    }
    private fun getLastLocationOnWalk() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            LastKnownLocation.let {
                LastKnownLocation.latitude?.let { it1 ->
                    LastKnownLocation.longitude?.let { it2 ->
                        viewModel.observeUsersOnWalk()
                    }
                }
                val mapPoint = LastKnownLocation.latitude?.let { it1 ->
                    LastKnownLocation.longitude?.let { it2 ->
                        MapPoint.mapPointWithGeoCoord(it1, it2)
                    }
                }
                val location = Location("providerName")
                location.latitude = LastKnownLocation.latitude!!
                location.longitude = LastKnownLocation.longitude!!
                showUserLocationOnMap(location)
                fragmentWalkBinding.mapView.setMapCenterPoint(mapPoint, true)

                for (marker in currentMarkers) {
                    fragmentWalkBinding.mapView.removePOIItem(marker)
                }
                currentMarkers.clear()
            }
        }
    }


    private fun checkPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showUserLocationOnMap(location: Location) {
        //fragmentWalkBinding.mapView.removePOIItem(userLocationMarker)
        userLocationMarker?.let {
            fragmentWalkBinding.mapView.removePOIItem(it)
        }
        userLocationMarker = MapPOIItem().apply {
            itemName = "나"
            mapPoint = MapPoint.mapPointWithGeoCoord(location.latitude, location.longitude)
            markerType = MapPOIItem.MarkerType.CustomImage
            customImageResourceId = R.drawable.mylocation
            isCustomImageAutoscale = true
            setCustomImageAnchor(0.5f, 1.0f)

        }
        fragmentWalkBinding.mapView.addPOIItem(userLocationMarker)
//        fragmentWalkBinding.mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(location.latitude, location.longitude),true)
    }
    private fun showUserLocationOnMap1(location: Location) {
        //fragmentWalkBinding.mapView.removePOIItem(userLocationMarker)
        userLocationMarker?.let {
            fragmentWalkBinding.mapView.removePOIItem(it)
        }
        userLocationMarker = MapPOIItem().apply {
            itemName = "나"
            mapPoint = MapPoint.mapPointWithGeoCoord(location.latitude, location.longitude)
            markerType = MapPOIItem.MarkerType.CustomImage
            customImageResourceId = R.drawable.mylocation
            isCustomImageAutoscale = true
            setCustomImageAnchor(0.5f, 1.0f)

        }
        fragmentWalkBinding.mapView.addPOIItem(userLocationMarker)

//        fragmentWalkBinding.mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(location.latitude, location.longitude),true)
    }
    private fun updateCurrentLocationOnce() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val mapPoint = MapPoint.mapPointWithGeoCoord(it.latitude, it.longitude)
                    fragmentWalkBinding.mapView.setMapCenterPoint(mapPoint, true)
                }
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            p0 ?: return
            for (location in p0.locations) {
                // 정확도 체크
                if (location.accuracy <= 3) {
                    lastLocation?.let {
                        totalDistance += it.distanceTo(location).toDouble()
                        // UI 업데이트
                        fragmentWalkBinding.textViewWalkDistance.text =
                            "${String.format("%.1f", totalDistance)} m"
                    }
                    showUserLocationOnMap(location)
                    lastLocation = location
                    lastLocation?.let { location ->
                        viewModel.updateLocationAndOnWalkStatus(
                            userId,
                            location.latitude,
                            location.longitude
                        )
                    }
                    Log.d("infooooo1", lastLocation.toString())
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val locationRequest = LocationRequest.create().apply {
                interval = 2000 // 2초마다 갱신
                fastestInterval = 5000 // 타앱
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        totalDistance = 0.0
        fragmentWalkBinding.textViewWalkDistance.text = totalDistance.toString()
    }


    //LastKnownLocation의 위치에서 검색 마킹(원래 있던 마커들 지우고)
    private fun getLastLocation() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    //showUserLocationOnMap1(location)
                }
            }
            LastKnownLocation.let {
                LastKnownLocation.latitude?.let { it1 ->
                    LastKnownLocation.longitude?.let { it2 ->
                        viewModel.searchPlacesByKeyword(it1, it2, "동물")
                    }
                }
                val mapPoint = LastKnownLocation.latitude?.let { it1 ->
                    LastKnownLocation.longitude?.let { it2 ->
                        MapPoint.mapPointWithGeoCoord(it1, it2)
                    }


                }


                fragmentWalkBinding.mapView.setMapCenterPoint(mapPoint,true)


                for (marker in currentMarkers) {
                    fragmentWalkBinding.mapView.removePOIItem(marker)
                }
                currentMarkers.clear()
            }
        }
    }

    //거리 필터의 값에 따라 ㄱ
    private fun getLastLocationFilter(radius: Int) {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            LastKnownLocation.let {
                LastKnownLocation.latitude?.let { it1 ->
                    LastKnownLocation.longitude?.let { it2 ->
                        viewModel.searchPlacesByKeywordFilter(it1, it2, "동물", radius)
                    }
                }
                val mapPoint = LastKnownLocation.latitude?.let { it1 ->
                    LastKnownLocation.longitude?.let { it2 -> MapPoint.mapPointWithGeoCoord(it1, it2)
                    }
                }
                fragmentWalkBinding.mapView.setMapCenterPoint(mapPoint, true)

                for (marker in currentMarkers) {
                    fragmentWalkBinding.mapView.removePOIItem(marker)
                }
                currentMarkers.clear()
            }
        }
    }

    //권한 핸들링
    //앱 초기 실행시 권한 부여 여부 결정 전에 위치를 받아올 수 없는 현상 핸들링
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 사용자가 위치 권한을 승인한 경우
                    isLocationPermissionGranted = true
                    getCurrentLocation()
                } else {
                    //이런 느낌? 근데 스낵바 띄우고 위치정보를 가져오지 못한채로 기본값(서울시청 기준)을 보여주는게 맞는건가? 아니면 다시 권한을 요청해야하나?
                    showSnackbar("현재 위치를 확인하려면 위치 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
                }
                return
            }
            //다른 권한 필요하면 ㄱ
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(fragmentWalkBinding.root, message, Snackbar.LENGTH_LONG).show()
    }

    @SuppressLint("SimpleDateFormat")
    fun getCurrentDate(): String {
        val current = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd") // 년-월-일
        return formatter.format(current)
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateAgeGroup(birthday: String): String {
        val birthYear = LocalDate.parse(birthday, DateTimeFormatter.ofPattern("yyyy-MM-dd")).year
        val currentYear = LocalDate.now().year
        val age = currentYear - birthYear

        return when {
            age in 0..9 -> "나이대 : 영유아 및 초등학생"
            age in 10..19 -> "나이대 : 10대"
            age in 20..29 -> "나이대 : 20대"
            age in 30..39 -> "나이대 : 30대"
            age in 40..49 -> "나이대 : 40대"
            age in 50..59 -> "나이대 : 50대"
            age in 60..69 -> "나이대 : 60대"
            age in 70..79 -> "나이대 : 70대"
            else -> "밝히지 않음"
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateAge(birthday: String): Int {
        val birthDate = LocalDate.parse(birthday, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val currentDate = LocalDate.now()

        var age = currentDate.year - birthDate.year

        if (birthDate.month > currentDate.month || (birthDate.month == currentDate.month && birthDate.dayOfMonth > currentDate.dayOfMonth)) {
            age--
        }

        return age
    }

    //맵의 마커 클릭시
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPOIItemSelected(p0: net.daum.mf.map.api.MapView?, p1: MapPOIItem?) {
        if (p1?.itemName == "나") {
            return
        }
        if (onWalk == false) {
            val selectedPlace = kakaoSearchResponse.documents.find { it.id.hashCode() == p1?.tag }
            val detailCardView = layoutInflater.inflate(R.layout.row_place_review, null)
            val detailDialog = BottomSheetDialog(requireActivity())
            val detailRating = detailCardView.findViewById<RatingBar>(R.id.placeReviewDetailRatingBar)
            val detailUserName = detailCardView.findViewById<TextView>(R.id.textViewPlaceReviewDetailUserName)
            val detailDate = detailCardView.findViewById<TextView>(R.id.textViewPlaceReviewDetailDate)
            val detailContent = detailCardView.findViewById<TextView>(R.id.textView4)
            val detailImage = detailCardView.findViewById<ImageView>(R.id.imageView11)
            val buttonsubmit = initialBottomSheetView.findViewById<Button>(R.id.buttonSubmitReview)
            val kakaoPlaceId = selectedPlace?.id
          
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("placeImage", selectedPlace!!.address_name)
                //selecedPlace로 구글맵에서 이미지 가져오기
                val placeId = searchPlaceWithAutoComplete(
                    mainActivity.placesClient,
                    selectedPlace!!.address_name
                )
                Log.d("placeImage", placeId.toString())

                val bitmap = fetchPlaceImageFromGooglePlacesAPI(mainActivity.placesClient, placeId)
                Log.d("placeImage", bitmap.toString())

                withContext(Dispatchers.Main) {
                    val imageViewPlace =
                        initialBottomSheetView.findViewById<ImageView>(R.id.ImageViewBottomRecommendPlace)

                    if (bitmap != null) {
                        // 이미지를 받아온 경우
                        imageViewPlace.setImageBitmap(bitmap)
                    } else {
                        // 이미지를 받아오지 못한 경우 또는 Place ID가 null인 경우 처리
                        // 예: 기본 이미지 설정
                        imageViewPlace.setImageResource(R.drawable.default_profile_image)
                    }

                    bottomSheetDialog.show()
                }

            }
          
            //클릭한 맵의 placeId로 메서드 실행
            kakaoPlaceId?.let {
                viewModel.fetchFavoriteCount(it)
                viewModel.fetchReviewCount(it)
                viewModel.fetchLatestReviewsForPlace(it)
                viewModel.fetchPlaceInfoFromFirestore(it)
                viewModel.fetchIsPlaceFavoritedByUser(it, userId)  // Removed the collect operation
                viewModel.fetchAverageRatingForPlace(it)
            }

            lifecycleScope.launch {
                //바텀시트의 좋아요 상태에 따라 하트 이미지 변경
                viewModel.isPlaceFavorited.collect { isFavorited ->
                    isFavorited?.let {
                        if (it) {
                            initialBottomSheetView.findViewById<ImageView>(R.id.imageViewFavoirte).setImageResource(R.drawable.filled_heart)
                            isFavorited1 = true

                        } else {
                            initialBottomSheetView.findViewById<ImageView>(R.id.imageViewFavoirte).setImageResource(R.drawable.empty_heart)
                            isFavorited1 = false
                        }

                    }

                }

            }
            //place의 리뷰 별점 평균 표기
            viewModel.averageRatingForPlace.observe(viewLifecycleOwner) { avgRating ->
                val roundedAvgRating = String.format("%.1f", avgRating).toFloat()
                initialBottomSheetView.findViewById<RatingBar>(R.id.placeRatingBar).rating = avgRating
                initialBottomSheetView.findViewById<TextView>(R.id.textViewratingbar).text = roundedAvgRating.toString()
                avgRatingBundle.putFloat("avgRating", avgRating)
                Log.d("avgRating", avgRating.toString())
            }

            //favorite 서브컬렉션의 문서 개수 받아와서 실시간 반영
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.favoriteCount.collect { count ->
                    val favoriteCountTextView = initialBottomSheetView.findViewById<TextView>(R.id.textViewPlaceFavoriteCount)
                    favoriteCountTextView.text = "${count}명의 유저가 추천합니다."
                }
            }

            //place 정보 받아와서 db에 있는 place인지 확인 후 분기로 이름 입력
            viewModel.placeInfo.observe(viewLifecycleOwner) { placeInfo ->
                val textViewPlaceName = initialBottomSheetView.findViewById<TextView>(R.id.textView)
                val imageViewPlace = initialBottomSheetView.findViewById<ImageView>(R.id.ImageViewBottomRecommendPlace)
                textViewPlaceName.text = placeInfo?.get("name") as? String ?: "${selectedPlace?.place_name}"
              
//                 // firestore에서 받아온 정보로부터 imageRes받아온 것
//                 val imageUrl = placeInfo?.get("imageRes") as? String

//                 Glide.with(imageViewPlace.context)
//                     .load(imageUrl)
//                     .placeholder(R.drawable.default_profile_image)
//                     .listener(object : RequestListener<Drawable> {
//                         override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
//                             return false
//                         }

//                         override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {

//                             Handler(Looper.getMainLooper()).postDelayed({ bottomSheetDialog.show() }, 200)
//                             return false
//                         }
//                     })
//                     .into(imageViewPlace)
            }

            //리뷰 개수 받아와서 출력
            viewModel.reviewCount.observe(viewLifecycleOwner) { reviewCount ->
                val reviewCountTextView = initialBottomSheetView.findViewById<TextView>(R.id.textViewPlaceReviewCount)
                reviewCountTextView.text = "${reviewCount}개의 리뷰가 있어요"
            }

            //최신순 리뷰 두개 받아와서 바텀시트에 1,2 출력
            viewModel.latestReviews.observe(viewLifecycleOwner) { reviews ->
                latestReviews = reviews
                val placeuserRating1 =
                    initialBottomSheetView.findViewById<RatingBar>(R.id.placeUserRatingBar1)
                val placeuserRating2 =
                    initialBottomSheetView.findViewById<RatingBar>(R.id.placeUserRatingBar2)
                val placeuserReview1 =
                    initialBottomSheetView.findViewById<TextView>(R.id.placeUserReview1)
                val placeuserReview2 =
                    initialBottomSheetView.findViewById<TextView>(R.id.placeUserReview2)

                if (reviews.isNotEmpty()) {
                    val firstReview = reviews[0]
                    placeuserRating1.rating = firstReview.rating!!
                    placeuserReview1.text = firstReview.comment

                    placeuserRating1.visibility = View.VISIBLE
                    placeuserReview1.visibility = View.VISIBLE


                    if (reviews.size > 1) {
                        val secondReview = reviews[1]
                        placeuserRating2.rating = secondReview.rating!!
                        placeuserReview2.text = secondReview.comment

                        placeuserRating2.visibility = View.VISIBLE
                        placeuserReview2.visibility = View.VISIBLE

                    } else {
                        placeuserRating2.visibility = View.GONE
                        placeuserReview2.visibility = View.GONE
                    }

                    initialBottomSheetView.findViewById<Chip>(R.id.chipViewAllReviews).visibility = View.VISIBLE
                    initialBottomSheetView.findViewById<TextView>(R.id.textViewNoReview).visibility = View.GONE
                } else {
                    placeuserRating1.visibility = View.GONE
                    placeuserRating2.visibility = View.GONE
                    placeuserReview1.visibility = View.GONE
                    placeuserReview2.visibility = View.GONE
                    initialBottomSheetView.findViewById<Chip>(R.id.chipViewAllReviews).visibility = View.GONE
                    initialBottomSheetView.findViewById<TextView>(R.id.textViewNoReview).visibility = View.VISIBLE
                }
            }



            //바텀시트 끌어 올렸을때 상세 리뷰 페이지로 이동
            bottomSheetDialog.behavior.addBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            avgRatingBundle.putString("place_name", selectedPlace?.place_name)
                            avgRatingBundle.putString("place_id", selectedPlace?.id)
                            avgRatingBundle.putString("phone", selectedPlace?.phone)
                            avgRatingBundle.putString(
                                "place_road_adress_name",
                                selectedPlace?.road_address_name
                            )
                            avgRatingBundle.putString(
                                "place_category",
                                selectedPlace?.category_group_name
                            )
                            mainActivity.navigate(R.id.action_mainFragment_to_placeReviewFragment, avgRatingBundle,)
                            bottomSheetDialog.dismiss()
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })

            //좋아요 버튼 눌렀을 때  userid로 favorite 서브 컬렉션에 문서 추가 / isFavorited 상태에 따라 이미지 교체,스넥바,count 출력
            initialBottomSheetView.findViewById<ImageView>(R.id.imageViewFavoirte).apply {
                val place = Place(selectedPlace!!.id, selectedPlace.place_name, selectedPlace.category_group_name, (selectedPlace.x).toString(), (selectedPlace.y).toString(), selectedPlace.phone, selectedPlace.road_address_name)
                setOnClickListener {
                    if (!isFavorited1) {
                        isFavorited1 = true
                        setImageResource(R.drawable.filled_heart)
                        val favorite = Favorite(userId)
                        viewModel.addPlaceToFavorite(place, favorite)
                        val bottomplacelayout: CoordinatorLayout =
                            initialBottomSheetView.findViewById(R.id.bottom_place_layout)
                        Snackbar.make(bottomplacelayout, "반영되었습니다.", Snackbar.LENGTH_SHORT).show()
                    } else {
                        isFavorited1 = false
                        setImageResource(R.drawable.empty_heart)
                        viewModel.removeFavorite(kakaoPlaceId!!, userId)
                        val bottomplacelayout: CoordinatorLayout =
                            initialBottomSheetView.findViewById(R.id.bottom_place_layout)
                        Snackbar.make(bottomplacelayout, "반영되었습니다.", Snackbar.LENGTH_SHORT).show()
                    }


                    viewModel.fetchFavoriteCount(kakaoPlaceId!!)
                    viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                        viewModel.favoriteCount.collect { favoriteCount ->
                            Log.d("firstsubmit", favoriteCount.toString())
                            val favoriteCountTextView =
                                initialBottomSheetView.findViewById<TextView>(R.id.textViewPlaceFavoriteCount)
                            favoriteCountTextView.text = "${favoriteCount}명의 유저가 추천합니다."
                        }
                    }

                }
            }
            //리뷰 입력 버튼
            buttonsubmit.setOnClickListener {
                val bundle = Bundle()
                bundle.putString("place_name", selectedPlace?.place_name)
                bundle.putString("place_id", selectedPlace?.id)
                bundle.putString("place_category", selectedPlace?.category_group_name)
                bundle.putString("place_long", selectedPlace?.x.toString())
                bundle.putString("place_lat", selectedPlace?.y.toString())
                bundle.putString("phone", selectedPlace?.phone)
                bundle.putString("userNickname", userNickname)
                bundle.putString("place_road_adress_name", selectedPlace?.road_address_name)

                mainActivity.navigate(R.id.action_mainFragment_to_writePlaceReviewFragment, bundle)
                bottomSheetDialog.dismiss()
            }
            //최근리뷰 1 누르면 상세 카드뷰 출력
            initialBottomSheetView.findViewById<TextView>(R.id.placeUserReview1)
                .setOnClickListener {
                    detailDialog.setContentView(detailCardView)
                    detailRating.rating = latestReviews!![0].rating!!
                    detailUserName.text = latestReviews!![0].userNickname
                    detailDate.text = latestReviews!![0].date
                    detailContent.text = latestReviews!![0].comment
                    detailDialog.findViewById<TextView>(R.id.textViewPlaceReviewModify)?.visibility =
                        View.GONE
                    detailDialog.findViewById<TextView>(R.id.textViewPlaceReviewDelete)?.visibility =
                        View.GONE
                    latestReviews!![0].imageRes?.let { imageUrl ->
                        Glide.with(detailImage.context)
                            .load(imageUrl)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                                    return false
                                }

                                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                    detailDialog.show()
                                    return false
                                }
                            })
                            .into(detailImage)
                    }
                }
            //최근리뷰 2 누르면 상세 카드뷰 출력
            initialBottomSheetView.findViewById<TextView>(R.id.placeUserReview2)
                .setOnClickListener {

                    detailRating.rating = latestReviews!![1].rating!!
                    detailUserName.text = latestReviews!![1].userNickname
                    detailDate.text = latestReviews!![1].date
                    detailContent.text = latestReviews!![1].comment
                    detailDialog.findViewById<TextView>(R.id.textViewPlaceReviewModify)?.visibility =
                        View.GONE
                    detailDialog.findViewById<TextView>(R.id.textViewPlaceReviewDelete)?.visibility =
                        View.GONE
                    latestReviews!![1].imageRes?.let { imageUrl ->
                        Glide.with(detailImage.context)
                            .load(imageUrl)
                            .listener(object : RequestListener<Drawable> { override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                                    detailDialog.show()
                                    return false
                                }

                                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                    detailDialog.show()
                                    return false
                                }
                            })
                            .into(detailImage)
                    }
                }
            //상세리뷰 페이지
            initialBottomSheetView.findViewById<Chip>(R.id.chipViewAllReviews).setOnClickListener {
                avgRatingBundle.putString("place_name", selectedPlace?.place_name)
                avgRatingBundle.putString("place_id", selectedPlace?.id)
                avgRatingBundle.putString("phone", selectedPlace?.phone)
                avgRatingBundle.putString(
                    "place_road_adress_name",
                    selectedPlace?.road_address_name
                )
                avgRatingBundle.putString("place_category", selectedPlace?.category_group_name)
                avgRatingBundle.putString("userNickname", userNickname)
                mainActivity.navigate(
                    R.id.action_mainFragment_to_placeReviewFragment,
                    avgRatingBundle
                )
                bottomSheetDialog.dismiss()
            }
            //산책 on 유저마커 바텀시트
        } else {

            val selectedUserNicknameHash = p1?.tag

            val selectedUser = nearbyUsers?.find { it.nickname.hashCode() == selectedUserNicknameHash }
            selectedUser?.let { user ->
                user.uid?.let { viewModel.fetchMatchingWalkCount(it) }
                val userNicknameTextView = onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomUserNickname)
                userNicknameTextView.text = user.nickname
                val textViewBottomUserAgeRange=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomUserAgeRange)
                textViewBottomUserAgeRange.text=user.birthday?.let { calculateAgeGroup(it) }
                val imageViewUser=onWalkBottomSheetView.findViewById<ImageView>(R.id.ImageViewBottomUserProfileImage)
                val textViewBottomUserAvailability=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomUserAvailability)
                if(user.walkHoursStart?.isNotEmpty() == true) {
                    textViewBottomUserAvailability.text = "산책 가능 시간대 : ${user.walkHoursStart} ~ ${user.walkHoursEnd}"
                }else{
                    textViewBottomUserAvailability.text = "산책 가능 시간대 : 언제든지 가능해요"
                }
                val textViewBottomUserMathcingHistory=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomUserMathcingHistory)
                val textViewBottomPetName=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomPetName)
                val textViewBottomPetAge=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomPetAge)
                val textViewBottomPetBreed=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomPetBreed)
                val textViewBottomPetPropensity=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomPetPropensity)
                val textViewBottomPetGender=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomPetGender)
                val textViewBottomIsPetNeutered=onWalkBottomSheetView.findViewById<TextView>(R.id.textViewBottomIsPetNeutered)
                val imageViewBottomPetProfileImage=onWalkBottomSheetView.findViewById<ImageView>(R.id.ImageViewBottomPetProfileImage)
                val buttonBottomWalk=onWalkBottomSheetView.findViewById<Button>(R.id.buttonBottomWalk)
                val buttonBottomBlock=onWalkBottomSheetView.findViewById<Button>(R.id.buttonBottomBlock)
                user.uid?.let { viewModel.blockUser(userId, it) }
                buttonBottomWalk.setOnClickListener {
                    val bundle=Bundle()
                    bundle.putString("receiverId",user.uid)
                    mainActivity.navigate(R.id.action_mainFragment_to_chat,bundle)
                }
                buttonBottomBlock.setOnClickListener {
                    viewModel.isUserBlocked.observe(viewLifecycleOwner, Observer { isBlocked ->
                        if (isBlocked) {
                            Snackbar.make(buttonBottomBlock, "사용자가 차단되었습니다.",Snackbar.LENGTH_SHORT).show()
                        }
                         else {
                            Snackbar.make(buttonBottomBlock, "사용자 차단에 실패하였습니다.",Snackbar.LENGTH_SHORT).show()
                        }
                    })




//                    val bundle=Bundle()
//                    bundle.putString("receiverId",user.uid)
//                    mainActivity.navigate(R.id.action_mainFragment_to_manage_block,bundle)
                }
                user.pets.let { pets ->
                    val firstPet = pets.firstOrNull()
                    firstPet?.let { pet ->
                        val petSex=when((pet.petSex)?.toInt()){
                            0->{"남아"}
                            1->{"여아"}
                            else -> {"입력 안함"}
                        }
                        val isPetNeutered=if(pet.neutered==true){ "O" }else{ "X" }
                        val petAge= pet.birth?.let { calculateAge(it) }
                        textViewBottomPetName.text = "이름 : ${pet.name}"
                        textViewBottomPetAge.text = "나이 : ${petAge}세" //나중에계산해
                        textViewBottomPetBreed.text="견종 : ${pet.breed}"
                        textViewBottomPetPropensity.text="성향 : ${pet.character}"
                        textViewBottomPetGender.text="성별 : ${petSex}"
                        textViewBottomIsPetNeutered.text="중성화 여부 : ${isPetNeutered}"
                        if(pet.imgaeURI !=null){
                            Glide.with(imageViewBottomPetProfileImage.context)
                                .load(pet.imgaeURI)
                                .placeholder(R.drawable.pets_24px)
                                .listener(object : RequestListener<Drawable> {
                                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                                        return false
                                    }

                                    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                        return false
                                    }
                                })
                                .into(imageViewBottomPetProfileImage)
                        } else {
                            imageViewBottomPetProfileImage.setImageResource(R.drawable.pets_24px)
                        }
                    }
                }
                viewModel.walkMatchingCount.observe(viewLifecycleOwner, Observer { count ->
                    Log.d("매칭 기록",count.toString())
                    if(count!=null) {
                        textViewBottomUserMathcingHistory.text = "매칭 기록: ${count}회"
                    }else{
                        textViewBottomUserMathcingHistory.text = "매칭 기록: 0회"
                    }
                })
                if (user.userImage != null) {
                    Glide.with(imageViewUser.context)
                        .load(user.userImage)
                        .placeholder(R.drawable.default_profile_image)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                                return false
                            }

                            override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    onWalkbottomSheetDialog.show()
                                }, 200)
                                return false
                            }
                        })
                        .into(imageViewUser)
                } else {
                    imageViewUser.setImageResource(R.drawable.default_profile_image)
                    Handler(Looper.getMainLooper()).postDelayed({
                        onWalkbottomSheetDialog.show()
                    }, 200)
                }

            }

        }
    }

    //selectedPlace로 구글맵에서 이미지 가져오기
    private suspend fun fetchPlaceImageFromGooglePlacesAPI(
        placesClient: PlacesClient,
        placeId: String?,
    ): Bitmap? {

        if (placeId == null) {
            //place가 없거나 해서 placeId를 못받아온 경우, null 반환
            return null
        }
        // Specify fields. Requests for photos must always have the PHOTO_METADATAS field.
        val fields = listOf(Place.Field.PHOTO_METADATAS)
        val placeRequest = FetchPlaceRequest.newInstance(placeId, fields)

        try {
            val response = placesClient.fetchPlace(placeRequest).await()
            val place = response.place

            // Get the photo metadata.
            val metadata = place.photoMetadatas
            if (metadata == null || metadata.isEmpty()) {
                Log.w("placeImage", "No photo metadata.")
                return null
            }
            val photoMetadata = metadata.first()

            // Create a FetchPhotoRequest.
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(500) // Optional.
                .setMaxHeight(300) // Optional.
                .build()

            val fetchPhotoResponse = placesClient.fetchPhoto(photoRequest).await()

            // 반환할 Bitmap 반환
            return fetchPhotoResponse.bitmap
        } catch (exception: ApiException) {
            Log.e("placeImage", "Place not found: " + exception.message)
            return null
        }
    }

    // Place 검색 함수 (선택한 장소의 주소를 auto complete로 검색)
    private suspend fun searchPlaceWithAutoComplete(
        placesClient: PlacesClient,
        addressName: String,
    ): String? = suspendCoroutine { continuation ->
        // Create a new token for the autocomplete session. Pass this to FindAutocompletePredictionsRequest,
        // and once again when the user makes a selection (for example when calling fetchPlace()).
        val token = AutocompleteSessionToken.newInstance()

        // Create a RectangularBounds object.
        // 대한민국으로 설정
        val bounds = RectangularBounds.newInstance(
            LatLng(34.6008162, 125.8237523),
            LatLng(38.459617, 129.731323)
        )
        // Use the builder to create a FindAutocompletePredictionsRequest.
        val request =
            FindAutocompletePredictionsRequest.builder()
                .setLocationRestriction(bounds)
                .setCountries("KR")
                .setTypesFilter(listOf(PlaceTypes.ADDRESS))
                .setSessionToken(token)
                // 선택한 장소의 주소로 검색
                .setQuery(addressName)
                .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response: FindAutocompletePredictionsResponse ->
                // Get the first prediction if available.
                val firstPrediction = response.autocompletePredictions.firstOrNull()
                val placeId = firstPrediction?.placeId
                continuation.resume(placeId) // Return placeId as the result
            }.addOnFailureListener { exception: Exception? ->
                if (exception is ApiException) {
                    Log.e("placeImage", "Place not found: ${exception.statusCode}")
                    continuation.resume(null) // Return null if an error occurs
                }
            }
    }


    //맵 드래그 -> 맵 중심의 좌표 이동하면 실행되는 메서드
    @Deprecated("Deprecated in Java")
    override fun onMapViewCenterPointMoved(p0: MapView?, p1: MapPoint?) {
        // 새로운 중심에서 검색(onWalk == true면 user검색 false면 place검색)
        if(onWalk == false) {
            p1?.mapPointGeoCoord?.let {
                LastKnownLocation.latitude = it.latitude
                LastKnownLocation.longitude = it.longitude
                viewModel.searchPlacesByKeyword(it.latitude, it.longitude, "동물")

                Log.d("lastlocation", (LastKnownLocation.latitude).toString())
            }
        }else{
            p1?.mapPointGeoCoord?.let {
                viewModel.observeUsersOnWalk()
            }
        }
    }

    //맵의 줌 아웃 제한
    override fun onMapViewZoomLevelChanged(p0: MapView?, zoomLevel: Int) {
        val maxZoomLevel = 3
        if (zoomLevel > maxZoomLevel) {
            p0?.setZoomLevel(maxZoomLevel, true)
        }
    }

    override fun onMapViewSingleTapped(p0: MapView?, p1: MapPoint?) {}
    override fun onMapViewDoubleTapped(p0: MapView?, p1: MapPoint?) {}
    override fun onMapViewLongPressed(p0: MapView?, p1: MapPoint?) {}
    override fun onMapViewDragStarted(p0: MapView?, p1: MapPoint?) {}
    override fun onMapViewDragEnded(p0: MapView?, p1: MapPoint?) {}
    override fun onMapViewMoveFinished(p0: MapView?, p1: MapPoint?) {}
    override fun onCurrentLocationUpdate(
        mapView: net.daum.mf.map.api.MapView?,
        mapPoint: MapPoint?,
        v: Float,
    ) {
    }

    override fun onCurrentLocationDeviceHeadingUpdate(p0: MapView?, p1: Float) {}
    override fun onCurrentLocationUpdateFailed(p0: MapView?) {}
    override fun onCurrentLocationUpdateCancelled(p0: MapView?) {}
    override fun onCalloutBalloonOfPOIItemTouched(p0: MapView?, p1: MapPOIItem?) {}
    override fun onCalloutBalloonOfPOIItemTouched(
        p0: MapView?,
        p1: MapPOIItem?,
        p2: MapPOIItem.CalloutBalloonButtonType?,
    ) {
    }

    override fun onDraggablePOIItemMoved(p0: MapView?, p1: MapPOIItem?, p2: MapPoint?) {}
    override fun onMapViewInitialized(p0: MapView?) {
        p0?.setZoomLevel(2, true)
    }

    override fun onResume() {
        super.onResume()

        if(onWalk == true) {
            requestLocationPermissionIfNeededOnWalk()
        }else{
            requestLocationPermissionIfNeeded()

        }
    }
}


//뷰 모델 팩토리
class WalkViewModelFactory(private val repository: WalkRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalkViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}