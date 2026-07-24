package com.example.audiobible.fragments

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp // Вот этот маркер заставит Hilt ожить и уберет краш в AppActivity
class App : Application()