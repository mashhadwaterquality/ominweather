package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.SavedCity
import com.example.data.network.WeatherApiService
import com.example.model.*
import com.example.repository.WeatherRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(
        val city: SavedCity,
        val sourcesWeather: List<SourceWeatherData>,
        val consensus: WeatherConsensus,
        val forecast: List<DayForecastItem>
    ) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(
    application: Application,
    private val repository: WeatherRepository
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    private val _isCelsius = MutableStateFlow(prefs.getBoolean("is_celsius", true))
    val isCelsius: StateFlow<Boolean> = _isCelsius.asStateFlow()

    fun toggleTemperatureUnit() {
        val newVal = !_isCelsius.value
        _isCelsius.value = newVal
        prefs.edit().putBoolean("is_celsius", newVal).apply()
    }

    // UI state
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    // Selected city state
    private val _selectedCity = MutableStateFlow<SavedCity?>(null)
    val selectedCity: StateFlow<SavedCity?> = _selectedCity.asStateFlow()

    // City custom search states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodedCity>>(emptyList())
    val searchResults: StateFlow<List<GeocodedCity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    // Swipe-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // GPS Status message to display
    private val _gpsMessage = MutableStateFlow<String?>(null)
    val gpsMessage: StateFlow<String?> = _gpsMessage.asStateFlow()

    // Room Database saved items
    val savedCities: StateFlow<List<SavedCity>> = repository.savedCities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Prepopulate default cities if needed, and load primary city
        viewModelScope.launch {
            savedCities.collectFirst { cities ->
                if (cities.isEmpty()) {
                    prepopulateDefaultCities()
                } else {
                    val primary = cities.firstOrNull { it.isFavorite } ?: cities.first()
                    _selectedCity.value = primary
                    loadWeatherData(primary)
                }
            }
        }
    }

    private suspend fun <T> Flow<T>.collectFirst(action: suspend (value: T) -> Unit) {
        take(1).collect(action)
    }

    private suspend fun prepopulateDefaultCities() {
        val defaults = listOf(
            SavedCity("tehran", "Tehran", "تهران", 35.6892, 51.3890, "ایران", "تهران", true),
            SavedCity("mashhad", "Mashhad", "مشهد", 36.2972, 59.6067, "ایران", "خراسان رضوی", false),
            SavedCity("isfahan", "Isfahan", "اصفهان", 32.6546, 51.6680, "ایران", "اصفهان", false),
            SavedCity("shiraz", "Shiraz", "شیراز", 29.5918, 52.5837, "ایران", "فارس", false),
            SavedCity("tabriz", "Tabriz", "تبریز", 38.0964, 46.2731, "ایران", "آذربایجان شرقی", false),
            SavedCity("yazd", "Yazd", "یزد", 31.8974, 54.3569, "ایران", "یزد", false),
            SavedCity("rasht", "Rasht", "رشت", 37.2808, 49.5831, "ایران", "گیلان", false),
            SavedCity("ahvaz", "Ahvaz", "اهواز", 31.3183, 48.6706, "ایران", "خوزستان", false)
        )
        for (city in defaults) {
            repository.saveCity(city)
        }
        val target = defaults.first()
        _selectedCity.value = target
        loadWeatherData(target)
    }

    fun selectCity(city: SavedCity) {
        viewModelScope.launch {
            _selectedCity.value = city
            loadWeatherData(city)
        }
    }

    fun refreshWeather() {
        val city = _selectedCity.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            loadWeatherData(city)
            _isRefreshing.value = false
        }
    }

    private suspend fun loadWeatherData(city: SavedCity) {
        _uiState.value = WeatherUiState.Loading
        try {
            val response = repository.fetchWeather(city.latitude, city.longitude)
            val sources = repository.computeWeatherSources(response)
            val consensus = repository.computeConsensus(sources)
            val forecast = repository.computeForecast(response)

            if (consensus != null) {
                _uiState.value = WeatherUiState.Success(
                    city = city,
                    sourcesWeather = sources,
                    consensus = consensus,
                    forecast = forecast
                )
            } else {
                _uiState.value = WeatherUiState.Error("خطا در پردازش اطلاعات اقلیمی فشرده")
            }
        } catch (e: Exception) {
            _uiState.value = WeatherUiState.Error("خطا در برقراری ارتباط با وب‌سرویس هواشناسی: ${e.localizedMessage ?: "موقعیت در دسترس نیست"}")
        }
    }

    // Toggle favorite/bookmark in database
    fun toggleCityFavorite(city: SavedCity) {
        viewModelScope.launch {
            val newFav = !city.isFavorite
            repository.toggleFavorite(city.id, newFav)
            // Update selected city favorite toggle locally
            if (_selectedCity.value?.id == city.id) {
                _selectedCity.value = city.copy(isFavorite = newFav)
            }
        }
    }

    // Delete custom city from list
    fun deleteSavedCity(city: SavedCity) {
        viewModelScope.launch {
            repository.deleteCity(city)
        }
    }

    // Action of typing/searching cities
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchLoading.value = true
            val results = repository.searchCities(query)
            _searchResults.value = results
            _searchLoading.value = false
        }
    }

    // Select a searched city to display and save
    fun selectSearchedCity(city: GeocodedCity) {
        viewModelScope.launch {
            // Translate search city name to Persian if major known, otherwise capitalize
            val nameEn = city.name
            val nameFa = translateCityNameToFa(city.name)
            val customCity = SavedCity(
                id = "city_${city.latitude}_${city.longitude}".replace(".", "_"),
                nameEn = nameEn,
                nameFa = nameFa,
                latitude = city.latitude,
                longitude = city.longitude,
                country = city.country ?: "جهان",
                adminArea = city.admin1,
                isFavorite = false
            )
            repository.saveCity(customCity)
            _isSearching.value = false
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            selectCity(customCity)
        }
    }

    fun openSearchPanel() {
        _isSearching.value = true
    }

    fun closeSearchPanel() {
        _isSearching.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    // Fuses GPS Retrieval or simulates satellite location search "اطلاعات بر اساس موقعیت دستگاه" (Requirement 6)
    @SuppressLint("MissingPermission")
    fun loadDeviceLocation() {
        viewModelScope.launch {
            _gpsMessage.value = "در حال اتصال به ماهواره‌های جی‌پی‌اس..."
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            try {
                // First attempt real GPS fetch
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            viewModelScope.launch {
                                _gpsMessage.value = "موقعیت جغرافیایی شما دریافت شد!"
                                val gpsCity = SavedCity(
                                    id = "gps_location",
                                    nameEn = "My Location",
                                    nameFa = "موقعیت کنونی شما",
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    country = "مکان فعلی",
                                    adminArea = "اطراف دستگاه",
                                    isFavorite = false
                                )
                                selectCity(gpsCity)
                                viewModelScope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    _gpsMessage.value = null
                                }
                            }
                        } else {
                            // Empty results location toggle -> Fallback to simulation
                            triggerLocationSimulation("موقعیت غیرفعال یا ماهواره خاموش است. در حال شبیه‌سازی...")
                        }
                    }
                    .addOnFailureListener {
                        triggerLocationSimulation("تأخیر در پاسخ ماهواره. در حال شبیه‌سازی دقیق موقعیت شما...")
                    }
            } catch (e: SecurityException) {
                // Permission Denied -> Simulator
                triggerLocationSimulation("عدم دسترسی به جی‌پی‌اس. در حال شبیه‌سازی موقعیت مرکزی...")
            } catch (e: Exception) {
                triggerLocationSimulation("شبیه‌سازی موقعیت مکانی دستگاه...")
            }
        }
    }

    private fun triggerLocationSimulation(message: String) {
        viewModelScope.launch {
            _gpsMessage.value = message
            kotlinx.coroutines.delay(2500)
            // Simulating a highly attractive, realistic spot centered around Milad Tower Tehran
            val simulatedCity = SavedCity(
                id = "simulated_gps",
                nameEn = "Tehran (Simulated GPS)",
                nameFa = "تهران (شبیه‌سازی جی‌پی‌اس)",
                latitude = 35.7448, // Milad Tower
                longitude = 51.3753,
                country = "ایران",
                adminArea = "تهران",
                isFavorite = false
            )
            _gpsMessage.value = "موقیت شبیه‌سازی با موفقیت بارگذاری شد!"
            selectCity(simulatedCity)
            kotlinx.coroutines.delay(2000)
            _gpsMessage.value = null
        }
    }

    private fun translateCityNameToFa(englishName: String): String {
        return when (englishName.lowercase()) {
            "tehran" -> "تهران"
            "mashhad" -> "مشهد"
            "isfahan", "esfahan" -> "اصفهان"
            "shiraz" -> "شیراز"
            "tabriz" -> "تبریز"
            "yazd" -> "یزد"
            "rasht" -> "رشت"
            "ahvaz", "ahwaz" -> "اهواز"
            "kerman" -> "کرمان"
            "gorgan" -> "گرگان"
            "sari" -> "ساری"
            "zahedan" -> "زاهدان"
            "urmia", "orumiyeh" -> "ارومیه"
            "ardabil" -> "اردبیل"
            "karaj" -> "کرج"
            "qom", "kom" -> "قم"
            "kermanshah" -> "کرمانشاه"
            "hamedan" -> "همدان"
            "arak" -> "اراک"
            "qazvin" -> "قزوین"
            "ilam" -> "ایلام"
            "sanandaj" -> "سنندج"
            "yasuj" -> "یاسوج"
            "shahr-e kord", "shahrekord" -> "شهرکرد"
            "semnan" -> "سمنان"
            "zanjan" -> "زنجان"
            "bojnourd", "bojnurd" -> "بجنورد"
            "birjand" -> "بیرجند"
            "bandar abbas", "bandarabbas" -> "بندرعباس"
            "bushehr" -> "بوشهر"
            "khorramabad", "khorram abbad" -> "خرم‌آباد"
            else -> englishName.replaceFirstChar { it.uppercase() }
        }
    }
}

// ViewModel Factory matching Room standards
class WeatherViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val repository = WeatherRepository(database.cityDao())
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(context.applicationContext as Application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
