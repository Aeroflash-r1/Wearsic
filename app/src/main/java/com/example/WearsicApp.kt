package com.example

import android.app.Application
import com.example.di.AppContainer

class WearsicApp : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
