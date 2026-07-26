# Как собрать ядро и запустить проект

Этот проект — Kotlin-обёртка (UI, парсер конфигов, VPN-сервис) вокруг
движка **Xray-core**. Сам движок написан на Go и должен быть скомпилирован
отдельно под Android — я не смог сделать это здесь, так как у меня нет
доступа в интернет в этой сессии. Есть два пути, от простого к сложному.

## Вариант A (быстрый) — взять готовое ядро из open-source проекта

Есть готовые, уже собранные под Android библиотеки ядра, которые используют
v2rayNG и подобные клиенты:

1. Клонируй `https://github.com/2dust/AndroidLibXrayLite` (аналогично можно
   взять `libv2ray` из v2rayNG).
2. Собери `.aar` по инструкции в их README (нужен Go 1.21+, gomobile,
   Android NDK).
3. Положи готовый `xray-core.aar` в `app/libs/`.
4. В `app/build.gradle.kts` раскомментируй строку:
   ```kotlin
   implementation(files("libs/xray-core.aar"))
   ```
5. В `XrayCoreBridge.kt` замени заглушки на реальные вызовы, например:
   ```kotlin
   import libv2ray.Libv2ray

   fun start(configJson: String) {
       Libv2ray.runV2Ray(configJson, context.cacheDir.absolutePath)
   }
   fun stop() = Libv2ray.stopV2Ray()
   ```

## Вариант B (свой билд) — компилируем Xray-core сам

```bash
# 1. Установи Go 1.21+, затем gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

# 2. Клонируй xray-core
git clone https://github.com/XTLS/Xray-core.git
cd Xray-core

# 3. Собери AAR под Android (arm64 + armv7)
gomobile bind -target=android -androidapi 24 \
  -o xray-core.aar ./main

# 4. Скопируй результат
cp xray-core.aar /path/to/VlessHub/app/libs/
```

Обрати внимание: голое ядро Xray-core не экспортирует высокоуровневый API
"start/stop по JSON" само по себе — обычно для этого пишут тонкую Go-обёртку
(пакет вроде `libv2ray`), которая уже есть готовая в AndroidLibXrayLite —
поэтому вариант A обычно быстрее.

## tun2socks — соединяем системный VPN-туннель с локальным SOCKS

`VpnService` в Android даёт тебе только файловый дескриптор TUN-интерфейса
(сырые IP-пакеты). Чтобы завернуть их в SOCKS-порт, который слушает
Xray-core, нужен tun2socks. Готовые варианты:

- **hev-socks5-tunnel** — https://github.com/heiher/hev-socks5-tunnel
  (лёгкий, на C, есть готовый JNI-модуль под Android)
- **sing-tun** (из проекта sing-box) — если захочешь мигрировать на
  sing-box вместо Xray-core, там уже всё интегрировано из коробки

Собранный `.so` кладётся в `app/src/main/jniLibs/<abi>/`, и в
`VlessHubVpnService.establishTunnel()` в месте с `// TODO` вызывается его JNI-метод
с `tunInterface.fd` и портом `XrayConfigBuilder.localSocksPort()` (10808).

## Сборка самого Android-приложения

```bash
cd VlessHub
./gradlew assembleDebug
# APK будет в app/build/outputs/apk/debug/
```

(gradlew wrapper не приложен — открой проект в Android Studio, она
сама предложит сгенерировать wrapper, либо запусти `gradle wrapper`
при наличии локальной установки Gradle 8.7+.)

## Что уже готово в этом прототипе

- ✅ Парсер ссылок vless:// vmess:// trojan:// ss:// → единая модель `ServerProfile`
- ✅ Генератор JSON-конфига для Xray-core из `ServerProfile` (`XrayConfigBuilder`)
- ✅ Хранилище списка серверов (SharedPreferences)
- ✅ UI: список серверов, добавление по ссылке, сканирование QR (ZXing),
  приём deep-link (клик по vless:// ссылке в Telegram сразу открывает приложение)
- ✅ `VpnService` с системным TUN-интерфейсом, foreground-уведомлением

## Что нужно доделать руками

- ⬜ Подключить реальное ядро (Вариант A или B выше)
- ⬜ Подключить tun2socks и вызвать его в `establishTunnel()`
- ⬜ per-app routing (список приложений, которые идут в обход VPN) —
  под это уже есть заготовка `builder.addDisallowedApplication`
- ⬜ Иконки/лаунчер-иконка (сейчас использую системные заглушки)
