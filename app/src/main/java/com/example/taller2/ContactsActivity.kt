package com.example.taller2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller2.MIscelanius.Companion.PERMISSION_READ_CONTACTS
import com.example.taller2.databinding.ActivityContactsBinding

class ContactsActivity : AppCompatActivity() {
    lateinit var binding: ActivityContactsBinding
    private lateinit var mlista: ListView
    private var mContactsAdapter: ContactsAdapter? = null // Usará la clase interna
    private var mCursor: Cursor? = null

    private val mProjection: Array<String> = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mlista = findViewById(R.id.listViewContactos)

        mContactsAdapter = ContactsAdapter(this, null, 0)

        mlista.adapter = mContactsAdapter

        permisos()
    }


    private fun permisos() {
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                //Lo que tenga que hacer
                loadContacts()

            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.READ_CONTACTS
            ) -> {

                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_CONTACTS),
                    PERMISSION_READ_CONTACTS
                )
            }

            else -> {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_CONTACTS),
                    PERMISSION_READ_CONTACTS
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
            PERMISSION_READ_CONTACTS -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Lo que tengas que hacer X2
                    loadContacts()
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


    private inner class ContactsAdapter(context: Context?, c: Cursor?, flags: Int) :
        CursorAdapter(context, c, flags) {

        private val displayNameIndex: Int =
            c?.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY) ?: -1

        override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
            return LayoutInflater.from(context).inflate(R.layout.list_item_contacto, parent, false)
        }

        override fun bindView(view: View?, context: Context?, cursor: Cursor?) {
            val tvIndice = view?.findViewById<TextView>(R.id.textViewIndiceContacto)
            val tvNombre = view?.findViewById<TextView>(R.id.textViewNombreContacto)
            val ivIcono = view?.findViewById<ImageView>(R.id.iconoContacto)

            // Obtener índice de columna del nombre
            val displayNameIndex: Int = cursor?.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY) ?: -1

            // Leer el nombre del cursor. getString devuelve String? (puede ser null)
            val nombre: String? = if (displayNameIndex != -1) {
                cursor?.getString(displayNameIndex)
            } else {
                // Es buena idea loguear si el índice es inválido, aunque no debería pasar
                Log.w("ContactsAdapter", "Índice de columna de nombre inválido.")
                null // Si el índice es malo, tratamos el nombre como nulo
            }

            // Obtener la posición para mostrar el índice (1, 2, 3...)
            val indice = (cursor?.position ?: -1) + 1

            // Asignar el texto del índice
            tvIndice?.text = indice.toString()

            // Asignar el texto del nombre:
            // Usamos isNullOrEmpty para considerar también nombres vacíos "" como "Sin nombre"
            tvNombre?.text = if (nombre.isNullOrEmpty()) {
                "Sin nombre" // Texto alternativo si es null O vacío
            } else {
                nombre // El nombre real si existe y no está vacío
            }

            // Asignar el icono
            ivIcono?.setImageResource(R.drawable.contacto) // Cambia por tu icono si es diferente
        }

    }

    private fun loadContacts() {
        Log.d("ContactsActivity", "loadContacts() llamado")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {

            mCursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, mProjection, null, null, null
            )

            mContactsAdapter?.changeCursor(mCursor)

        } else {
            Toast.makeText(this, "No hay permiso, reduciendo funcionalidades", Toast.LENGTH_SHORT).show()
        }
    }

}