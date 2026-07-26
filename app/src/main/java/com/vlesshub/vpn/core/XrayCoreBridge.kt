package com.vlesshub.vpn.core

/**
 * Тонкая Kotlin-обёртка над Go-библиотекой Xray-core, собранной через gomobile
 * в xray-core.aar (см. /BUILDING_CORE.md в корне проекта).
 *
 * Сама Go-библиотека экспортирует что-то вроде:
 *
 *   package xraybridge
 *   func StartFromJSON(configJson string) error
 *   func Stop()
 *   func QueryStats(tag string) int64
 *
 * Здесь мы просто прокидываем вызовы к JNI-обёртке, которую генерирует gomobile
 * (пакет libv2ray / AndroidLibXrayLite тоже подходит и уже готов, если не хочется
 * компилировать Go самому — см. README).
 */
object XrayCoreBridge {

    @Volatile
    private var running = false

    /**
     * Запускает ядро с готовым JSON-конфигом (см. XrayConfigBuilder.build()).
     * Бросает исключение, если конфиг некорректен или порт занят.
     */
    fun start(configJson: String) {
        // TODO: заменить на реальный вызов сгенерированного gomobile-биндинга, например:
        // libv2ray.Libv2ray.runV2Ray(configJson, cacheDir)
        // На этапе прототипа — просто заглушка, чтобы верхнеуровневая логика
        // (UI, VpnService, парсер) уже работала и её можно было тестировать.
        running = true
    }

    fun stop() {
        // TODO: libv2ray.Libv2ray.stopV2Ray()
        running = false
    }

    fun isRunning(): Boolean = running

    /** Байты, прошедшие через прокси-outbound с момента старта (для UI-статистики). */
    fun queryUplinkBytes(): Long = 0L
    fun queryDownlinkBytes(): Long = 0L
}
