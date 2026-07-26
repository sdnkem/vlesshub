package com.vlesshub.vpn.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vlesshub.vpn.core.ProfileStore
import com.vlesshub.vpn.databinding.ActivityServerEditBinding
import com.vlesshub.vpn.util.ConfigParser
import com.vlesshub.vpn.util.SubscriptionImporter
import kotlinx.coroutines.launch

/**
 * Экран вставки конфиг-ссылки вручную:
 *  - прямые ссылки vless:// vmess:// trojan:// ss://
 *  - happ://add/<url>[#title] — подписка, качающая JSON-конфиг с сервера
 */
class ServerEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerEditBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener {
            val raw = binding.inputConfigUrl.text.toString().trim()
            if (raw.isEmpty()) return@setOnClickListener

            if (SubscriptionImporter.isHappAddLink(raw)) {
                importSubscription(raw)
                return@setOnClickListener
            }

            val profile = ConfigParser.parse(raw)
            if (profile == null) {
                Toast.makeText(this, "Некорректная ссылка конфига", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ProfileStore.get(this).add(profile)
            ProfileStore.get(this).setActive(profile.id)
            finish()
        }
    }

    private fun importSubscription(raw: String) {
        binding.btnSave.isEnabled = false
        Toast.makeText(this, "Загружаю конфиг…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            runCatching {
                SubscriptionImporter.importFromHappLink(raw)
            }.onSuccess { profiles ->
                if (profiles.isEmpty()) {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this@ServerEditActivity, "Сервер не вернул профилей", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                ProfileStore.get(this@ServerEditActivity).addAll(profiles)
                ProfileStore.get(this@ServerEditActivity).setActive(profiles.first().id)
                finish()
            }.onFailure { e ->
                binding.btnSave.isEnabled = true
                Toast.makeText(this@ServerEditActivity, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
