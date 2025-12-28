# MoneroDroid

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="MoneroDroid Logo" width="120"/>
</p>

<p align="center">
  <strong>Run a Monero full node on your Android device</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#requirements">Requirements</a> •
  <a href="#installation">Installation</a> •
  <a href="#building-from-source">Building</a> •
  <a href="#usage">Usage</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#license">License</a>
</p>

---

## Features

- **Full Monero Node** - Run a complete Monero node directly on your Android device
- **Pruned Node Support** - Choose between full node (~300GB) or pruned node (~50GB) to save storage
- **External Storage** - Use internal storage or external SD card for blockchain data
- **Background Service** - Node runs as a foreground service with persistent notification
- **Start on Boot** - Optionally auto-start the node when your device boots
- **Real-time Status** - Monitor sync progress, peer connections, and node health
- **Auto-Update** - Check for and install monerod updates directly from the app
- **Material 3 Design** - Modern dark theme with Monero orange accents

## Screenshots

| Main Screen | Settings | Syncing |
|:-----------:|:--------:|:-------:|
| Node status and controls | Configuration options | Sync progress |

## Requirements

### Device Requirements

- **Android Version:** Android 7.0 (API 24) or higher
- **Architecture:** ARM64 (arm64-v8a) or ARM32 (armeabi-v7a)
- **Storage:** 
  - Pruned node: ~50GB free space
  - Full node: ~300GB free space
- **RAM:** 2GB+ recommended
- **Network:** Stable internet connection for P2P sync

### Permissions

MoneroDroid requires the following permissions:

| Permission | Purpose |
|------------|---------|
| `INTERNET` | P2P network and RPC communication |
| `FOREGROUND_SERVICE` | Keep node running in background |
| `WAKE_LOCK` | Prevent device sleep during sync |
| `POST_NOTIFICATIONS` | Show node status notification |
| `RECEIVE_BOOT_COMPLETED` | Start on boot feature |
| `MANAGE_EXTERNAL_STORAGE` | SD card storage support (Android 11+) |

## Installation

### Download APK

Download the latest release from the [Releases](https://github.com/sevendeuce/monerodroid/releases) page.

### Install

1. Enable "Install from unknown sources" in your device settings
2. Open the downloaded APK file
3. Follow the installation prompts
4. Grant required permissions when prompted

## Building from Source

### Prerequisites

- **Android Studio** Arctic Fox (2020.3.1) or later
- **JDK 17** or higher
- **Android SDK** with API level 35
- **Gradle 8.9** (included via wrapper)

### Clone Repository

```bash
git clone https://github.com/sevendeuce/monerodroid.git
cd monerodroid
```

### Build Debug APK

```bash
# Using Gradle wrapper
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Build Release APK

```bash
# Unsigned release
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Build with Android Studio

1. Open Android Studio
2. Select **File > Open** and navigate to the project directory
3. Wait for Gradle sync to complete
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**

### Signing Release APK

Create a keystore for signing:

```bash
keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias monerodroid
```

Sign the APK:

```bash
# Build signed release
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=path/to/release-keystore.jks \
  -Pandroid.injected.signing.store.password=YOUR_STORE_PASSWORD \
  -Pandroid.injected.signing.key.alias=monerodroid \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD
```

## Project Structure

```
monerodroid/
├── app/
│   ├── src/main/
│   │   ├── java/com/sevendeuce/monerodroid/
│   │   │   ├── data/           # Data models (NodeState, RpcResponse)
│   │   │   ├── service/        # NodeService, BootReceiver
│   │   │   ├── ui/
│   │   │   │   ├── screens/    # MainScreen, SettingsScreen
│   │   │   │   └── theme/      # Colors, Typography, Theme
│   │   │   ├── util/           # Managers and utilities
│   │   │   │   ├── MonerodBinaryManager.kt
│   │   │   │   ├── MonerodProcess.kt
│   │   │   │   ├── ConfigManager.kt
│   │   │   │   ├── StorageManager.kt
│   │   │   │   ├── NodeRpcClient.kt
│   │   │   │   └── ArchitectureDetector.kt
│   │   │   ├── viewmodel/      # MainViewModel
│   │   │   └── MainActivity.kt
│   │   ├── jniLibs/            # Bundled monerod binaries
│   │   │   ├── arm64-v8a/libmonerod.so
│   │   │   └── armeabi-v7a/libmonerod.so
│   │   └── res/                # Resources
│   └── build.gradle.kts
├── .github/workflows/          # CI/CD pipelines
├── gradle/
│   └── libs.versions.toml      # Dependency versions
└── build.gradle.kts
```

## Usage

### First Launch

1. **Grant Permissions** - Accept storage permission to allow blockchain data storage
2. **Choose Storage** - Select internal or external storage for blockchain data
3. **Select Node Type** - Toggle between pruned (~50GB) or full node (~300GB)
4. **Start Node** - Tap the power button to start the node

### Running the Node

- The node runs as a foreground service with a persistent notification
- Sync progress is displayed on the main screen
- Peer count shows network connectivity status
- Use the notification to quickly stop the node

### Stopping the Node

- Tap the power button on the main screen, OR
- Tap "Stop" on the notification
- Wait for graceful shutdown (may take up to 30 seconds)

## Configuration

### Settings

| Setting | Description | Default |
|---------|-------------|---------|
| **Pruned Node** | Use blockchain pruning to reduce storage | Enabled |
| **Start on Boot** | Auto-start node when device boots | Disabled |
| **Storage Location** | Internal or external (SD card) storage | Internal |

### Network Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 18080 | P2P | Peer-to-peer network |
| 18081 | RPC | Local RPC (localhost only) |
| 18089 | RPC | Restricted RPC (network accessible) |

### Generated Config File

The app generates `monerod.conf` with optimized settings:

```ini
data-dir=/path/to/blockchain
prune-blockchain=1
p2p-bind-ip=0.0.0.0
p2p-bind-port=18080
rpc-bind-ip=127.0.0.1
rpc-bind-port=18081
rpc-restricted-bind-ip=0.0.0.0
rpc-restricted-bind-port=18089
out-peers=32
in-peers=32
```

## Updating Monerod

MoneroDroid can check for and install monerod updates:

1. Open **Settings**
2. Scroll to **MONEROD UPDATE** section
3. Tap **Check for Updates**
4. If an update is available, tap **Update Monerod**
5. Wait for download and installation to complete

**Note:** The node must be stopped before updating.

## Troubleshooting

### Node Won't Start

- Ensure sufficient storage space (50GB+ for pruned, 300GB+ for full)
- Check that storage permission is granted
- Verify the monerod binary is installed (download if needed)

### Sync is Slow

- Ensure stable internet connection
- Check peer count (should be 8+ for good connectivity)
- Consider using a pruned node if storage is limited

### High Battery Usage

- This is expected during initial sync
- Battery usage decreases significantly once synced
- Consider keeping device plugged in during initial sync

### App Crashes

- Clear app data and restart
- Reinstall the app
- Check for sufficient RAM (close other apps)

## CI/CD

This project uses GitHub Actions for continuous integration:

### Automatic Builds

Every push to `main` triggers:
- Debug APK build
- Release APK build (unsigned)
- Unit tests

### Creating a Release

```bash
# Tag a new version
git tag v1.0.0
git push origin v1.0.0
```

This automatically:
- Builds signed release APK (if secrets configured)
- Creates GitHub Release with APK attached
- Generates release notes

### GitHub Secrets for Signed Releases

Configure these secrets in your repository:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias name |
| `KEY_PASSWORD` | Key password |

Encode keystore:
```bash
base64 -i release-keystore.jks | tr -d '\n'
```

## Tech Stack

- **Language:** Kotlin 2.0
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM with StateFlow
- **Storage:** DataStore Preferences
- **Networking:** OkHttp 4
- **JSON:** Gson
- **Async:** Kotlin Coroutines
- **Build:** Gradle 8.9 with Kotlin DSL

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.04.01 | UI framework |
| Material 3 | Latest | Design system |
| Lifecycle ViewModel | 2.9.0 | ViewModel + Compose |
| DataStore | 1.1.1 | Preferences storage |
| OkHttp | 4.12.0 | HTTP client |
| Gson | 2.10.1 | JSON parsing |
| Coroutines | 1.8.1 | Async operations |

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features

## Security

- RPC port 18081 is bound to localhost only
- Restricted RPC (18089) allows limited remote access
- No wallet functionality - this is a node-only app
- All connections to getmonero.org use HTTPS

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 SevenDeuce

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Acknowledgments

- [Monero Project](https://getmonero.org) - For the monerod daemon
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- The Monero community for their support

## Disclaimer

This software is provided for educational and personal use. Running a Monero node contributes to the decentralization and security of the Monero network. The developers are not responsible for any misuse of this software.

---

<p align="center">
  Made with ❤️ for the Monero community
</p>
