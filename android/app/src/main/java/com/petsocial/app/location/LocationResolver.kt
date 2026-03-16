package com.petsocial.app.location

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LocationSnapshot(
    val suburb: String?,
    val latitude: Double?,
    val longitude: Double?,
)

object LocationResolver {
    fun hasLocationPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun detectLocation(context: Context): LocationSnapshot? {
        if (!hasLocationPermission(context)) return null

        val fused = LocationServices.getFusedLocationProviderClient(context)
        val lastKnown: Location? = try {
            suspendCancellableCoroutine<Location?> { cont ->
                fused.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (_: SecurityException) {
            return null
        }
        val location: Location = lastKnown ?: try {
            val request = CurrentLocationRequest.Builder()
                .setDurationMillis(3_000L)
                .build()
            val tokenSource = CancellationTokenSource()
            suspendCancellableCoroutine<Location?> { cont ->
                fused.getCurrentLocation(request, tokenSource.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { tokenSource.cancel() }
            } ?: return null
        } catch (_: SecurityException) {
            return null
        }

        val geocoder = Geocoder(context)
        val suburb = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                        cont.resume(addresses.firstOrNull()?.subLocality ?: addresses.firstOrNull()?.locality)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.subLocality
                    ?: geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()
                        ?.locality
            }
        } catch (_: Exception) {
            null
        }
        return LocationSnapshot(
            suburb = suburb,
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    suspend fun detectSuburb(context: Context): String? {
        return detectLocation(context)?.suburb
    }

    fun observeLocation(
        context: Context,
        pollIntervalMs: Long = 30_000L,
    ): Flow<LocationSnapshot> = flow {
        var lastSnapshot: LocationSnapshot? = null
        while (true) {
            val next = detectLocation(context)
            if (next != null && next != lastSnapshot) {
                emit(next)
                lastSnapshot = next
            }
            delay(pollIntervalMs.coerceAtLeast(10_000L))
        }
    }
}
