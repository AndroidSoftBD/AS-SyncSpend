package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExpenseEntity
import com.example.data.model.SyncPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("SyncSpend", appName)
  }

  @Test
  fun `verify sync payload serialization and deserialization`() {
    val expense = ExpenseEntity(
        id = "exp_123",
        title = "Grocery Store",
        amount = 45.50,
        type = "EXPENSE",
        category = "Food & Dining",
        accountName = "Main Card"
    )

    val payload = SyncPayload(
        eventId = "evt_001",
        vaultCode = "SPEND-1000",
        action = "UPSERT_EXPENSE",
        timestamp = 1791000000L,
        deviceId = "dev_alpha",
        deviceName = "Pixel 8",
        expense = expense
    )

    val jsonString = payload.toJsonString()
    assertNotNull(jsonString)

    val parsed = SyncPayload.fromJsonString(jsonString)
    assertNotNull(parsed)
    assertEquals("evt_001", parsed?.eventId)
    assertEquals("SPEND-1000", parsed?.vaultCode)
    assertEquals("UPSERT_EXPENSE", parsed?.action)
    assertEquals("dev_alpha", parsed?.deviceId)
    assertEquals("Grocery Store", parsed?.expense?.title)
    assertEquals(45.50, parsed?.expense?.amount ?: 0.0, 0.001)
  }
}
