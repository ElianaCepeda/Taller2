package com.example.taller2

import android.app.Activity
import android.content.ActivityNotFoundException
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
import android.net.Uri // Importar Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.Date // Importar Date
import java.text.SimpleDateFormat // Importar SimpleDateFormat
import java.util.Locale
import android.content.ContentValues
import android.content.Context
import android.os.Build
import com.example.taller2.MIscelanius.Companion.PERMISSION_READ_STORAGE
import java.io.OutputStream

class CameraActivity : AppCompatActivity() {
    lateinit var binding: ActivityCameraBinding
    private lateinit var currentPhotoUri: Uri // Variable para guardar la Uri de la foto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cargarImagenPrevia()
        binding.buttonGallery.setOnClickListener {
            permisosGallery()
        }

        binding.buttonCamera.setOnClickListener {
            permisosCamara()
        }

    }
//-------------------OnRequestPermissionsResult---------------------------------
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
                    abrirCamara()
                } else {
                    Toast.makeText(this, "Permiso para acceder a camara denegado, reduciendo funcionalidades", Toast.LENGTH_SHORT).show()
                }
                return
            }

            PERMISSION_READ_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido, ahora sí abre la galería
                    abrirGaleria()
                } else {
                    Toast.makeText(this, "Permiso para acceder a galería denegado, reduciendo funcionalidades", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
            }
        }
    }

//-------------------Fin OnRequestPermissionsResult---------------------------------



//--------------------Manejo de permisos de camara, tomar foto y guardar en galeria--------------------------------
    private fun permisosCamara() {
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                //Lo que tenga que hacer
                abrirCamara()

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



    // Lanzador para manejar el resultado de la cámara
    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // La imagen se guardó en 'currentPhotoUri', ya NO viene en 'result.data'
            if (::currentPhotoUri.isInitialized) {
                // Carga la imagen desde la Uri en el ImageView
                binding.imageViewFoto.setImageURI(currentPhotoUri)

                // 2. Llama a la función para guardar en la galería pública
                guardarImagenEnGaleria(currentPhotoUri)
                // Opcional: Notificar al Media Scanner (si quieres que aparezca en Galerías externas)
                // scanFile(currentPhotoUri)
                Toast.makeText(this, "Foto guardada en $currentPhotoUri", Toast.LENGTH_LONG).show() // Muestra la Uri
            } else {
                Toast.makeText(this, "Error: Uri de foto no encontrada.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Captura cancelada", Toast.LENGTH_SHORT).show()
        }
    }


    // Metodo para abrir la cámara
    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Crear el archivo y obtener la Uri usando FileProvider
        try {
            val photoFile: File = createImageFile()
            // Guardar la Uri para usarla en el resultado del launcher
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "com.example.taller2.provider", // DEBE COINCIDIR con android:authorities en Manifest
                photoFile
            )
            // Añadir la Uri al Intent como EXTRA_OUTPUT
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)

            // Lanzar el intent
            tomarFotoLauncher.launch(intent)

        } catch (ex: IOException) {
            // Error creando el archivo
            Log.e("CameraActivity", "Error al crear archivo de imagen", ex)
            Toast.makeText(this, "Error al preparar archivo para la foto.", Toast.LENGTH_SHORT).show()
        } catch (ex: ActivityNotFoundException) {
            // No se encontró app de cámara
            Toast.makeText(this, "No se encontró una aplicación de cámara.", Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            // Otro error inesperado
            Log.e("CameraActivity", "Error inesperado al abrir cámara", ex)
            Toast.makeText(this, "Error inesperado al abrir cámara.", Toast.LENGTH_SHORT).show()
        }
    }



    // --- Metodo para crear el archivo de imagen ---
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Crear un nombre de archivo único basado en la fecha/hora
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES) // Directorio específico de la app
        if (storageDir == null) {
            throw IOException("No se pudo obtener el directorio de almacenamiento externo.")
        }
        if (!storageDir.exists()) {
            storageDir.mkdirs() // Crea el directorio si no existe
        }
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefijo */
            ".jpg", /* sufijo */
            storageDir /* directorio */
        ).apply {
            // Opcional: Podrías guardar la ruta absoluta si la necesitaras más tarde
            // currentPhotoPath = absolutePath
        }
    }


    // --- Nueva Función para Guardar en Galería (API 29+) ---
    private fun guardarImagenEnGaleria(sourceUri: Uri) {
        // Funciona mejor en Android 10 (API 29) y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentResolver = applicationContext.contentResolver
            // Genera un nombre único para el archivo en la galería
            val nombreImagen = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"

            // Prepara los detalles (metadatos) de la imagen para MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nombreImagen)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                // Guarda en la carpeta Pictures estándar
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                // Marca como pendiente mientras copiamos los datos
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            var outputStream: OutputStream? = null
            var destinationUri: Uri? = null

            try {
                // Inserta la entrada en MediaStore y obtén la Uri de destino
                destinationUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                if (destinationUri == null) {
                    throw IOException("No se pudo crear la entrada en MediaStore")
                }

                // Abre un stream de salida hacia la Uri de destino en MediaStore
                outputStream = contentResolver.openOutputStream(destinationUri)
                if (outputStream == null) {
                    throw IOException("No se pudo obtener OutputStream para la Uri de destino")
                }

                // Abre un stream de entrada desde la Uri de origen (nuestro archivo privado)
                contentResolver.openInputStream(sourceUri).use { inputStream ->
                    if (inputStream == null) {
                        throw IOException("No se pudo obtener InputStream de la Uri de origen")
                    }
                    // Copia los bytes del archivo privado al archivo de MediaStore
                    inputStream.copyTo(outputStream)
                }

                // La copia fue exitosa, marca la imagen como NO pendiente
                values.clear() // Limpia los valores anteriores
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(destinationUri, values, null, null)

                Toast.makeText(this, "Imagen guardada en la galería", Toast.LENGTH_SHORT).show()

                if (destinationUri != null) {
                    saveLastImageUri(destinationUri)
                }

            } catch (e: Exception) { // Captura genérica para IO, Security, etc.
                Log.e("CameraActivity", "Error al guardar en galería", e)
                Toast.makeText(this, "Error al guardar en galería: ${e.message}", Toast.LENGTH_LONG).show()

                // Si algo falló, intenta eliminar la entrada pendiente en MediaStore si se creó
                if (destinationUri != null) {
                    try {
                        contentResolver.delete(destinationUri, null, null)
                    } catch (deleteEx: Exception) {
                        Log.e("CameraActivity", "Error al eliminar entrada fallida de MediaStore", deleteEx)
                    }
                }
            } finally {
                // Asegúrate de cerrar el OutputStream
                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    Log.e("CameraActivity", "Error al cerrar OutputStream", e)
                }
            }
        } else {
            // Para versiones anteriores a Android 10 (API < 29)
            // Se necesitaría el permiso WRITE_EXTERNAL_STORAGE y otra lógica.
            // Por simplicidad, podrías solo notificar al usuario o no hacer nada.
            Toast.makeText(this, "Guardado en galería automático requiere Android 10+", Toast.LENGTH_LONG).show()
            // Opcionalmente, podrías intentar usar MediaScanner para la URI privada, aunque no garantiza
            // que todas las galerías la muestren si está en el directorio privado.
            // scanFile(sourceUri) // Descomenta si quieres probar esto en versiones antiguas
        }
    }


    // --- Función para GUARDAR la Uri en SharedPreferences ---
    private fun saveLastImageUri(uri: Uri) {
        // Obtiene una instancia de SharedPreferences (elige un nombre, e.g., "camera_prefs")
        val sharedPref = getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)
        // Usa 'edit()' para modificar las preferencias
        with(sharedPref.edit()) {
            // Guarda la Uri convertida a String con una clave ("last_image_uri")
            putString("last_image_uri", uri.toString())
            // Aplica los cambios (apply es asíncrono, commit es síncrono)
            apply()
        }
    }

    // --- Función para CARGAR la Uri desde SharedPreferences ---
    private fun loadLastImageUri(): Uri? {
        val sharedPref = getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)
        // Obtiene el String guardado, devuelve null si no existe
        val uriString = sharedPref.getString("last_image_uri", null)
        // Si se encontró un String, conviértelo de nuevo a Uri
        return if (uriString != null) {
            try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                // Si la Uri guardada es inválida por alguna razón
                Log.e("CameraActivity", "Error al parsear Uri guardada", e)
                null
            }
        } else {
            null // No había ninguna Uri guardada
        }
    }

    private fun cargarImagenPrevia() {
        // 1. Intenta cargar la Uri guardada
        val lastImageUri = loadLastImageUri()

        // 2. Lógica para mostrar la imagen o el placeholder
        if (lastImageUri != null) {
            try {
                binding.imageViewFoto.setImageURI(lastImageUri)
                Log.d("CameraActivity", "Última imagen cargada: $lastImageUri")
            } catch (e: SecurityException) {
                Log.e("CameraActivity", "Error de seguridad al cargar la imagen", e)
                Toast.makeText(this, "No se pudo cargar la imagen anterior (permiso?)", Toast.LENGTH_SHORT).show()
                // Mostrar placeholder si falla
                mostrarPlaceholder() // Llama a función auxiliar (opcional)
            } catch (e: Exception) {
                Log.e("CameraActivity", "Error al cargar la imagen: ${e.message}", e)
                Toast.makeText(this, "No se pudo cargar la imagen anterior.", Toast.LENGTH_SHORT).show()
                mostrarPlaceholder() // Llama a función auxiliar (opcional)
            }
        } else {
            // No hay imagen guardada, asegúrate que se muestre el placeholder
            Log.d("CameraActivity", "No hay imagen anterior guardada.")
            mostrarPlaceholder() // Llama a función auxiliar (opcional)
        }
    }

    // Función auxiliar opcional para establecer el placeholder (evita repetición)
    private fun mostrarPlaceholder() {
        // Asegúrate de que 'camera_placeholder' exista en res/drawable
        binding.imageViewFoto.setImageResource(R.drawable.camera)
    }

//--------------------Fin Manejo de permisos de camara, tomar foto y guardar en galeria--------------------------------



//--------------------Manejo de permisos de galeria y guardar foto --------------------------------
    private fun permisosGallery() {
        // Determina qué permiso necesitas basado en la versión de Android
        val permisoRequerido = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // TIRAMISU = API 33
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(
                this, permisoRequerido
            ) == PackageManager.PERMISSION_GRANTED -> {
                //Lo que tenga que hacer
                abrirGaleria()

            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,permisoRequerido
            ) -> {

                requestPermissions(
                    arrayOf(permisoRequerido),
                    PERMISSION_READ_STORAGE
                )
            }

            else -> {
                requestPermissions(
                    arrayOf(permisoRequerido),
                    PERMISSION_READ_STORAGE
                )
            }
        }

    }


    // --- Lanzador para manejar el resultado de la galería ---
    private val seleccionarImagenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Obtiene la Uri de la imagen seleccionada
            val selectedImageUri: Uri? = result.data?.data
            if (selectedImageUri != null) {
                // 1. Muestra la imagen seleccionada en el ImageView
                binding.imageViewFoto.setImageURI(selectedImageUri)
                Log.d("CameraActivity", "Imagen seleccionada de galería: $selectedImageUri")

                // 2. Guarda esta Uri como la última imagen usada (para persistencia)
                saveLastImageUri(selectedImageUri)

            } else {
                Toast.makeText(this, "No se pudo obtener la imagen de la galería", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("CameraActivity", "Selección de galería cancelada")
            // No es necesario un Toast si el usuario simplemente canceló
        }
    }


    // --- Función para lanzar el Intent de la galería ---
    private fun abrirGaleria() {
        // Intent para seleccionar imágenes desde el almacenamiento externo
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        // Alternativa: Intent(Intent.ACTION_GET_CONTENT) que es más general
        // intent.type = "image/*" // Puedes añadir esto si usas ACTION_GET_CONTENT

        try {
            seleccionarImagenLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No se encontró una aplicación de galería.", Toast.LENGTH_SHORT).show()
            Log.e("CameraActivity", "No gallery app found", e)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir la galería.", Toast.LENGTH_SHORT).show()
            Log.e("CameraActivity", "Error opening gallery", e)
        }
    }

}