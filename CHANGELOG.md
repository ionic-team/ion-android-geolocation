# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
