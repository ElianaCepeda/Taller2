package com.example.taller2

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller2.MIscelanius.Companion.PERMISSION_CAMERA
import com.example.taller2.MIscelanius.Companion.PERMISSION_READ_CONTACTS
import com.example.taller2.databinding.ActivityCameraBinding

class CameraActivity : AppCompatActivity() {
    lateinit var binding: ActivityCameraBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permisosCamara()

    }


    private fun permisosCamara() {
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                //Lo que tenga que hacer

            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.CAMERA
            ) -> {

                requestPermissions(
                    arrayOf(android.Manifest.permission.CAMERA),
                    PERMISSION_CAMERA
                )
            }

            else -> {
                requestPermissions(
                    arrayOf(android.Manifest.permission.CAMERA),
                    PERMISSION_CAMERA
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
            PERMISSION_CAMERA -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Lo que tengas que hacer X2
                } else {
                    //No haga nada
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    // Lanzador para manejar el resultado de la cámara
    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                binding.imageViewFoto.setImageBitmap(imageBitmap)
                Toast.makeText(this, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No se pudo obtener la imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Metodo para abrir la cámara
    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            tomarFotoLauncher.launch(intent)
        } else {
            Toast.makeText(this, "No se encontró una aplicación de cámara", Toast.LENGTH_SHORT).show()
        }
    }
}