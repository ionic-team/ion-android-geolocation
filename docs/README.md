# OSGeolocationLib

The `OSGeolocationLib-Android` is a library built using `Kotlin` that provides geolocation features for Android applications.

The `OSGLOCController` class provides the main features of the Library, which are:
- obtaining the location/position of the device a single time;
- adding a watch to obtain periodic location updates;
- clearing/removing a previously added watch, turning off location updates.


## Index

- [Motivation](#motivation)
- [Usage](#usage)
- [Methods](#methods)
    - [Obtain the current location of the device](#obtain-the-current-location-of-the-device)
    - [Add a watch for periodic location updates](#add-a-watch-for-periodic-location-updates)
    - [Clear a watch that was added previously](#clear-a-watch-that-was-added-previously)

## Motivation

This library is to be used by the Geolocation Plugin for [OutSystems' Cordova Plugin](https://github.com/ionic-team/cordova-outsystems-geolocation) and [Ionic's Capacitor Plugin](https://github.com/ionic-team/outsystems-geolocation).

## Usage

In your app-level gradle file, import the `OSGeolocationLib` library like so:

    dependencies {
    	implementation("com.capacitorjs:osgeolocation-android:1.0.0")
	}


## Methods

As mentioned before, the library offers the `OSGLOCController` class that provides the following methods to interact with:

### Obtain the current location of the device

```kotlin
suspend fun getCurrentPosition(
    activity: Activity, 
    options: OSGLOCLocationOptions
): Result<OSGLOCLocationResult>
```

The method is composed of the following input parameters:
- **activity**: the `Activity` from the app using the library to use when obtaining the location.
- **options**: `OSGLOCLocationOptions` with the options with which to make the location request with (e.g. timeout).

The method returns a `Result` containing either an object of type `OSGLOCLocationResult`, which includes the geolocation data (e.g. latitide, longitude), or an exception that should be handled by the caller app.

### Add a watch for periodic location updates

```kotlin
fun addWatch(
    activity: Activity,
    options: OSGLOCLocationOptions,
    watchId: String
): Flow<Result<List<OSGLOCLocationResult>>>
```

The method is composed of the following input parameters:
- **activity**: the `Activity` from the app using the library to use when obtaining the location updates.
- **options**: `OSGLOCLocationOptions` with the options with which to make the location updates request with (e.g. timeout).
- **watchId**: a unique id identifying the watch to add, so that it can be removed later.

The method returns a Flow in which hthe location updates will be emitted into.

### Clear a watch that was added previously

```kotlin
fun clearWatch(id: String): Boolean
```

The method is composed of the following input parameters:
- **id**: the `watchId` identigying the watch to remove.

The method returns a Boolean indicating if the watch was cleared or not (in case the watch isn't found).