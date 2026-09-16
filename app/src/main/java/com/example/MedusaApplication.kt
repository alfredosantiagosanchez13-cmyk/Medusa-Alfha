package com.example

/**
 * MedusaApplication extiende MainApplication para garantizar retrocompatibilidad
 * y hereda la inicialización de canales, base de datos y FirebaseAuthProvider.
 */
class MedusaApplication : MainApplication() {

    companion object {
        val isDatabaseAvailable: Boolean
            get() = MainApplication.isDatabaseAvailable

        val isFirebaseAvailable: Boolean
            get() = MainApplication.isFirebaseAvailable
    }
}

