# VirtualXposed — Android 11 fix build

VirtualXposed 0.22.0 with a fix for the Xposed Installer crash on Android 11 (NPE in ModuleUtil when VirtualApp's hooked `getInstalledPackages` returns entries with a null `applicationInfo`).

The GitHub Actions workflow clones the upstream repo (https://github.com/android-hacker/VirtualXposed), applies `fix.patch`, swaps the dead jcenter repo for mirrors, and builds the arm64 aosp debug APK with the original toolchain (Gradle 4.6 / AGP 3.2.1 / JDK 8 / NDK r17). The APK is published as a release asset.
