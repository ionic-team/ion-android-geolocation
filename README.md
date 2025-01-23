# ion-android-geolocation

The `ion-android-geolocation` is a library built using `Kotlin` that provides geolocation features for Android applications.

The `IONGLOCController` class provides the main features of the Library, which are:
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
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)
- [Support](#support)

## Motivation

This library is used by the Geolocation Plugin for [OutSystems' Cordova Plugin](https://github.com/ionic-team/cordova-outsystems-geolocation) and [Ionic's Capacitor Plugin](https://github.com/ionic-team/outsystems-geolocation).

## Usage

In your app-level gradle file, import the `ion-android-geolocation` library like so:

```
    dependencies {
    	implementation("io.ionic.libs:iongeolocation-android:1.0.0")
	}
```

## Methods

As mentioned before, the library offers the `IONGLOCController` class that provides the following methods to interact with:

### Obtain the current location of the device

```kotlin
suspend fun getCurrentPosition(
    activity: Activity, 
    options: IONGLOCLocationOptions
): Result<IONGLOCLocationResult>
```

The method is composed of the following input parameters:
- **activity**: the `Activity` from the app using the library to use when obtaining the location.
- **options**: `IONGLOCLocationOptions` with the options with which to make the location request with (e.g. timeout).

The method returns a `Result` containing either an object of type `IONGLOCLocationResult`, which includes the geolocation data (e.g. latitide, longitude), or an exception that should be handled by the caller app.

### Add a watch for periodic location updates

```kotlin
fun addWatch(
    activity: Activity,
    options: IONGLOCLocationOptions,
    watchId: String
): Flow<Result<List<IONGLOCLocationResult>>>
```

The method is composed of the following input parameters:
- **activity**: the `Activity` from the app using the library to use when obtaining the location updates.
- **options**: `IONGLOCLocationOptions` with the options with which to make the location updates request with (e.g. timeout).
- **watchId**: a unique id identifying the watch to add, so that it can be removed later.

The method returns a Flow in which the location updates will be emitted to.

### Clear a watch that was added previously

```kotlin
fun clearWatch(id: String): Boolean
```

The method is composed of the following input parameters:
- **id**: the `watchId` identigying the watch to remove.

The method returns a Boolean indicating if the watch was cleared or not (in case the watch isn't found).

## Troubleshooting

Common issues and solutions:

1. Location updates not received
   - Check that location permission is allowed on the device
   - Verify location services are enabled on the device
   - Ensure the necessary permissions are included in `AndroidManifest.xml`

2. Poor accuracy
   - Enable high accuracy mode
   - Ensure clear sky view
   - Wait for better GPS signal

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

`ion-android-geolocation` is available under the MIT license. See the [LICENSE](LICENSE) file for more info.

## Support

- Report issues on our [Issue Tracker](https://github.com/ionic-team/ion-android-geolocation/issues)