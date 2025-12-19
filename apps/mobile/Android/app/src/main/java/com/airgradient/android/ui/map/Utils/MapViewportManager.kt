package com.airgradient.android.ui.map.Utils

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import kotlin.math.abs

private const val VIEWPORT_WINDOW_MS = 60_000L
private const val TAG = "MapViewportManager"

/**
 * Map Viewport Manager
 * Handles dynamic data fetching based on map viewport changes (zoom and pan)
 * with debouncing, optimization, and intelligent caching
 */
class MapViewportManager(private val coroutineScope: CoroutineScope) {
    
    companion object {
        private const val DEBOUNCE_DELAY_MS = 800L  // Wait 800ms after user stops interacting
        private const val MIN_ZOOM_CHANGE = 0.3     // Minimum zoom change to trigger update
        private const val MIN_BOUNDS_CHANGE = 0.01  // Minimum bounds change (degrees) to trigger update
        private const val MAX_API_CALLS_PER_MINUTE = 30 // Rate limiting
    }
    
    private var lastZoomLevel: Double = -1.0
    private var lastBounds: ViewportBounds? = null
    private var lastUpdateTime: Long = 0L
    private var apiCallCount = 0
    private var apiCallWindow = System.currentTimeMillis()
    
    // Debounced viewport change events
    private val _viewportChanges = MutableSharedFlow<ViewportChange>()
    private val viewportChanges = _viewportChanges.asSharedFlow()
    
    // Listeners for data fetching
    private var dataFetchListener: ((ViewportBounds, Boolean) -> Unit)? = null
    private var throttleListener: ((ViewportThrottleEvent) -> Unit)? = null
    
    init {
        // Set up debounced processing of viewport changes
        coroutineScope.launch {
            viewportChanges
                .debounce(DEBOUNCE_DELAY_MS)
                .distinctUntilChanged()
                .collect { change ->
                    processViewportChange(change)
                }
        }
        
        // Reset API call counter every minute
        coroutineScope.launch {
            while (true) {
                delay(VIEWPORT_WINDOW_MS)
                apiCallCount = 0
                apiCallWindow = System.currentTimeMillis()
                throttleListener?.invoke(
                    ViewportThrottleEvent(
                        isThrottled = false,
                        millisUntilReset = 0,
                        callsThisMinute = apiCallCount
                    )
                )
            }
        }
    }
    
    /**
     * Called when map viewport changes (zoom or pan)
     */
    fun onViewportChange(mapView: MapView) {
        val currentZoom = mapView.zoomLevelDouble
        val currentBounds = mapView.boundingBox.toViewportBounds()
        
        debugLog { "Viewport change captured: zoom=${"%.2f".format(currentZoom)}, bounds=$currentBounds" }
        
        val change = ViewportChange(
            bounds = currentBounds,
            zoomLevel = currentZoom,
            timestamp = System.currentTimeMillis()
        )
        
        // Emit change for debounced processing
        coroutineScope.launch {
            _viewportChanges.emit(change)
        }
    }
    
    /**
     * Set listener for data fetching
     */
    fun setDataFetchListener(listener: (ViewportBounds, Boolean) -> Unit) {
        dataFetchListener = listener
    }

    fun setThrottleListener(listener: (ViewportThrottleEvent) -> Unit) {
        throttleListener = listener
    }
    
    /**
     * Process debounced viewport change
     */
    private suspend fun processViewportChange(change: ViewportChange) {
        debugLog { "Processing debounced viewport change" }
        
        val shouldFetch = shouldFetchData(change)
        
        if (shouldFetch.first) {
            if (isWithinRateLimit()) {
                debugLog { "Rate limit ok. Fetching data for bounds=${change.bounds}" }
                
                dataFetchListener?.invoke(change.bounds, shouldFetch.second.contains("zoom"))
                
                // Update tracking variables
                lastZoomLevel = change.zoomLevel
                lastBounds = change.bounds
                lastUpdateTime = System.currentTimeMillis()
                apiCallCount++
                val remaining = (VIEWPORT_WINDOW_MS - (System.currentTimeMillis() - apiCallWindow)).coerceAtLeast(0L)
                throttleListener?.invoke(
                    ViewportThrottleEvent(
                        isThrottled = false,
                        millisUntilReset = remaining,
                        callsThisMinute = apiCallCount
                    )
                )
                
                debugLog { "Fetch dispatched" }
            } else {
                debugLog { "Rate limit reached. Skipping fetch" }
            }
        } else {
            debugLog { "Viewport change did not meet fetch criteria" }
        }

        debugLog { "Viewport change processed" }
    }
    
    /**
     * Determine if we should fetch new data based on viewport change
     */
    private fun shouldFetchData(change: ViewportChange): Pair<Boolean, String> {
        val reasons = mutableListOf<String>()
        
        // Check if this is the first load
        if (lastBounds == null) {
            reasons.add("initial_load")
            return true to reasons.joinToString(", ")
        }
        
        // Check zoom level change
        val zoomChange = abs(change.zoomLevel - lastZoomLevel)
        if (zoomChange >= MIN_ZOOM_CHANGE) {
            reasons.add("zoom_change(${String.format("%.1f", zoomChange)})")
        }
        
        // Check bounds change  
        val boundsChange = calculateBoundsChange(lastBounds!!, change.bounds)
        if (boundsChange >= MIN_BOUNDS_CHANGE) {
            reasons.add("bounds_change(${String.format("%.3f", boundsChange)})")
        }
        
        // Check time since last update (force refresh after 5 minutes)
        val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
        if (timeSinceLastUpdate > 300_000) { // 5 minutes
            reasons.add("time_expired")
        }
        
        // Check if bounds are completely different (user panned far away)
        if (!lastBounds!!.overlaps(change.bounds)) {
            reasons.add("no_bounds_overlap")
        }
        
        val shouldFetch = reasons.isNotEmpty()
        return shouldFetch to reasons.joinToString(", ")
    }
    
    /**
     * Calculate bounds change percentage
     */
    private fun calculateBoundsChange(oldBounds: ViewportBounds, newBounds: ViewportBounds): Double {
        val latChange = maxOf(
            abs(oldBounds.north - newBounds.north),
            abs(oldBounds.south - newBounds.south)
        )
        val lonChange = maxOf(
            abs(oldBounds.east - newBounds.east),
            abs(oldBounds.west - newBounds.west)
        )
        
        return maxOf(latChange, lonChange)
    }
    
    /**
     * Check if we're within API rate limits
     */
    private fun isWithinRateLimit(): Boolean {
        val within = apiCallCount < MAX_API_CALLS_PER_MINUTE
        if (!within) {
            val remaining = (VIEWPORT_WINDOW_MS - (System.currentTimeMillis() - apiCallWindow)).coerceAtLeast(0L)
            throttleListener?.invoke(
                ViewportThrottleEvent(
                    isThrottled = true,
                    millisUntilReset = remaining,
                    callsThisMinute = apiCallCount
                )
            )
        }
        return within
    }
    
    
    /**
     * Force immediate data fetch (bypass debouncing)
     */
    fun forceFetch(bounds: ViewportBounds, zoomLevel: Double) {
        debugLog { "Force fetch requested for bounds=$bounds" }

        coroutineScope.launch {
            if (!isWithinRateLimit()) {
                return@launch
            }

            dataFetchListener?.invoke(bounds, true)
            lastBounds = bounds
            lastZoomLevel = zoomLevel
            lastUpdateTime = System.currentTimeMillis()
            apiCallCount++
            val remaining = (VIEWPORT_WINDOW_MS - (System.currentTimeMillis() - apiCallWindow)).coerceAtLeast(0L)
            throttleListener?.invoke(
                ViewportThrottleEvent(
                    isThrottled = false,
                    millisUntilReset = remaining,
                    callsThisMinute = apiCallCount
                )
            )
        }
    }
    
    /**
     * Reset state (useful for testing or configuration changes)
     */
    fun reset() {
        lastBounds = null
        lastZoomLevel = -1.0
        lastUpdateTime = 0L
        apiCallCount = 0
        debugLog { "ViewportManager reset" }
    }
    
    /**
     * Get current viewport statistics
     */
    fun getStats(): ViewportStats {
        return ViewportStats(
            lastZoomLevel = lastZoomLevel,
            lastBounds = lastBounds,
            lastUpdateTime = lastUpdateTime,
            apiCallsThisMinute = apiCallCount,
            timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
        )
    }

    /**
     * Clean up resources (called when ViewModel is cleared)
     */
    fun dispose() {
        dataFetchListener = null
        reset()
        debugLog { "ViewportManager disposed and resources cleaned up" }
    }

    private inline fun debugLog(message: () -> String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message())
        }
    }
}

/**
 * Viewport bounds data class
 */
data class ViewportBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    /**
     * Check if this bounds overlaps with another
     */
    fun overlaps(other: ViewportBounds): Boolean {
        return !(north < other.south || 
                south > other.north || 
                east < other.west || 
                west > other.east)
    }
    
    /**
     * Get area of bounds (for comparison)
     */
    fun getArea(): Double {
        return abs(north - south) * abs(east - west)
    }
    
    /**
     * Expand bounds by a padding amount
     */
    fun expand(padding: Double): ViewportBounds {
        return ViewportBounds(
            north = north + padding,
            south = south - padding,
            east = east + padding,
            west = west - padding
        )
    }
    
    override fun toString(): String {
        return "Bounds(N=${String.format("%.3f", north)}, S=${String.format("%.3f", south)}, " +
               "E=${String.format("%.3f", east)}, W=${String.format("%.3f", west)})"
    }
}

/**
 * Viewport change event
 */
data class ViewportChange(
    val bounds: ViewportBounds,
    val zoomLevel: Double,
    val timestamp: Long
)

data class ViewportThrottleEvent(
    val isThrottled: Boolean,
    val millisUntilReset: Long,
    val callsThisMinute: Int
)

/**
 * Viewport statistics
 */
data class ViewportStats(
    val lastZoomLevel: Double,
    val lastBounds: ViewportBounds?,
    val lastUpdateTime: Long,
    val apiCallsThisMinute: Int,
    val timeSinceLastUpdate: Long
)

/**
 * Extension function to convert OSMDroid BoundingBox to ViewportBounds
 */
fun BoundingBox.toViewportBounds(): ViewportBounds {
    return ViewportBounds(
        north = latNorth,
        south = latSouth,
        east = lonEast,
        west = lonWest
    )
}

/**
 * Map bounds data class for API validation
 */
data class MapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

/**
 * Extension function to convert ViewportBounds to MapBounds (for API validator)
 */
fun ViewportBounds.toMapBounds(): MapBounds {
    return MapBounds(north, south, east, west)
}
