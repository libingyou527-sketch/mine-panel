package app.minepanel.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofencePlaceTest {

    @Test
    fun serializeThenParseRoundTrips() {
        val places = listOf(
            GeofencePlace("家", 30.0, 120.0, 200),
            GeofencePlace("公司", 31.1, 121.2, 150)
        )
        val json = GeofencePlace.toJson(places)
        val parsed = GeofencePlace.parseList(json)

        assertEquals(2, parsed.size)
        assertEquals("家", parsed[0].label)
        assertEquals(30.0, parsed[0].lat, 1e-9)
        assertEquals(120.0, parsed[0].lng, 1e-9)
        assertEquals(200, parsed[0].radiusM)
        assertEquals("公司", parsed[1].label)
    }

    @Test
    fun emptyOrInvalidJsonYieldsEmptyList() {
        assertTrue(GeofencePlace.parseList("").isEmpty())
        assertTrue(GeofencePlace.parseList("not-a-json").isEmpty())
        assertTrue(GeofencePlace.parseList("[]").isEmpty())
    }

    @Test
    fun missingRadiusFallsBackToDefault() {
        val json = """[{"label":"店","lat":29.5,"lng":119.5}]"""
        val parsed = GeofencePlace.parseList(json)
        assertEquals(1, parsed.size)
        assertEquals(GeofencePlace.DEFAULT_RADIUS, parsed[0].radiusM)
    }
}
