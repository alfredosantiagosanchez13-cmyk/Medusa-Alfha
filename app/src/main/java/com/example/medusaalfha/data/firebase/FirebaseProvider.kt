package com.example.medusaalfha.data.firebase

import android.content.Context
import com.example.medusaalfha.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Proveedor centralizado de instancias de Firebase.
 * Garantiza que Firestore SIEMPRE se inicialice con el databaseId personalizado
 * especificado en R.string.firestore_database_id (evitando la instancia por defecto).
 */
object FirebaseProvider {

    @Volatile
    private var firestoreInstance: FirebaseFirestore? = null

    fun getFirestore(context: Context): FirebaseFirestore {
        return firestoreInstance ?: synchronized(this) {
            firestoreInstance ?: run {
                val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                FirebaseFirestore.getInstance(dbId).also { firestoreInstance = it }
            }
        }
    }

    fun getAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
}
