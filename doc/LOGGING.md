# Logging Documentation

This document explains how to view, configure, and extend logging in the JRiver Multiplatform Client.

## 1. Architecture
The project uses **SLF4J** as a logging facade, with **Logback** as the concrete implementation for JVM and Android targets. Networking logs are provided by the **Ktor Logging Plugin**.

## 2. Viewing Logs by Platform

### JVM (Desktop)
Logs are printed to the standard output (`STDOUT`).
*   **During Development**: Run `./gradlew :composeApp:run`. Logs will appear in your terminal.
*   **Configuration**: Edit `composeApp/src/jvmMain/resources/logback.xml`.
*   **Example Output**:
    ```text
    14:20:05.123 [main] INFO  io.ktor.client.HttpClient - REQUEST: http://192.168.1.50:52199/MCWS/v1/Alive
    14:20:05.456 [main] INFO  io.ktor.client.HttpClient - RESPONSE: 200 OK
    ```

### Android
Logs are sent to **Logcat**.
*   **Viewing**: Use the "Logcat" tab in Android Studio or run `adb logcat` from the command line.
*   **Filtering**: Filter by tag `HttpClient` for network logs or by the application's package name `com.example.jrr`.
*   **Configuration**: Android uses the same `logback-classic` dependency, but you can also use native `android.util.Log` for platform-specific debugging.

### iOS
Logs are sent to the **System Log** and appear in the Xcode console.
*   **Viewing**: Open the `iosApp` project in Xcode and run the application. Logs appear in the "Debug Area" at the bottom.
*   **KMP Context**: `println()` statements in `commonMain` are automatically routed to `NSLog` on iOS.

## 3. Configuring Ktor Network Logs
The Ktor client is configured in `composeApp/src/commonMain/kotlin/com/example/jrr/App.kt`.

To change the verbosity of network logs, adjust the `LogLevel`:

```kotlin
val httpClient = HttpClient {
    install(Logging) {
        level = LogLevel.INFO // Options: ALL, HEADERS, BODY, INFO, NONE
    }
}
```

## 4. Troubleshooting "No SLF4J providers"
If you see the warning `No SLF4J providers were found`, ensure that `libs.logback.classic` is included in the `dependencies` block of your platform-specific source set in `build.gradle.kts`.

```kotlin
// Example for jvmMain
jvmMain.dependencies {
    implementation(libs.logback.classic)
}
```

## 5. Adding Custom Logs
To add your own logs in `commonMain`, you can use `println()` for simple debugging, or integrate a multiplatform logging library like [Napier](https://github.com/AAiraa/Napier) or [Kermit](https://github.com/touchlab/Kermit) if more advanced features (like log levels and crashlytics integration) are required.
