package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Somet AI", appName)
  }

  @Test
  fun `name setup preference updates correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsManager = PreferencesManager(context)
    prefsManager.completeNameSetup("Saad Rehman")
    assertEquals("Saad Rehman", prefsManager.userName.value)
    assertTrue(prefsManager.hasNameSetup.value)
  }

  @Test
  fun `default AI model is mini_flash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsManager = PreferencesManager(context)
    assertEquals("mini_flash", prefsManager.selectedModel.value)
  }
}

