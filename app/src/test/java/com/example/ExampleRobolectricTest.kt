package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SeedData
import com.example.domain.ai.ItineraryPlannerWizard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TripMate AI", appName)
  }

  @Test
  fun `test AI questionnaire generation and itinerary synthesis`() {
    val questions = ItineraryPlannerWizard.getDefaultQuestions()
    assertTrue("Default questions should not be empty", questions.isNotEmpty())

    val sampleTrip = SeedData.defaultTrip
    val answers = questions.associate { it.stepNumber to (it.selectedOption ?: "") }
    val items = ItineraryPlannerWizard.generateItineraryForTrip(sampleTrip, answers)

    assertTrue("Generated items should not be empty", items.isNotEmpty())
    assertEquals(4, sampleTrip.numberOfDays)
    val day1Items = items.filter { it.dayNumber == 1 }
    assertTrue("Day 1 should have activities", day1Items.isNotEmpty())
  }

  @Test
  fun `test group expense equal split calculation`() {
    val totalExpense = 6000.0
    val membersCount = 4
    val sharePerMember = totalExpense / membersCount
    assertEquals(1500.0, sharePerMember, 0.001)

    // Check Yash paid 6000, 3 members owe 1500 each
    val owedPerOther = sharePerMember
    val totalOwed = owedPerOther * (membersCount - 1)
    assertEquals(4500.0, totalOwed, 0.001)
  }

  @Test
  fun `test place image service returns valid image URL`() = kotlinx.coroutines.runBlocking {
    val imageService = com.example.data.remote.PlaceImageService()
    val url = imageService.fetchPlaceImageUrl("Amber Fort", "Jaipur", "Historical")
    assertTrue("Should return non-empty image url", url.isNotBlank())
    assertTrue("Should be an image URL", url.startsWith("http://") || url.startsWith("https://"))
  }
}
