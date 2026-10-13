package com.example.medusaalfha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.medusaalfha.data.firebase.AppCheckHelper
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppCheckIntegrationTest {

    @Test
    fun testAppCheckHelperInitialization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)
        AppCheckHelper.initializeAppCheck(context)
    }
}
