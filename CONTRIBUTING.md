# Contributing

Bug reports and focused pull requests are welcome.

1. Open an issue describing the behavior and expected result.
2. Keep changes scoped and avoid including Bluetooth captures or credentials.
3. Run `./gradlew testDebugUnitTest assembleDebug` before submitting a pull
   request.
4. Explain which Karoo and camera firmware versions were tested.
5. License contributions under GPL-3.0-only, the same license as AeroCam.

Changes to the proven BLE connection lifecycle should include focused tests and
manual regression results for camera-on boot, delayed camera power-on,
out-of-range recovery, camera reboot, and deliberate camera power-off.

