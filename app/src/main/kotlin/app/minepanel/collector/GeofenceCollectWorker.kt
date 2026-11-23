package app.minepanel.collector

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.minepanel.core.PermissionUtils
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.PluginType
import app.minepanel.core.plugin.GeofencePlace
import app.minepanel.data.repository.PluginEventRepository
import kotlinx.coroutines.flow.first

/**
 * 地理围栏插件：基于系统最近一次定位，与手动录入的常去点做半径比对，
 * 只记录到达/离开离散事件与地点名，不采集、不上传连续轨迹。
 * 未授权、无定位缓存或未配置常去点时静默跳过。
 */
class GeofenceCollectWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val settings = ServiceLocator.settings()
            val snapshot = settings.settings.first()
            val enabled = snapshot.plugins[PluginType.GEOFENCE] ?: false
            if (!enabled) return Result.success()
            if (!PermissionUtils.hasPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) &&
                !PermissionUtils.hasPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                return Result.success()
            }

            val places = GeofencePlace.parseList(snapshot.geofencePlaces)
            if (places.isEmpty()) return Result.success()

            val location = lastKnownLocation() ?: return Result.success()
            val matched = places.minByOrNull { place -> distance(location, place) }
                ?.takeIf { distance(location, it) <= it.radiusM }

            val matchedLabel = matched?.label
            val previousLabel = snapshot.geofenceState.ifBlank { null }
            val repo = PluginEventRepository(ServiceLocator.database().pluginEventDao())

            if (matchedLabel != previousLabel) {
                if (previousLabel != null) {
                    repo.record(
                        PluginType.GEOFENCE,
                        "place_leave",
                        "{\"label\":${PluginJson.escape(previousLabel)}}"
                    )
                }
                if (matchedLabel != null) {
                    repo.record(
                        PluginType.GEOFENCE,
                        "place_enter",
                        "{\"label\":${PluginJson.escape(matchedLabel)}}"
                    )
                }
                settings.setGeofenceState(matchedLabel.orEmpty())
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun lastKnownLocation(): Location? {
        val manager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    private fun distance(location: Location, place: GeofencePlace): Float {
        val target = Location("").apply {
            latitude = place.lat
            longitude = place.lng
        }
        return location.distanceTo(target)
    }

    companion object {
        const val WORK_NAME = "plugin_geofence_collect"
    }
}
