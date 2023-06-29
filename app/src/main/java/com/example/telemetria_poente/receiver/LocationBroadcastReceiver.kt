package com.example.telemetria_poente.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.telemetria_poente.service.LocationService

class LocationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val sharedPreferences: SharedPreferences =
                context.getSharedPreferences("appPreferences", Context.MODE_PRIVATE)
            val switchState = sharedPreferences.getBoolean("switchState", false)

            // Iniciar o serviço somente se o estado do switch estiver ligado
            if (switchState) {
                val serviceIntent = Intent(context, LocationService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
