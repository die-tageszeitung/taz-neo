package de.taz.app.android

import java.text.SimpleDateFormat
import java.util.Locale

const val TAZ_AUTH_HEADER = "X-tazAppAuthKey"

const val DEBUG_VERSION_DOWNLOAD_ENDPOINT = "https://dl.taz.de/down/taz.neo.debug.apk"

// UI
const val DEFAULT_MOMENT_RATIO = "0.670219:1"
const val DEFAULT_MOMENT_FILE = "noInternetMoment.png"
const val SUBSCRIPTION_EMAIL_ADDRESS = "app@taz.de"
const val TAZ_ACCOUNT_SUFFIX = "@taz.de"
const val WEBVIEW_DRAG_SENSITIVITY_FACTOR = 2
const val WEBVIEW_HTML_FILE_TERMS = "welcomeTerms.html"
const val WEBVIEW_HTML_FILE_REVOCATION = "welcomeRevocation.html"
const val WEBVIEW_HTML_FILE_DATA_POLICY = "welcomeSlidesDataPolicy.html"
const val WEBVIEW_HTML_FILE_SEARCH_HELP = "searchHelp.html"
const val WEBVIEW_JQUERY_FILE = "jquery-3.min.js"
const val WEBVIEW_TAP_TO_SCROLL_OFFSET = 350
const val KNILE_SEMIBOLD_RESOURCE_FILE_NAME = "knile-semibold-webfont.woff"
const val KNILE_REGULAR_RESOURCE_FILE_NAME = "knile-regular-webfont.woff"
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