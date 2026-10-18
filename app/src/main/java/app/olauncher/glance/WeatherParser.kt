package app.olauncher.glance

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class Place(
    val name: String,
    val admin1: String? = null,
    val country: String? = null,
    val lat: Double,
    val lon: Double,
) {
    fun displayName(): String =
        listOfNotNull(
            name.takeIf { it.isNotBlank() },
            admin1?.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() },
        ).joinToString(", ")
}

data class Forecast(
    val nowTemp: Double,
    val nowCode: Int,
    val isDay: Boolean,
    val todayMax: Double,
    val todayMin: Double,
    val hours: List<Hour>,
    /** City's UTC offset from Open-Meteo; null for caches written before it was parsed. */
    val utcOffsetSeconds: Int? = null,
) {
    /**
     * Converts the phone's local [phoneNow] to the city's local time, since [hours] are in the
     * city's zone. Falls back to [phoneNow] unchanged when the offset is unknown.
     */
    fun cityNow(phoneNow: LocalDateTime, phoneZone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        val offsetSeconds = utcOffsetSeconds ?: return phoneNow
        val offset = try {
            ZoneOffset.ofTotalSeconds(offsetSeconds)
        } catch (e: Exception) {
            return phoneNow
        }
        return phoneNow.atZone(phoneZone).withZoneSameInstant(offset).toLocalDateTime()
    }
}

data class Hour(
    val time: LocalDateTime,
    val temp: Double,
    val precipProb: Int,
    val code: Int,
)

object WeatherParser {

    fun parsePlaces(json: String?): List<Place> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            val results = root.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<Place>()
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val name = obj.getString("name")
                val admin1 = obj.optString("admin1").takeIf { it.isNotBlank() }
                val country = obj.optString("country").takeIf { it.isNotBlank() }
                val lat = obj.getDouble("latitude")
                val lon = obj.getDouble("longitude")
                list.add(Place(name, admin1, country, lat, lon))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseForecast(json: String?): Forecast? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            val current = root.getJSONObject("current")
            val nowTemp = current.getDouble("temperature_2m")
            val nowCode = current.getInt("weather_code")
            val isDay = current.optInt("is_day", if (current.optBoolean("is_day")) 1 else 0) == 1

            val daily = root.getJSONObject("daily")
            val maxArray = daily.getJSONArray("temperature_2m_max")
            val minArray = daily.getJSONArray("temperature_2m_min")
            val todayMax = maxArray.getDouble(0)
            val todayMin = minArray.getDouble(0)

            val hourly = root.getJSONObject("hourly")
            val timeArray = hourly.getJSONArray("time")
            val tempArray = hourly.getJSONArray("temperature_2m")
            val precipArray = hourly.getJSONArray("precipitation_probability")
            val codeArray = hourly.getJSONArray("weather_code")

            val count = minOf(
                timeArray.length(),
                tempArray.length(),
                precipArray.length(),
                codeArray.length(),
            )
            val hours = mutableListOf<Hour>()
            for (i in 0 until count) {
                val timeStr = timeArray.getString(i)
                val time = LocalDateTime.parse(timeStr)
                val temp = tempArray.getDouble(i)
                val precipProb = precipArray.getInt(i)
                val code = codeArray.getInt(i)
                hours.add(Hour(time, temp, precipProb, code))
            }

            Forecast(
                nowTemp = nowTemp,
                nowCode = nowCode,
                isDay = isDay,
                todayMax = todayMax,
                todayMin = todayMin,
                hours = hours,
                utcOffsetSeconds = if (root.has("utc_offset_seconds")) root.optInt("utc_offset_seconds") else null,
            )
        } catch (e: Exception) {
            null
        }
    }
}
