package io.github.vikulin.opengammakit.viewmodel

import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition

class MapViewModel : ViewModel() {
    var cameraPosition: CameraPosition? = null
    var mapType = GoogleMap.MAP_TYPE_SATELLITE
}
