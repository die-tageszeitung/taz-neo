package de.taz.app.android

import java.text.SimpleDateFormat
import java.util.Locale

const val TAZ_AUTH_HEADER = "X-tazAppAuthKey"

const val DEBUG_VERSION_DOWNLOAD_ENDPOINT = "https://dl.taz.de/down/taz.neo.debug.apk"

// UI
const val DEFAULT_MOMENT_FILE = "noInternetMoment.png"
const val SUBSCRIPTION_EMAIL_ADDRESS = "app@taz.de"
const val TAZ_ACCOUNT_SUFFIX = "@taz.de"
const val WEBVIEW_DRAG_SENSITIVITY_FACTOR = 2
const val WEBVIEW_HTML_FILE_TERMS = "welcomeTerms.html"
const val WEBVIEW_HTML_FILE_REVOCATION = "welcomeRevocation.html"
const val WEBVIEW_HTML_FILE_DATA_POLICY = "welcomeSlidesDataPolicy.html"
const val WEBVIEW_HTML_FILE_SEARCH_HELP = "searchHelp.html"
const val COVERFLOW_MAX_SMOOTH_SCROLL_DISTANCE = 15
const val HIDE_LOGO_DELAY_MS = 200L
const val LOGO_ANIMATION_DURATION_MS = 300L


const val MIN_TEXT_SIZE = 30
const val MAX_TEXT_SIZE = 200

// Settings
const val ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE = "ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE"

// General Preferences
const val MAX_SIMULTANEOUS_QUERIES = 8
const val MAX_SIMULTANEOUS_DOWNLOADS = 8
const val TAP_ICON_FADE_OUT_TIME = 100L
const val LOADING_SCREEN_FADE_OUT_TIME = 500L
const val CONNECTION_FAILURE_BACKOFF_TIME_MS = 100L
const val MAX_CONNECTION_FAILURE_BACKOFF_TIME_MS = 4000L
const val MAX_BYTES = 4294967296 // = 2^32

// onSaveInstanceState
const val DISPLAYABLE_NAME = "displayableName"

// SimpleDateFormat is not thread safe!!! We need to create to create a instance for every thread context
val simpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

const val COPY_BUFFER_SIZE = 100 * 1024 // 100kiB

const val METADATA_DOWNLOAD_RETRY_INDEFINITELY = -1
const val METADATA_DOWNLOAD_DEFAULT_RETRIES = 7
const val FILE_DOWNLOAD_RETRY_INDEFINITELY = -1

// The app is not translated to multiple languages, thus we should use GERMAN as the default locale
// when doing string transforms like lowercase()
val appLocale: Locale = Locale.GERMAN


// The time in milliseconds after which a session is deemed to be idle if there was no user action (4h)
const val APP_SESSION_TIMEOUT_MS = 4L * 60L * 60L * 1_000L

// Default playback speed used for the AudioPlayer
const val DEFAULT_AUDIO_PLAYBACK_SPEED = 1F

// Delay used to be sure that heights of views are calculated correctly
const val DELAY_FOR_VIEW_HEIGHT_CALCULATION = 800L

// Number of moments and FrontPages to keep while scrubbing
// Note that this is the overall number, so when a user logs in/out it does not keep them per IssueStatus
const val KEEP_LATEST_MOMENTS_COUNT = 15

// The scrubber will be started every X days
const val SCRUBBER_INTERVAL_DAYS = 30L

// Resource file names
const val TAZ_API_JS_FILENAME = "tazApi.js"
const val TAZ_API_CSS_FILENAME = "tazApi.css"

// category bookmarks for apiService.getCustomerData
const val CUSTOMER_DATA_CATEGORY_BOOKMARKS = "bookmarks"
const val CUSTOMER_DATA_CATEGORY_BOOKMARKS_ALL = "*"
const val CUSTOMER_DATA_VAL_DATE = "date"