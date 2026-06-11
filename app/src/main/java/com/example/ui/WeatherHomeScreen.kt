package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.SavedCity
import com.example.model.DayForecastItem
import com.example.model.SourceWeatherData
import com.example.model.WeatherConsensus
import com.example.model.WeatherSource

fun formatTempOnly(celsius: Double, isCelsius: Boolean): String {
    val valCon = if (isCelsius) celsius else (celsius * 1.8 + 32.0)
    val rounded = Math.round(valCon * 10.0) / 10.0
    return "$rounded"
}

fun formatTempWithUnit(celsius: Double, isCelsius: Boolean): String {
    val valCon = if (isCelsius) celsius else (celsius * 1.8 + 32.0)
    val rounded = Math.round(valCon * 10.0) / 10.0
    val unit = if (isCelsius) "°C" else "°F"
    return "$rounded$unit"
}

fun formatTempDiff(celsiusDiff: Double, isCelsius: Boolean): String {
    val valCon = if (isCelsius) celsiusDiff else (celsiusDiff * 1.8)
    val rounded = Math.round(valCon * 10.0) / 10.0
    val unit = if (isCelsius) "°C" else "°F"
    return "$rounded$unit"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherHomeScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isCelsius by viewModel.isCelsius.collectAsState()
    val savedCities by viewModel.savedCities.collectAsState()
    val selectedCity by viewModel.selectedCity.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val gpsMessage by viewModel.gpsMessage.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.loadDeviceLocation()
        }
    }

    LaunchedEffect(Unit) {
        val fineGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            viewModel.loadDeviceLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Determine background color based on current weather types
    val isDay = when (val state = uiState) {
        is WeatherUiState.Success -> {
            val code = state.consensus.consensusWeatherCode
            code < 4 // Standard daytime code indicator
        }
        else -> true
    }

    val gradientTheme = if (isDay) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F2027),
                Color(0xFF203A43),
                Color(0xFF2C5364)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0D1B2A),
                Color(0xFF1B263B),
                Color(0xFF415A77)
            )
        )
    }

    // Force RTL local direction for standard Persian reading flow
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientTheme)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Header Bar: Title, Search, and Location-GPS
                HeaderBar(
                    isCelsius = isCelsius,
                    onUnitToggle = { viewModel.toggleTemperatureUnit() },
                    onSearchClicked = { viewModel.openSearchPanel() },
                    onGpsClicked = { viewModel.loadDeviceLocation() }
                )

                // 2. City Filter Selector Chips (Horizontal row)
                CityChipsRow(
                    savedCities = savedCities,
                    selectedCity = selectedCity,
                    onCitySelected = { viewModel.selectCity(it) }
                )

                // 3. Current Weather UI State switchboard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (val state = uiState) {
                        is WeatherUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        is WeatherUiState.Error -> {
                            ErrorDisplay(
                                message = state.message,
                                onRetry = { viewModel.refreshWeather() }
                            )
                        }
                        is WeatherUiState.Success -> {
                            WeatherDetailsContent(
                                state = state,
                                isCelsius = isCelsius,
                                onToggleFavorite = { viewModel.toggleCityFavorite(it) },
                                onDeleteCity = { viewModel.deleteSavedCity(it) }
                            )
                        }
                    }
                }
            }

            // GPS satellite connection dialog overlay (Requirement 6)
            if (gpsMessage != null) {
                GpsOrbitalHUD(message = gpsMessage!!)
            }

            // Global search system sheet overlay (Requirement 3)
            if (isSearching) {
                SearchOverlayDialog(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    searchLoading = searchLoading,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onCitySelected = { viewModel.selectSearchedCity(it) },
                    onDismiss = { viewModel.closeSearchPanel() }
                )
            }
        }
    }
}

@Composable
fun HeaderBar(
    isCelsius: Boolean,
    onUnitToggle: () -> Unit,
    onSearchClicked: () -> Unit,
    onGpsClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "هواشناسی همگرا",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "جمع‌بندی هوشمند آمار جوی در کشور",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capsule Segmented unit selector (Celsius vs Fahrenheit toggle)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(2.dp)
                    .testTag("unit_toggle_capsule"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (isCelsius) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                        .clickable { if (!isCelsius) onUnitToggle() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "°C",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCelsius) Color(0xFF0F2027) else Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (!isCelsius) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                        .clickable { if (isCelsius) onUnitToggle() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "°F",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isCelsius) Color(0xFF0F2027) else Color.White
                    )
                }
            }
            // Find Satellite / Device Location
            IconButton(
                onClick = onGpsClicked,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .clip(CircleShape)
                    .testTag("gps_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "یافتن با جی‌پی‌اس",
                    tint = Color.White
                )
            }

            // Search city button
            IconButton(
                onClick = onSearchClicked,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .clip(CircleShape)
                    .testTag("search_city_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "جستجوی شهر جدید",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun CityChipsRow(
    savedCities: List<SavedCity>,
    selectedCity: SavedCity?,
    onCitySelected: (SavedCity) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        savedCities.forEach { city ->
            val isSelected = selectedCity?.id == city.id
            InputChip(
                selected = isSelected,
                onClick = { onCitySelected(city) },
                label = {
                    Text(
                        text = city.nameFa,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF0F2027) else Color.White
                    )
                },
                leadingIcon = {
                    if (city.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "مورد علاقه",
                            tint = if (isSelected) Color(0xFF0F2027) else Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocationCity,
                            contentDescription = "شهر",
                            tint = if (isSelected) Color(0xFF0F2027) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = InputChipDefaults.inputChipColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    selectedContainerColor = Color.White.copy(alpha = 0.9f)
                ),
                border = InputChipDefaults.inputChipBorder(
                    enabled = true,
                    selected = isSelected,
                    selectedBorderColor = Color.White,
                    borderColor = Color.White.copy(alpha = 0.2f),
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.2.dp
                ),
                modifier = Modifier
                    .testTag("city_chip_${city.id}")
                    .height(38.dp)
            )
        }
    }
}

@Composable
fun WeatherDetailsContent(
    state: WeatherUiState.Success,
    isCelsius: Boolean,
    onToggleFavorite: (SavedCity) -> Unit,
    onDeleteCity: (SavedCity) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Location card showing main consolidated info
        LocationCard(
            city = state.city,
            consensus = state.consensus,
            isCelsius = isCelsius,
            onToggleFavorite = { onToggleFavorite(state.city) },
            onDeleteCity = { onDeleteCity(state.city) }
        )

        // Consensus & Agreement statistics dashboard (Requirement 4)
        AgreementConsensusDashboard(consensus = state.consensus, isCelsius = isCelsius)

        // Multiple Sources Side-by-Side meteorology grid (Requirement 1, 2)
        SourcesComparisonSection(sources = state.sourcesWeather, isCelsius = isCelsius)

        // Multi-Days Forecast prediction list (Requirement 5)
        MultiDayForecastSection(forecast = state.forecast, isCelsius = isCelsius)

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun LocationCard(
    city: SavedCity,
    consensus: WeatherConsensus,
    isCelsius: Boolean,
    onToggleFavorite: () -> Unit,
    onDeleteCity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button for custom searched locations (do not delete default core cities)
                val isCoreCity = city.id in listOf("tehran", "mashhad", "isfahan", "shiraz", "tabriz", "yazd", "rasht", "ahvaz")
                if (!isCoreCity) {
                    IconButton(
                        onClick = onDeleteCity,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Red.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف شهر از لیست",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }

                // Title displaying city and province
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = city.nameFa,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (city.adminArea != null) {
                        Text(
                            text = "${city.adminArea}، ${city.country}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Bookmark/Favorite button
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (city.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "نشان کردن شهر",
                        tint = if (city.isFavorite) Color.Yellow else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Massive Visual Weather Consensus Icon and Temperature
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = consensus.consensusIcon,
                    fontSize = 72.sp,
                    modifier = Modifier.padding(end = 16.dp),
                    textAlign = TextAlign.Center
                )
                Column {
                    Text(
                        text = formatTempWithUnit(consensus.avgTemperature, isCelsius),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                    Text(
                        text = consensus.consensusDesc,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Metrics split bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricColumn(
                    icon = Icons.Default.WaterDrop,
                    label = "میانگین رطوبت",
                    value = "${consensus.avgHumidity}%"
                )
                VerticalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.height(30.dp))
                MetricColumn(
                    icon = Icons.Default.Air,
                    label = "سرعت باد",
                    value = "${consensus.avgWindSpeed} km/h"
                )
                VerticalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.height(30.dp))
                MetricColumn(
                    icon = Icons.Default.CompareArrows,
                    label = "اختلاف مدل‌ها",
                    value = formatTempDiff(consensus.tempStdDev, isCelsius)
                )
            }
        }
    }
}

@Composable
fun MetricColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF64B5F6)
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun AgreementConsensusDashboard(
    consensus: WeatherConsensus,
    isCelsius: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "جمع‌بندی آمار و هماهنگی مدل‌ها",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // High-fidelity badge to express consensus strength (Requirement 4)
                Box(
                    modifier = Modifier
                        .background(
                            color = when {
                                consensus.tempStdDev < 0.4 -> Color(0xFF2E7D32).copy(alpha = 0.8f) // Green
                                consensus.tempStdDev < 1.0 -> Color(0xFF1565C0).copy(alpha = 0.8f) // Blue
                                consensus.tempStdDev < 1.6 -> Color(0xFFEF6C00).copy(alpha = 0.8f) // Orange
                                else -> Color(0xFFC62828).copy(alpha = 0.8f) // Red
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = consensus.degreeOfAgreement,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Temperature ranges comparison info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "کمترین فرضیه مدل‌ها: ${formatTempWithUnit(consensus.minTemperature, isCelsius)}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "بیشترین فرضیه مدل‌ها: ${formatTempWithUnit(consensus.maxTemperature, isCelsius)}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Agreement slider representation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            ) {
                // Determine the agreement slider gauge length
                val fractionalAgreement = (2.2 - consensus.tempStdDev).coerceIn(0.1, 2.0) / 2.0
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fractionalAgreement.toFloat())
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFFB74D), Color(0xFF64B5F6))
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }

            Text(
                text = "پایدارساز آماری انحراف معیار دما برابر با ${formatTempDiff(consensus.tempStdDev, isCelsius)} است. همگرایی کمتر نشانگر ثبات شدید هوای منطقه است.",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun SourcesComparisonSection(
    sources: List<SourceWeatherData>,
    isCelsius: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "مقایسه جزئیات دریافتی از منابع مختلف",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Show the 4 sources in a custom grid cards side-by-side (Requirement 1, 2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            sources.forEach { sourceData ->
                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .shadow(2.dp, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Badge / source name Fa
                        Column {
                            Text(
                                text = sourceData.source.nameFa,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4FC3F7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sourceData.source.nameEn,
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }

                        // Temp & Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTempWithUnit(sourceData.temperature, isCelsius),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = sourceData.icon,
                                fontSize = 24.sp
                            )
                        }

                        Text(
                            text = sourceData.weatherDesc,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Divider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)

                        // Sub metrics for details (Humidity and Wind speed)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "رطوبت",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "${sourceData.humidity}%",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Air,
                                    contentDescription = "باد",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "${sourceData.windSpeed} km/h",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MultiDayForecastSection(
    forecast: List<DayForecastItem>,
    isCelsius: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "پیش‌بینی چند روزه آب و هوایی (برآیند محاسباتی)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                forecast.take(7).forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Day of the week
                        Column(modifier = Modifier.width(90.dp)) {
                            Text(
                                text = day.dayOfWeek,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = day.date,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }

                        // 2. Weather Icon and Description
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = day.icon,
                                fontSize = 22.sp
                            )
                            Text(
                                text = day.weatherDesc,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // 3. Minimum / Maximum Temperatures representation bar
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (day.precipitationSum > 0.1) {
                                Text(
                                    text = "${day.precipitationSum} mm🌧️",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64B5F6),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = formatTempWithUnit(day.tempMax, isCelsius),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = formatTempWithUnit(day.tempMin, isCelsius),
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    if (day != forecast.lastOrNull()) {
                        Divider(color = Color.White.copy(alpha = 0.05f), thickness = 0.8.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDisplay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SignalWifiOff,
            contentDescription = "قطع شبکه",
            tint = Color(0xFFE57373),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
        ) {
            Text(text = "تلاش مجدد ارتباط", color = Color.White)
        }
    }
}

// Custom orbital high-fidelity GPS Connection Anim HUD (Requirement 6)
@Composable
fun GpsOrbitalHUD(
    message: String,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = {}) {
        Box(
            modifier = modifier
                .size(240.dp)
                .background(Color(0xFF0F2027).copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            // Infinite satellite orbital rotate indicator
            val infiniteTransition = rememberInfiniteTransition(label = "orbital")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "angle"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { 0.7f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF64B5F6),
                        strokeWidth = 3.dp
                    )
                    Icon(
                        imageVector = Icons.Default.Satellite,
                        contentDescription = "اتصال ماهواره",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp
                )
                Text(
                    text = "در حال تجمیع اطلاعات اقلیم...",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// Complete Overlay Search panel with input field and dynamic results list
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlayDialog(
    searchQuery: String,
    searchResults: List<com.example.model.GeocodedCity>,
    searchLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onCitySelected: (com.example.model.GeocodedCity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .shadow(12.dp, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header of Search Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "افزودن موقعیت (شهر جدید)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن صفحه جستجو",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                // Search Input Field
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_text_input"),
                    placeholder = {
                        Text(
                            text = "نام شهر را به انگلیسی بنویسید (مثال: Tehran)",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF64B5F6)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (searchLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    },
                    singleLine = true
                )

                Text(
                    text = "جستجو هوشمند به صورت زنده از بانک مرکزی جغرافیایی انجام می‌گردد.",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Divider(color = Color.White.copy(alpha = 0.1f))

                // Searched Cities list
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.length < 2) "منتظر تایپ عبارت..." else "شهری یافت نشد. انگلیسی جستجو نمایید.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(searchResults) { city ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onCitySelected(city) }
                                    .padding(12.dp)
                                    .testTag("searched_city_item_${city.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = city.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    if (city.admin1 != null) {
                                        Text(
                                            text = "${city.admin1}، ${city.country ?: ""}",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    } else {
                                        Text(
                                            text = city.country ?: "",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "انتخاب",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64B5F6),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = null,
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
