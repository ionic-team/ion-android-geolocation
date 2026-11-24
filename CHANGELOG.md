# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### 2026-11-24

- Chore: Depend on `io.ionic.libs:ionnativeislands-android:1.0.0` from Maven Central instead of a bundled AAR.

### 2026-11-18

- Feature: The Location Button is the Jetpack Compose `LocationButton` (`androidx.core.locationbutton:locationbutton-compose:1.0.0-alpha01`, still an alpha release), hosted in a `ComposeView`, instead of the Views `LocationButton`, which can show Google's compat button next to the system button on Android 17 ([issue tracker](https://issuetracker.google.com/issues/567953859)).
- BREAKING CHANGE: The host Activity must be a `ComponentActivity` (`AppCompatActivity` is one), as the Compose button finds its Activity and permission launcher through it.

### 2026-11-16

- BREAKING CHANGE: The library is now a single AAR that includes the Location Button, built with `compileSdk` 37. Consumers must build with `compileSdk` 37 or higher (the AAR declares `minCompileSdk = 37`). Builds on `compileSdk` 36 (MABS 12) can no longer use this library, including for the existing geolocation methods.
- BREAKING CHANGE: The minimum supported Android version is now API 26 (`minSdk` 26, previously 24).
- BREAKING CHANGE: Consuming builds now need Android Gradle Plugin 9.1 or higher and Kotlin 2.1 or higher, as required by `androidx.core.locationbutton`. The library is built with Kotlin 2.2 and depends on `kotlin-stdlib` 2.2.x.
- Feature: Depend on `androidx.core.locationbutton:locationbutton-compose:1.0.0-alpha01`, which is still an alpha release (the latest published), so its API may change incompatibly. The `android.permission.USE_LOCATION_BUTTON` permission is now added to the merged manifest.
- Chore: Update build tooling (Gradle 9.4.1, AGP 9.2.1, nexus publish plugin 2.0.0, SonarQube plugin 7.6.0). The publishing script no longer uses the `-b` Gradle flag, which Gradle 9 removed.

### 2026-07-21

- Feature: Add a Native Islands Location Button component with grant, position, and error events.

## [3.0.0]

### 2026-10-30

- **Breaking:** minSdk raised from 23 to 24.
- Chore: Update dependencies and build tooling (Gradle 8.14.3, AGP 8.13.0, minSdk 24, androidx libraries, coroutines 1.9.0, test libraries).

## [2.2.2]

### 2026-08-04

- Fix: The result of the enable-location dialog was delivered only to the most recent request, leaving earlier concurrent requests waiting forever [RMET-5383](https://outsystemsrd.atlassian.net/browse/RMET-5383).

## [2.2.1]

### 2026-06-15

- Fix: Return error when user rejects request to turn on location with `enableLocationManagerFallback=true`. Prior to this fix, it would try to retrieve the location nonetheless and timeout.

## [2.2.0]

### 2026-02-02
- Feature: Add support for magnetic and true heading using device sensors (available for location updates only).
- Feature: Add `magneticHeading`, `trueHeading`, `headingAccuracy`, and `course` to `IONGLOCLocationResult`.

## [2.1.0]

### 2025-10-31

- Feature: Add native timeout to `watchPosition` Add new `interval` to control location updates without `timeout` variable.

## [2.0.0]

### 2025-09-30

- Feature: Allow using a fallback if Google Play Services fails.

BREAKING CHANGE: The constructor for the controller and some of its methods have changed signature.
You will need to change how your application calls the library if you update to this version.

### 2025-06-26

- Migrate publishing from OSSRH to Central Portal.

## [1.0.0]

### 2025-01-20
- Refactor: Rename library to `IONGeolocationLib`.

### 2025-01-08
- Feat: Add workflows to run unit tests and release the library in Maven repo.

### 2025-01-07
- Feat: Add implementation for whole library, including `getCurrentPosition`, `watchPosition`, and `clearWatch`.
- Chore: Create repository.
