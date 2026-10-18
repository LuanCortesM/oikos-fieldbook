package com.oikos.fieldbook.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFileParserTest {

    @Test
    fun gpxComAtributosEmQualquerOrdemEPontoSemFilhos() {
        val gpx = """
            <gpx><trk><trkseg>
              <trkpt lon="-43.2" lat="-22.9"><ele>40</ele><time>2026-03-14T11:00:00Z</time></trkpt>
              <trkpt lat="-22.91" lon="-43.21"/>
            </trkseg></trk>
            <wpt lat="-10" lon="-50"><name>acampamento</name></wpt></gpx>
        """.trimIndent()
        val points = TrackFileParser.parse(gpx)
        assertEquals("waypoint nao entra na linha da trilha", 2, points.size)
        assertEquals(-22.9, points[0].latitude, 0.0)
        assertEquals(-43.2, points[0].longitude, 0.0)
        assertEquals(40.0, points[0].altitude!!, 0.0)
        assertEquals(1773486000000L, points[0].timestamp)
        assertNull("sem horario no arquivo, sem horario inventado", points[1].timestamp)
    }

    @Test
    fun gpxSoComWaypointsUsaOsWaypoints() {
        val points = TrackFileParser.parse("""<gpx><wpt lat="-10" lon="-50"/><wpt lat="-10.1" lon="-50.1"/></gpx>""")
        assertEquals(2, points.size)
    }

    @Test
    fun kmlLineStringNaoTemHorario() {
        val kml = "<kml><LineString><coordinates>-43.2,-22.9,40 -43.21,-22.91,45</coordinates></LineString></kml>"
        val points = TrackFileParser.parse(kml)
        assertEquals(2, points.size)
        assertEquals(-22.91, points[1].latitude, 0.0)
        assertTrue(points.all { it.timestamp == null })
    }

    @Test
    fun kmlGxTrackTemHorario() {
        val kml = """
            <kml><gx:Track>
              <when>2026-03-14T08:00:00-03:00</when><when>2026-03-14T08:01:00-03:00</when>
              <gx:coord>-43.2 -22.9 40</gx:coord><gx:coord>-43.21 -22.91 45</gx:coord>
            </gx:Track></kml>
        """.trimIndent()
        val points = TrackFileParser.parse(kml)
        assertEquals(2, points.size)
        assertEquals(1773486000000L, points[0].timestamp)
        assertEquals(60_000L, points[1].timestamp!! - points[0].timestamp!!)
    }

    @Test
    fun geoJsonComCoordTimes() {
        val json = """{"type":"Feature","properties":{"coordTimes":["2026-03-14T11:00:00Z","2026-03-14T11:00:30Z"]},
            "geometry":{"type":"LineString","coordinates":[[-43.2,-22.9],[-43.21,-22.91]]}}"""
        val points = TrackFileParser.parse(json)
        assertEquals(2, points.size)
        assertEquals(-22.9, points[0].latitude, 0.0)
        assertEquals(30_000L, points[1].timestamp!! - points[0].timestamp!!)
    }

    @Test
    fun csvComColunaDeHorarioEVirgulaDecimal() {
        val csv = "latitude;longitude;altitude;time\n-22,9;-43,2;40;2026-03-14T11:00:00Z\n-22,91;-43,21;;2026-03-14T11:00:10Z"
        val points = TrackFileParser.parse(csv)
        assertEquals(2, points.size)
        assertEquals(-22.91, points[1].latitude, 0.0)
        assertEquals(40.0, points[0].altitude!!, 0.0)
        assertEquals(10_000L, points[1].timestamp!! - points[0].timestamp!!)
    }

    @Test
    fun horarioSemFusoNaoEAceito() {
        assertNull(TrackFileParser.parseInstant("2026-03-14 08:00:00"))
        assertEquals(1773486000000L, TrackFileParser.parseInstant("2026-03-14T11:00:00Z"))
    }
}
