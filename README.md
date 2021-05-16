# [S]ecurity App
Tested at LineageOS 16.0 (Android 9)

* Minimum version: Android 4.4
* Target version: Android 11

**Use at your own risk!** You've been warned.

## Features
* Remote wipe by SMS
* GUI: Status & Settings

## User manual
* Install and run the app
* Grant SMS permissions (first switch button)
* Add this app as Device Admin (second switch button)
* Enable remote wiping (third switch button)
* Add a secret passphrase (8-64 characters)

It's possible to enable a test-only mode! Just disable remote wiping after entering a passphrase. The status should make it clear that Test Mode is active.

To execute a remote wipe, simply send a SMS with your secret passphrase as content. Really, only your passphrase! No other words or lines.

## Yet another app
I know there are several other (open-source) apps for such a simple task. But sadly none of them fulfilled my own demands in early 2021. So here's yet another stupid app.

## Technical details
* Remote wipe will be executed with `WIPE_EXTERNAL_STORAGE`!
* Passphrase will be saved as salted SHA-512 hash in shared preferences.
* SMS counter and last timestamp gets stored at shared preferences too.

## Further development
Please be aware that I'm not planing to implement other features than remote wiping. There are great open-source solutions for general remote command apps, triggered by SMS or TCP/IP.

Anyways feel free to open an issue or submit a pull request to improve the current implementation.

# F-Droid & Co.
Please don't include this app in repositories like F-Droid, at least for the moment.

It's not ready yet. Feel free to open an issue if you think it's good enough.

# Disclaimer
This is my very first Android app! I had no previous experience at Android development.

Mainly this was a weekend project for my own needs, so don't expect a perfect solution.
