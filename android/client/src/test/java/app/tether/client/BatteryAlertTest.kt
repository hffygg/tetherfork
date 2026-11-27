package app.tether.client

import app.tether.client.model.BatteryAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryAlertTest {
    @Test fun stepsOfFiveBelowTheThreshold() {
        assertEquals(listOf(null, 20, 20, 20, 15, 15, 10, 5, 0),
            listOf(21, 20, 17, 16, 15, 11, 10, 5, 0).map { BatteryAlert.step(it, charging = false, threshold = 20) })
    }

    @Test fun noAlertWhenOffChargingOrUnknown() {
        assertNull(BatteryAlert.step(10, charging = false, threshold = 0))
        assertNull(BatteryAlert.step(10, charging = true, threshold = 20))
        assertNull(BatteryAlert.step(-1, charging = false, threshold = 20))
    }

    @Test fun alertsOnlyWhenTheStepGoesDown() {
        assertTrue(BatteryAlert.shouldAlert(20, null))
        assertFalse(BatteryAlert.shouldAlert(20, 20))
        assertTrue(BatteryAlert.shouldAlert(15, 20))
        assertFalse(BatteryAlert.shouldAlert(20, 15))   // a little charge back up: no new alert
        assertFalse(BatteryAlert.shouldAlert(null, 15))
    }
}
