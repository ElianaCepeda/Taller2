package com.example.taller2

import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.MIscelanius.Companion.PERMISSION_LOCATION

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.taller2.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.Marker

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    lateinit var mFusedLocationClient: FusedLocationProviderClient
    lateinit var mLocationRequest: LocationRequest
    lateinit var mLocationCallback: LocationCallback

    private var currentLocationMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //Iicializa el proveedor
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mLocationRequest = createLocationRequest()

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                Log.i("LOCATION", "Location update in the callback: $location")
                if (location != null) {
                    updateMapUI(location)
                }
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true


        // Verifica permisos aquí ANTES de intentar configurar la ubicación inicial
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            setupInitialLocation()
        } else {
            // Si no hay permisos, solicítalos. setupInitialLocation se llamará desde onRequestPermissionsResult si se otorgan.
            permisosUbicacion()
        }

    }

    private fun permisosUbicacion() {

        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                //Lo que tenga que hacer
                if (::mMap.isInitialized) {
                    setupInitialLocation()
                } else {
                    // El mapa no está listo aún, onMapReady se encargará
                    Log.d("Permissions", "Permisos OK, esperando a onMapReady.")
                }

            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {

                requestPermissions(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_LOCATION
                )
            }

            else -> {
                requestPermissions(
                    arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSION_LOCATION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Lo que tengas que hacer X2

                    // Permiso concedido, configura la ubicación inicial si el mapa está listo
                    if (::mMap.isInitialized) {
                        setupInitialLocation()
                    } else {
                        // El mapa no está listo aún, onMapReady se encargará
                        Log.d("PermissionsResult", "Permiso concedido, esperando a onMapReady.")
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Permiso para acceder a camara denegado, reduciendo funcionalidades",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun setupInitialLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("InitialLocation", "Permisos no concedidos al intentar configurar ubicación inicial.")
            // Si llegas aquí, algo falló en el flujo de permisos anterior.
            // Podrías volver a pedirlos o mostrar un mensaje.
            permisosUbicacion() // Intenta pedir permisos de nuevo
            return
        }

        // Habilita la capa "My Location" (muestra el punto azul y el botón de centrar)
        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true // Asegúrate que el botón sea visible

        // Intenta obtener la última ubicación conocida rápidamente
        mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                Log.d("InitialLocation", "Última ubicación conocida obtenida: ${location.latitude}, ${location.longitude}")
                // Mueve la cámara a la última ubicación conocida al inicio
                val initialLatLng = LatLng(location.latitude, location.longitude)


                mMap.addMarker(MarkerOptions().position(initialLatLng).title("Ubicación actual"))
                mMap.moveCamera(CameraUpdateFactory.zoomTo(15F))
                mMap.moveCamera(CameraUpdateFactory.newLatLng(initialLatLng))
                // Podrías llamar a updateMapUI(location) aquí también si quieres el marcador inicial
                updateMapUI(location)
            } else {
                Log.d("InitialLocation", "No se pudo obtener la última ubicación conocida (puede ser null al inicio).")
                // Puedes poner una ubicación por defecto o esperar la primera actualización del callback
                // val bogota = LatLng(4.60971, -74.08175) // Ejemplo: Bogotá
                // mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 12f))
            }

            // ¡Importante! Inicia las actualizaciones periódicas DESPUÉS de configurar el mapa inicial
            // y asegurarte de tener permisos.
            startLocationUpdates()

        }.addOnFailureListener { e ->
            Log.e("InitialLocation", "Error al obtener la última ubicación conocida.", e)
            // Iniciar actualizaciones de todos modos, puede que la primera falle pero las siguientes funcionen
            startLocationUpdates()
        }


    }

    private fun updateMapUI(location: Location) {
        // Verifica si el mapa (mMap) ya ha sido inicializado
        if (!::mMap.isInitialized) {
            Log.e("MapUI", "El mapa (mMap) no está listo todavía.")
            return
        }

        // Crea un objeto LatLng con la nueva ubicación
        val newLatLng = LatLng(location.latitude, location.longitude)

        // --- Opciones para actualizar el mapa ---

        // Opción 1: Mover la cámara y colocar/mover un marcador
        // Borra el marcador anterior si existe
        currentLocationMarker?.remove()
        // Añade un nuevo marcador en la nueva posición
        currentLocationMarker = mMap.addMarker(MarkerOptions().position(newLatLng).title("Mi Ubicación Actual"))
        // Mueve la cámara suavemente a la nueva ubicación con un nivel de zoom adecuado (ej. 15f)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))

        // Opción 2: Solo usar la capa "My Location" (punto azul)
        // Si solo quieres el punto azul y el botón de centrar, asegúrate de que esté habilitado
        // y la cámara se mueva (ya lo hace la línea animateCamera de arriba).
        // Esta opción es más simple si no necesitas un marcador personalizado.
        // Si usas esta opción, puedes comentar o quitar las líneas de `currentLocationMarker`.

        Log.d("MapUI", "Mapa actualizado a: ${location.latitude}, ${location.longitude}")
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null)
        }
    }

    private fun createLocationRequest(): LocationRequest =
// New builder
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,10000).apply {
            setMinUpdateIntervalMillis(5000)
        }.build()

}