<p align="center">
  <img src="V2rayNG/app/src/main/res/drawable-xxxhdpi/flow_logo.png" width="96" height="96" alt="Логотип Flow">
</p>

<h1 align="center">Flow</h1>

<p align="center">
  <a href="README.md"><kbd>English</kbd></a> · <strong>Русский</strong>
</p>

<p align="center">
  Нативный Android-клиент Xray на базе v2rayNG.<br>
  Интерфейс на Jetpack Compose и Material 3 Expressive.
</p>

<p align="center">
  <a href="https://github.com/iriskaaf/Flow/releases/latest"><img alt="Последний релиз" src="https://img.shields.io/github/v/release/iriskaaf/Flow?style=flat-square"></a>
  <a href="https://github.com/iriskaaf/Flow/releases"><img alt="Загрузки" src="https://img.shields.io/github/downloads/iriskaaf/Flow/total?style=flat-square"></a>
  <a href="LICENSE"><img alt="GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-4f46e5?style=flat-square"></a>
  <img alt="Android 7.0 и новее" src="https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=flat-square">
</p>

<p align="center">
  <a href="https://github.com/iriskaaf/Flow/releases/latest/download/Flow.apk"><strong>Скачать Flow.apk</strong></a>
  · <a href="https://github.com/iriskaaf/Flow/releases">Архив релизов</a>
  · <a href="https://github.com/iriskaaf/Flow/issues">Сообщить об ошибке</a>
</p>

## Скриншоты

Снято в Flow с демонстрационными подписками и тестовыми приложениями.

<table>
  <tr>
    <th>Подключение и избранное</th>
    <th>Сетка серверов</th>
    <th>Список серверов</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/home.png" width="240" alt="VPN подключён, видны избранные серверы"></td>
    <td><img src="docs/screenshots/servers-grid.png" width="240" alt="Карточки серверов тестовой подписки"></td>
    <td><img src="docs/screenshots/servers-list.png" width="240" alt="Список с описаниями провайдера и звёздочками"></td>
  </tr>
  <tr>
    <th>Развёрнутый поиск</th>
    <th>Маршрутизация</th>
    <th>Системное уведомление VPN</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/search.png" width="240" alt="Развёрнутый поиск и компактная сортировка"></td>
    <td><img src="docs/screenshots/routing.png" width="240" alt="Раздельная маршрутизация тестовых приложений"></td>
    <td><img src="docs/screenshots/vpn-notification.png" width="240" alt="Уведомление Flow VPN в шторке Android"></td>
  </tr>
</table>

## Возможности

- Импорт подписок и профилей из буфера обмена, QR-кода и ссылки. Поддержка HWID для совместимых HTTPS-подписок.
- Сетка и список серверов, поиск, сортировка по задержке, закреплённые избранные профили и произвольный порядок подписок.
- Три режима маршрутизации по приложениям и быстрое добавление распространённых российских сервисов.
- Плитка Android для подключения и отключения, системная светлая и тёмная темы, уведомления о подключении.
- Проверка и установка обновлений из GitHub Releases внутри приложения.

### Нативный Dynamic Island

Flow передаёт состояние VPN в системное уведомление. На совместимых устройствах с Android 16 и новее приложение запрашивает нативную [плашку Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update) с логотипом Flow и страной профиля. Обычное уведомление VPN доступно на поддерживаемых версиях Android при включённых уведомлениях. Отображение плашки зависит от прошивки и разрешений. Для Xiaomi Super Island также требуется допуск производителя. [Подробности об интеграции](README.Flow.md#статус-vpn-в-системе)

Переключатель находится в [Настройках → Dynamic Island](docs/screenshots/island-settings.png). Тестовый образ Android 16 показал обычное уведомление VPN, снятое для галереи.

## Установка

1. Скачайте универсальный `Flow.apk` из [последнего релиза](https://github.com/iriskaaf/Flow/releases/latest) и откройте файл на Android 7.0 или новее.
2. На вкладке **Серверы** нажмите **+** и импортируйте профиль или подписку. Flow подключается к серверу из вашего профиля.
3. Выберите сервер и включите VPN на главном экране. Android предложит подтвердить создание VPN при первом подключении.

### Обновления

Откройте **Настройки → О приложении → Проверить обновления**. Flow найдёт более свежий стабильный релиз на GitHub, скачает универсальный APK, сверит его SHA-256 и сравнит подпись с установленной версией. При установке Android покажет системное подтверждение.

Если Android запрашивает разрешение на установку приложений из Flow, его можно выдать в открывшемся системном экране. Для обновления вручную откройте APK из [GitHub Releases](https://github.com/iriskaaf/Flow/releases).

## Сборка из исходников

Понадобятся JDK 21 и Android SDK Platform 37. Откройте папку `V2rayNG` в Android Studio или выполните команды из терминала.

```sh
cd V2rayNG
./gradlew :app:assemblePlaystoreDebug :app:testPlaystoreDebugUnitTest
```

На Windows используйте `gradlew.bat`. APK появится в `V2rayNG/app/build/outputs/apk/playstore/debug/`. В репозитории есть нативные библиотеки для `arm64-v8a`, `armeabi-v7a`, `x86_64` и `x86`. Локальная отладочная сборка подписывается ключом вашей среды.

[Подробности о HWID, уведомлениях и системных индикаторах](README.Flow.md)

## Дизайн и лицензия

- Макеты интерфейса в [Figma](https://www.figma.com/design/aZIeNjiyeOGRtb2uTJ8f8j/XPRMNTS?node-id=5724-1740).
- Исходная база [v2rayNG 2.1.8](https://github.com/2dust/v2rayNG/tree/2.1.8). Сетевое ядро [Xray-core](https://github.com/XTLS/Xray-core) подключено через [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite/tree/v26.5.19). Для TUN используется [HEV SOCKS5 Tunnel](https://github.com/heiher/hev-socks5-tunnel/tree/dfabaa9cadf8894eb8ebfa0f75b1fee331869b34).
- Код Flow распространяется по [GPL-3.0](LICENSE).
