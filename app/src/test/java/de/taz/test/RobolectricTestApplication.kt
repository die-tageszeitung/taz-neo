package de.taz.test

import android.app.Application

// We have to overwrite the default AbstractTazApplication, because it would already initialize some
// Repositories and Sentry integration etc.
class RobolectricTestApplication : Application()