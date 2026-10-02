package com.rongyu.shixuji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)
        enableEdgeToEdge()
        // 主题统一在 MainScreen 里按用户设置（宋体 / 明暗）应用，这里不再多包一层
        setContent { MainScreen() }
    }
}
