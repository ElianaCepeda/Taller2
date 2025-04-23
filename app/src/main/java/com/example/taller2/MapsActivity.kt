package com.example.taller2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.taller2.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private const val UPDATE_INTERVAL_MS = 10000L
private const val FASTEST_UPDATE_INTERVAL_MS = 5000L
private const val MIN_DISTANCE_CHANGE_FOR_UPDATES = 30

private const val LOCATION_LOG_FILE = "location_log.json"

@Serializable
data class LocationEntry(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentLocationMarker: Marker? = null
    private var searchedLocationMarker: Marker? = null
    private var longClickMarker: Marker? = null
    private var lastKnownLocation: Location? = null
    private var lastLoggedLocation: Location? = null

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var currentLuminosity: Float = -1f

    private val LIGHT_THRESHOLD_DARK = 10f
    private val LIGHT_THRESHOLD_LIGHT = 50f


    private lateinit var geocoder: Geocoder


    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            Toast.makeText(this, "Permiso de ubicación concedido.", Toast.LENGTH_SHORT).show()
            if (::mMap.isInitialized) {
                try {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                } catch (e: SecurityException) {
                    Log.e("Location", "SecurityException enabling My Location layer after permission granted", e)
                    Toast.makeText(this@MapsActivity, "Error de seguridad (capa ubicación).", Toast.LENGTH_SHORT).show()
                }
                setupLocationServicesAndStartUpdates()
            } else {
                Log.d("Permissions", "Permiso concedido, esperando a onMapReady para configurar ubicación.")
            }

        } else {
            Toast.makeText(this, "Permiso de ubicación denegado. El mapa podría no mostrar tu posición actual.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            Toast.makeText(this, "Sensor de luminosidad no disponible en este dispositivo.", Toast.LENGTH_LONG).show()
        }

        geocoder = Geocoder(this, Locale.getDefault())




        setupSearchListener()
    }

    override fun onResume() {
        super.onResume()
        if (checkLocationPermissions()) {
            startLocationUpdates()
        }

        lightSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true

        setupMapListeners()

        if (checkLocationPermissions()) {
            setupLocationServicesAndStartUpdates()
            try {
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = true
            } catch (e: SecurityException) {
                Log.e("MapReady", "SecurityException enabling My Location layer in onMapReady", e)
                Toast.makeText(this, "Error de seguridad (capa ubicación).", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }


        applyMapStyle(currentLuminosity)
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupLocationServicesAndStartUpdates() {
        if (!checkLocationPermissions()) {
            Log.w("Location", "Location permissions not granted. Cannot setup services.")
            Toast.makeText(this, "Permisos de ubicación no concedidos.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("Location", "Last known location obtained: ${location.latitude}, ${location.longitude}")
                    lastKnownLocation = location

                    lastLoggedLocation = location

                    updateMapUI(location)

                    val initialLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15f))

                } else {
                    Log.d("Location", "Last known location is null. Will wait for first update.")

                }


                startLocationUpdates()

            }.addOnFailureListener { e ->
                Log.e("Location", "Error getting last known location: ${e.message}", e)

                startLocationUpdates()
            }
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException when calling fusedLocationClient.lastLocation: ${e.message}", e)
            Toast.makeText(this, "Error de seguridad al obtener última ubicación.", Toast.LENGTH_SHORT).show()

            startLocationUpdates()
        }
    }


    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL_MS)

        }.build()
    }

    private fun createLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d("LocationCallback", "Location update received: ${location.latitude}, ${location.longitude}")
                    handleLocationUpdate(location)
                }
            }
        }
    }

    private fun handleLocationUpdate(location: Location) {
        lastKnownLocation = location

        if (!::mMap.isInitialized) {
            Log.e("MapUI", "Map is not initialized, cannot handle location update.")
            return
        }

        val distanceToLastLogged = lastLoggedLocation?.distanceTo(location) ?: MIN_DISTANCE_CHANGE_FOR_UPDATES.toFloat() + 1f

        if (distanceToLastLogged >= MIN_DISTANCE_CHANGE_FOR_UPDATES) {
            Log.d("LocationCallback", "Moved >= $MIN_DISTANCE_CHANGE_FOR_UPDATES meters. Distance: $distanceToLastLogged")

            updateMapUI(location)

            logLocationToJson(location)

            lastLoggedLocation = location

            calculateAndShowDistance(location)

        } else {
            Log.d("LocationCallback", "Moved < $MIN_DISTANCE_CHANGE_FOR_UPDATES meters. Distance: ${String.format("%.2f", distanceToLastLogged)}")
        }
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermissions()) {
            Log.w("Location", "Cannot start location updates: permissions not granted.")
            Toast.makeText(this, "Permisos de ubicación no concedidos para iniciar actualizaciones.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!::fusedLocationClient.isInitialized || !::locationCallback.isInitialized || !::locationRequest.isInitialized) {
            Log.w("Location", "Location clients/requests not initialized. Cannot start updates.")
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */)
            Log.d("Location", "Location updates started.")
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException when calling requestLocationUpdates: ${e.message}", e)
            Toast.makeText(this, "Error de seguridad al iniciar actualizaciones de ubicación.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("Location", "Location updates stopped.")
        }
    }

    private fun updateMapUI(location: Location) {
        if (!::mMap.isInitialized) {
            Log.e("MapUI", "Map is not initialized, cannot update UI.")
            return
        }

        val newLatLng = LatLng(location.latitude, location.longitude)

        currentLocationMarker?.remove()

        currentLocationMarker = mMap.addMarker(MarkerOptions().position(newLatLng).title("Mi Ubicación Actual"))


        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))

        Log.d("MapUI", "Mapa actualizado a: ${location.latitude}, ${location.longitude}")
    }


    // --- JSON Logging  (Punto 4) ---

    private fun logLocationToJson(location: Location) {
        val jsonFile = File(filesDir, LOCATION_LOG_FILE)

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val newEntry = LocationEntry(location.latitude, location.longitude, timestamp)

        lifecycleScope.launch(Dispatchers.IO) {
            val existingEntries = try {
                if (jsonFile.exists()) {
                    Json.decodeFromString<MutableList<LocationEntry>>(jsonFile.readText())
                } else {
                    mutableListOf()
                }
            } catch (e: Exception) {
                Log.e("JSONLog", "Error reading existing JSON log file: ${e.message}", e)
                mutableListOf()
            }

            existingEntries.add(newEntry)

            try {
                jsonFile.writeText(Json.encodeToString(existingEntries))
                Log.d("JSONLog", "Logged location to JSON: Lat=${newEntry.latitude}, Lon=${newEntry.longitude}, Time=${newEntry.timestamp}. File size: ${jsonFile.length()} bytes.")
            } catch (e: Exception) {
                Log.e("JSONLog", "Error writing to JSON log file: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, "Error al guardar ubicación en archivo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // ---  (Punto 6) ---

    private fun setupSearchListener() {
        binding.editTextAddress.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || event == null) {
                val address = binding.editTextAddress.text.toString()
                if (address.isNotEmpty()) {
                    geocodeAddress(address)

                    true
                } else {
                    Toast.makeText(this, "Ingresa una dirección.", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                false
            }
        }

        binding.buttonSearch.setOnClickListener {
            val address = binding.editTextAddress.text.toString()
            if (address.isNotEmpty()) {
                geocodeAddress(address)

            } else {
                Toast.makeText(this, "Ingresa una dirección.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun geocodeAddress(addressString: String) {
        if (!::mMap.isInitialized) {
            Toast.makeText(this, "Mapa no listo para buscar dirección.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "Servicio de Geocoder no disponible en este dispositivo.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = geocoder.getFromLocationName(addressString, 1)

                withContext(Dispatchers.Main) {
                    if (!results.isNullOrEmpty()) {
                        val location = results[0]
                        val latLng = LatLng(location.latitude, location.longitude)
                        val formattedAddress = location.getAddressLine(0) ?: addressString

                        searchedLocationMarker?.remove()

                        searchedLocationMarker = mMap.addMarker(
                            MarkerOptions().position(latLng).title(formattedAddress)
                        )

                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                        Toast.makeText(this@MapsActivity, "Dirección encontrada: $formattedAddress", Toast.LENGTH_LONG).show()

                        lastKnownLocation?.let { currentLocation ->
                            calculateAndShowDistance(currentLocation)
                        }


                    } else {
                        Toast.makeText(this@MapsActivity, "Dirección no encontrada.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("Geocoder", "Error geocoding address: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, "Error de servicio de geocodificación.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Geocoder", "Unexpected error geocoding address: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, "Error al buscar dirección.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // ---  (Punto 7) ---

    private fun setupMapListeners() {
        mMap.setOnMapLongClickListener { latLng ->
            Log.d("MapListener", "Map LongClick at: ${latLng.latitude}, ${latLng.longitude}")
            reverseGeocodeLocation(latLng)
        }


    }

    private fun reverseGeocodeLocation(latLng: LatLng) {
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "Servicio de Geocoder no disponible en este dispositivo.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

                withContext(Dispatchers.Main) {
                    val addressText = if (!results.isNullOrEmpty()) {
                        results[0].getAddressLine(0) ?: "Dirección desconocida"
                    } else {
                        "Dirección desconocida"
                    }

                    longClickMarker?.remove()

                    longClickMarker = mMap.addMarker(
                        MarkerOptions().position(latLng).title(addressText)
                    )

                    Toast.makeText(this@MapsActivity, "Marcador en: $addressText", Toast.LENGTH_LONG).show()

                    lastKnownLocation?.let { currentLocation ->
                        calculateAndShowDistance(currentLocation)
                    }
                }
            } catch (e: IOException) {
                Log.e("Geocoder", "Error reverse geocoding location: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, "Error obteniendo dirección.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Geocoder", "Unexpected error reverse geocoding location: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, "Error al buscar dirección.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // --- Calculo de distancia (Punto 8) ---

    private fun calculateAndShowDistance(currentLocation: Location) {
        val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)

        var handledDistance = false

        searchedLocationMarker?.position?.let { searchedLatLng ->
            val distanceInMeters = FloatArray(1)
            Location.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude,
                searchedLatLng.latitude, searchedLatLng.longitude,
                distanceInMeters
            )
            showDistanceToast("Distancia a dirección buscada: ${String.format("%.2f", distanceInMeters[0])} metros")


            handledDistance = true

        }

        if (!handledDistance) {
            longClickMarker?.position?.let { longClickLatLng ->
                val distanceInMeters = FloatArray(1)
                Location.distanceBetween(
                    currentLatLng.latitude, currentLatLng.longitude,
                    longClickLatLng.latitude, longClickLatLng.longitude,
                    distanceInMeters
                )
                showDistanceToast("Distancia a marcador de LongClick: ${String.format("%.2f", distanceInMeters[0])} metros")

                handledDistance = true
            }
        }

    }

    private fun showDistanceToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    // --- Luminosidad (Punto 5) ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val newLuminosity = event.values[0]
            if (abs(newLuminosity - currentLuminosity) > 1.0f || currentLuminosity < 0) {
                currentLuminosity = newLuminosity
                Log.d("Sensor", "Luminosity changed: $currentLuminosity lux. Applying map style.")
                applyMapStyle(currentLuminosity)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun applyMapStyle(luminosity: Float) {
        if (!::mMap.isInitialized) {
            Log.w("MapStyle", "Map not initialized, cannot apply style.")
            return
        }
        try {
            val styleJsonResource = when {
                luminosity < LIGHT_THRESHOLD_DARK -> {
                    Log.d("MapStyle", "Luminosity ($luminosity lux) < $LIGHT_THRESHOLD_DARK. Applying Dark style.")
                    R.raw.map_style_dark
                }
                luminosity > LIGHT_THRESHOLD_LIGHT -> {
                    Log.d("MapStyle", "Luminosity ($luminosity lux) > $LIGHT_THRESHOLD_LIGHT. Applying Light style.")
                    R.raw.map_style_light
                }
                else -> {
                    Log.d("MapStyle", "Luminosity ($luminosity lux) is between thresholds. Applying Default style.")
                    mMap.setMapStyle(null)
                    return
                }
            }
            val success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleJsonResource))
            if (!success) {
                Log.e("MapStyle", "Style parsing failed. Check your JSON format.")
            }
        } catch (e: Exception) {
            Log.e("MapStyle", "Error applying map style: ${e.message}", e)
        }
    }





    // --- onDestroy ---
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
        if (::mMap.isInitialized) {
            mMap.clear()
        }
        currentLocationMarker?.remove()
        searchedLocationMarker?.remove()
        longClickMarker?.remove()
        currentLocationMarker = null
        searchedLocationMarker = null
        longClickMarker = null

    }
}