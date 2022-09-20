package de.taz.app.android

import java.text.SimpleDateFormat
import java.util.*

const val TAZ_AUTH_HEADER = "X-tazAppAuthKey"

const val DEBUG_VERSION_DOWNLOAD_ENDPOINT = "https://dl.taz.de/down/taz.neo.debug.apk"

// UI
const val DEFAULT_MOMENT_RATIO = "0.670219:1"
const val DEFAULT_MOMENT_FILE = "noInternetMoment.png"
const val DEFAULT_NAV_DRAWER_FILE_NAME = "navButton.taz.normal.png"
const val SUBSCRIPTION_EMAIL_ADDRESS = "digiabo@taz.de"
const val WEBVIEW_DRAG_SENSITIVITY_FACTOR = 2
const val WEBVIEW_HTML_FILE = "htmlFile"
const val WEBVIEW_HTML_FILE_TERMS = "welcomeTerms.html"
const val WEBVIEW_HTML_FILE_REVOCATION = "welcomeRevocation.html"
const val WEBVIEW_HTML_FILE_DATA_POLICY = "welcomeSlidesDataPolicy.html"
const val WEBVIEW_JQUERY_FILE = "jquery-3.min.js"
const val WEBVIEW_TAP_TO_SCROLL_OFFSET = 350
const val WEEKEND_TYPEFACE_RESOURCE_FILE_NAME = "knile-semibold-webfont.woff"

const val MIN_TEXT_SIZE = 30
const val MAX_TEST_SIZE = 200

// Settings
const val ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE = "ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE"

// General Preferences
const val DRAWER_SHOW_NUMBER = 1

// regex RFC 5322 from SO https://stackoverflow.com/questions/201323/how-can-i-validate-an-email-address-using-a-regular-expression
const val W3C_EMAIL_PATTERN =
    """(?:[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])"""

// regex for password 8 chars, 1 letter, 1 digit, 1 special char
const val PASSWORD_PATTERN = """^.*(?=.{8,})(?=.*[a-zA-Z])(?=.*\d)(?=.*[\W]).*$"""

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