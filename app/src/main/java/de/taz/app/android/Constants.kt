package de.taz.app.android

import java.text.SimpleDateFormat
import java.util.*

const val TAZ_AUTH_HEADER = "X-tazAppAuthKey"

const val DEBUG_VERSION_DOWNLOAD_ENDPOINT = "https://dl.taz.de/down/taz.neo.debug.apk"

// UI
const val DEFAULT_MOMENT_RATIO = "W,1.5:1"
const val DEFAULT_NAV_DRAWER_FILE_NAME = "navButton.taz.normal.png"
const val SUBSCRIPTION_EMAIL_ADDRESS = "app@taz.de"
const val WEBVIEW_DRAG_SENSITIVITY_FACTOR = 2
const val WEBVIEW_HTML_FILE = "htmlFile"
const val WEBVIEW_HTML_FILE_TERMS = "welcomeTerms.html"
const val WEBVIEW_HTML_FILE_REVOCATION = "welcomeRevocation.html"
const val WEBVIEW_JQUERY_FILE = "jquery-3.min.js"
const val WEEKEND_TYPEFACE_RESOURCE_FILE_NAME = "knile-semibold-webfont.woff"

const val MIN_TEXT_SIZE = 30
const val MAX_TEST_SIZE = 200

// Settings
const val ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE = "ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE"

// General Preferences
const val DRAWER_SHOW_NUMBER = 1

const val W3C_EMAIL_PATTERN =
    """^[a-zA-Z0-9.!#${'$'}%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*${'$'}"""

const val MAX_SIMULTANEOUS_QUERIES = 8
const val MAX_SIMULTANEOUS_DOWNLOADS = 8
const val LOADING_SCREEN_FADE_OUT_TIME = 500L
const val CONNECTION_FAILURE_BACKOFF_TIME_MS = 1000L
const val MAX_BYTES = 4294967296 // = 2^32

// feed
const val DISPLAYED_FEED = "taz"

// onSaveInstanceState
const val DISPLAYABLE_NAME = "displayableName"

const val PUBLIC_FOLDER = "public"

// SimpleDateFormat is not thread safe!!! We need to create to create a instance for every thread context
val simpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

const val COPY_BUFFER_SIZE = 100 * 1024 // 100kiB