package com.example

import com.example.ui.formatTempOnly
import com.example.ui.formatTempWithUnit
import com.example.ui.formatTempDiff
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCelsiusToFahrenheitConversion() {
    // 0°C is 32°F
    assertEquals("32.0°F", formatTempWithUnit(0.0, false))
    // 100°C is 212°F
    assertEquals("212.0°F", formatTempWithUnit(100.0, false))
    // 25.5°C is 77.9°F (25.5 * 1.8 + 32 = 45.9 + 32 = 77.9)
    assertEquals("77.9°F", formatTempWithUnit(25.5, false))
  }

  @Test
  fun testCelsiusKeepCorrect() {
    // 25.5°C stays 25.5°C
    assertEquals("25.5°C", formatTempWithUnit(25.5, true))
  }

  @Test
  fun testStandardDeviationDeltaConversion() {
    // Temperature delta conversion (no +32 offset, only multiplied by 1.8)
    // 1.5°C delta is 2.7°F delta
    assertEquals("2.7°F", formatTempDiff(1.5, false))
    // 1.5°C delta in Celsius stays 1.5°C
    assertEquals("1.5°C", formatTempDiff(1.5, true))
  }
}
