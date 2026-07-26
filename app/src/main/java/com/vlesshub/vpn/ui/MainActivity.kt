package com.vlesshub.vpn.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vlesshub.vpn.R
import com.vlesshub.vpn.core.VlessHubVpnService
import com.vlesshub.vpn.core.ProfileStore
import com.vlesshub.vpn.databinding.ActivityMainBinding
import com.vlesshub.vpn.model.ServerProfile
import com.vlesshub.vpn.util.ConfigParser
import com.vlesshub.vpn.util.SubscriptionImporter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ServerListAdapter
    private var connected = false

    private val vpnPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpn()
    }

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleIncomingConfig(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ServerListAdapter(
            onSelect = { profile ->
                ProfileStore.get(this).setActive(profile.id)
                adapter.submitList(ProfileStore.get(this).all(), profile.id)
            },
            onDelete = { profile ->
                ProfileStore.get(this).remove(profile.id)
                refreshList()
            },
            onRename = { profile -> showRenameDialog(profile) }
        )
        binding.serverList.layoutManager = LinearLayoutManager(this)
        binding.serverList.adapter = adapter

        binding.fabAddServer.setOnClickListener {
            startActivity(Intent(this, ServerEditActivity::class.java))
        }
        binding.fabScanQr.setOnClickListener {
            qrLauncher.launch(ScanOptions().setPrompt("Наведите камеру на QR-код конфига"))
        }
        binding.btnConnect.setOnClickListener { toggleConnection() }

        // Обработка vless:// vmess:// и т.д., если приложение открыто по ссылке
        intent?.dataString?.let { handleIncomingConfig(it) }

        refreshList()
    }

    private fun handleIncomingConfig(raw: String) {
        if (SubscriptionImporter.isHappAddLink(raw)) {
            importHappSubscription(raw)
            return
        }

        val profile = ConfigParser.parse(raw)
        if (profile != null) {
            ProfileStore.get(this).add(profile)
            ProfileStore.get(this).setActive(profile.id)
            refreshList()
        } else {
            Toast.makeText(this, "Не удалось распознать конфиг", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * happ://add/<url>[#title] — качаем JSON-конфиг с удалённого сервера и импортируем.
     * Загрузка сетевая, поэтому делаем это в корутине с показом статуса.
     */
    private fun importHappSubscription(raw: String) {
        Toast.makeText(this, "Загружаю конфиг…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            runCatching {
                SubscriptionImporter.importFromHappLink(raw)
            }.onSuccess { profiles ->
                if (profiles.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Сервер не вернул профилей", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                ProfileStore.get(this@MainActivity).addAll(profiles)
                ProfileStore.get(this@MainActivity).setActive(profiles.first().id)
                refreshList()
                Toast.makeText(
                    this@MainActivity,
                    "Добавлено профилей: ${profiles.size}",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRenameDialog(profile: ServerProfile) {
        val input = EditText(this).apply { setText(profile.remark) }
        AlertDialog.Builder(this)
            .setTitle("Название профиля")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    ProfileStore.get(this).update(profile.copy(remark = newTitle))
                    refreshList()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun refreshList() {
        val store = ProfileStore.get(this)
        adapter.submitList(store.all(), store.activeId())
    }

    private fun toggleConnection() {
        if (connected) {
            stopVpn()
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startVpn()
            }
        }
    }

    private fun startVpn() {
        val activeId = ProfileStore.get(this).activeId() ?: run {
            android.widget.Toast.makeText(this, "Сначала выбери сервер", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, VlessHubVpnService::class.java).apply {
            action = VlessHubVpnService.ACTION_CONNECT
            putExtra(VlessHubVpnService.EXTRA_PROFILE_ID, activeId)
        }
        startForegroundService(intent)
        connected = true
        binding.btnConnect.text = getString(R.string.disconnect)
    }

    private fun stopVpn() {
        val intent = Intent(this, VlessHubVpnService::class.java).apply {
            action = VlessHubVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        connected = false
        binding.btnConnect.text = getString(R.string.connect)
    }
}
