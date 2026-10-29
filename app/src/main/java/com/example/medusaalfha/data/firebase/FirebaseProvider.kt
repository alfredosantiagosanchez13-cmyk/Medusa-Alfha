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
                val dbId = try {
                    context.applicationContext.getString(R.string.firestore_database_id).trim()
                } catch (_: Exception) {
                    ""
                }
                val instance = if (dbId.isEmpty() || dbId == "(default)") {
                    android.util.Log.d("FirebaseProvider", "Conectando a Cloud Firestore con base de datos por defecto (default).")
                    FirebaseFirestore.getInstance()
                } else {
                    try {
                        android.util.Log.d("FirebaseProvider", "Conectando a Cloud Firestore con databaseId: $dbId")
                        FirebaseFirestore.getInstance(dbId)
                    } catch (e: Exception) {
                        android.util.Log.w("FirebaseProvider", "No se pudo conectar a custom dbId '$dbId', usando instancia (default): ${e.message}")
                        FirebaseFirestore.getInstance()
                    }
                }
                instance.also { firestoreInstance = it }
            }
        }
    }

    fun getAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
}
