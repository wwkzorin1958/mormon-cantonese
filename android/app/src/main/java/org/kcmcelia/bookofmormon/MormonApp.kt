package org.kcmcelia.bookofmormon

import android.app.Application

/**
 * Named MormonApp, not BomApp, so it cannot collide with the ui.BomApp composable.
 */
class MormonApp : Application()
