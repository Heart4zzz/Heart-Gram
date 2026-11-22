# HeartGram

HeartGram is an unofficial Android messaging client that uses the Telegram API. It is based on [Telegram for Android](https://github.com/TelegramMessenger/Telegram-Android). The [GPL license](LICENSE) and the notices for the original source and dependencies are retained.

## Build

Install Android SDK 36, Build Tools 36.0.0, NDK 27.2.12479018, and CMake 3.22.1. Use JDK 21 for the Gradle wrapper.

```sh
git submodule update --init --recursive
```

Copy `local.properties.example` to `local.properties`. Set `sdk.dir` or `ANDROID_HOME` and obtain your own [Telegram API credentials](https://core.telegram.org/api/obtaining_api_id). Enter `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` in the local file. Environment variables with the same names can be used in CI. A build with those fields blank can be compiled and inspected, and requires configured credentials to sign in.

```sh
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_AppStandalone:assembleAfatDebug
```

The standalone app defaults to the package `org.heartgram.client`. For a release, configure `HEARTGRAM_SIGNING_*` privately, keep a backup of your signing key, and use `:TMessagesProj_AppStandalone:assembleAfatStandalone`. The project uses Android's local debug signing for debug builds. A build signed with a different key or using a different package name installs separately from earlier builds.

Optional Firebase configuration belongs in `TMessagesProj_AppStandalone/google-services.json`. Configure it for your actual package name to use Firebase push notifications. An optional Google Maps key goes in `HEARTGRAM_MAPS_API_KEY`.

## Shared profiles and local settings

[The optional profile service](heartgram-profile-server/README.md) documents how to configure your own server and bot. Set `HEARTGRAM_PROFILE_URL` and `HEARTGRAM_OWNER_ID` in the Android build configuration for that deployment. The app reads public profile decorations from the configured server using Telegram user IDs. Publishing a decoration uses Telegram bot confirmation. Keep the bot token, webhook secret and server database on the server. The endpoint and owner ID supplied to Android are visible in the APK.

Edit history and retained deleted text are device-local features. The public source tree includes original launcher art and the cat illustration. The font chooser uses Android's built-in font families and supports importing a font locally.

The app uses normal Telegram presence, typing, story-view and read updates. For distribution, review the [Telegram API terms](https://core.telegram.org/api/terms) and include the corresponding source and license notices with your release.
