package io.github.vikulin.opengammakit.viewmodel

import androidx.lifecycle.ViewModel
import com.github.mikephil.charting.data.Entry
import org.maplibre.android.camera.CameraPosition

class MapViewModel : ViewModel() {
    var cameraPosition: CameraPosition? = null
    var styleLoaded = false
}
