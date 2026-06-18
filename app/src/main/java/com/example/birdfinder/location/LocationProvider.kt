package com.example.birdfinder.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** A resolved location fix with optional horizontal accuracy in metres. */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

/**
 * Coroutine wrapper around FusedLocationProviderClient. Prefers a fresh high-accuracy
 * fix (waiting up to [DEFAULT_TIMEOUT_MS]); only if that yields nothing does it fall back
 * to the last cached fix. Returns null when permission is missing or no fix is available,
 * so callers can fall back to user-configured coordinates.
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun freshFix(timeoutMs: Long = DEFAULT_TIMEOUT_MS): GeoFix? {
        if (!hasFineLocationPermission()) return null
        val fresh = runCatching {
            withTimeoutOrNull(timeoutMs) { requestCurrent() }
        }.getOrNull()
        if (fresh != null) return fresh
        return runCatching { lastKnown() }.getOrNull()
    }

    /** Back-compat convenience returning just lat/lon. */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun currentOrLastKnown(): Pair<Double, Double>? =
        freshFix()?.let { it.latitude to it.longitude }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(): GeoFix? = suspendCancellableCoroutine { cont ->
        val token = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { loc -> cont.resume(loc?.toFix()) }
            .addOnFailureListener { cont.resume(null) }
        // withTimeoutOrNull cancels us on timeout; propagate that to the location request.
        cont.invokeOnCancellation { token.cancel() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(): GeoFix? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { loc -> cont.resume(loc?.toFix()) }
            .addOnFailureListener { cont.resume(null) }
    }

    private fun Location.toFix() = GeoFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}
