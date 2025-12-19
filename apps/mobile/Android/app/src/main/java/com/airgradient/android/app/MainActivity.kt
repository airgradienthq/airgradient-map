package com.airgradient.android.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.airgradient.android.R
import com.airgradient.android.domain.repositories.BookmarkRepository
import com.airgradient.android.navigation.AGMapApp
import com.airgradient.android.ui.theme.AGMapTheme
import com.airgradient.android.widget.AirQualityWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val selectedLocationIdState = mutableStateOf<Int?>(null)
    @Inject lateinit var bookmarkRepository: BookmarkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val hasBookmarks = runBlocking {
            bookmarkRepository.getAllBookmarks().first().isNotEmpty()
        }

        intent?.let { incoming ->
            updateSelectedLocationFromIntent(incoming)
        }

        setContent {
            AGMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showFullImageSplash by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        // Give the activity a brief moment to draw the branded splash artwork
                        delay(650)
                        showFullImageSplash = false
                    }

                    val selectedLocationId = selectedLocationIdState.value

                    Box(modifier = Modifier.fillMaxSize()) {
                        AGMapApp(
                            selectedLocationId = selectedLocationId,
                            onSelectedLocationConsumed = {
                                selectedLocationIdState.value = null
                            },
                            hasBookmarks = hasBookmarks
                        )

                        if (showFullImageSplash) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colorResource(id = R.color.splash_background)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.androidnsplash2),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { incoming ->
            updateSelectedLocationFromIntent(incoming)
        }
    }

    private fun updateSelectedLocationFromIntent(intent: Intent) {
        val locationId = intent.getIntExtra(
            AirQualityWidgetProvider.EXTRA_SELECTED_LOCATION_ID,
            -1
        )
        if (locationId > 0) {
            selectedLocationIdState.value = locationId
        }
    }
}
