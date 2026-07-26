package com.vlesshub.vpn.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.vlesshub.vpn.model.ServerProfile
import com.vlesshub.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Системный VPN-туннель. Поднимает TUN-интерфейс, весь трафик заворачивает
 * в локальный SOCKS порт, поднятый Xray-core (через tun2socks).
 *
 * Реальная связка tun-fd -> socks обычно делается через отдельный нативный
 * бинарь tun2socks (Sing-box or hev-socks5-tunnel), который тоже нужно
 * положить в jniLibs при сборке — см. BUILDING_CORE.md.
 */
class VlessHubVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.vlesshub.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.vlesshub.vpn.DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        private const val CHANNEL_ID = "happ_vpn_channel"
        private const val NOTIF_ID = 1

        private const val TUN_ADDRESS = "10.10.10.1"
        private const val TUN_ROUTE = "0.0.0.0"
        private const val TUN_DNS = "1.1.1.1"
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                disconnect()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val profile = ProfileStore.get(applicationContext)
                    .find(intent.getStringExtra(EXTRA_PROFILE_ID))
                if (profile != null) {
                    connect(profile)
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun connect(profile: ServerProfile) {
        startForeground(NOTIF_ID, buildNotification(profile.remark))

        scope.launch {
            runCatching {
                val configJson = XrayConfigBuilder.build(profile)
                XrayCoreBridge.start(configJson)
                establishTunnel()
            }.onFailure {
                disconnect()
            }
        }
    }

    private fun establishTunnel() {
        val builder = Builder()
            .setSession("VlessHub")
            .addAddress(TUN_ADDRESS, 24)
            .addRoute(TUN_ROUTE, 0)
            .addDnsServer(TUN_DNS)
            .setMtu(1500)

        // Исключаем собственное приложение из туннеля, чтобы не было петли
        runCatching { builder.addDisallowedApplication(packageName) }

        tunInterface = builder.establish()

        // TODO: запустить tun2socks, отдав ему tunInterface.fd и локальный SOCKS-порт
        // из XrayConfigBuilder.localSocksPort(). Пример (hev-socks5-tunnel):
        //   TProxyService.startTProxy(tunInterface.fd, "127.0.0.1", XrayConfigBuilder.localSocksPort())
    }

    private fun disconnect() {
        runCatching { XrayCoreBridge.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onRevoke() {
        disconnect()
        super.onRevoke()
    }

    private fun buildNotification(remark: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Подключено: $remark")
            .setContentText("Нажмите, чтобы открыть VlessHub")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }
}
