# Scanner Demo

## Run The Demo

### Dependencies

* [Realm Mobile Platform](https://realm.io/docs/realm-mobile-platform/get-started/)
* [CocoaPods](https://cocoapods.org)
* Node.js

### Steps To Run

#### 1. Setting up the Scanner iOS app

1. Navigate to the Scanner directory in Terminal and enter `pod install` to install Realm into the Scanner app.
2. Open the `Scanner.xcworkspace` file in Xcode.

#### 2. Setting up the Realm Mobile Platform

1. Download the latest copy of [Realm Mobile Platform](https://realm.io/docs/realm-mobile-platform/get-started/) from the Realm website.
2. Start the Mobile Platform by running the `start-object-server.command`. Take note of the admin access token that is displayed.
3. If your web browser doesn't open automatically, open it, and navigate to 'http://localhost:9080'.
4. Register a user account with the username `scanner@realm.io` and the password `password`.

#### 3. Running the Scanner Global Listener

1. Open `ScannerServer/index.js` and replace the `REALM_ACCESS_TOKEN` value with the access token you previously noted. 
2. Navigate to the `ScannerServer` directory in Terminal, and run `npm install` to unpackage the Global Listener.
3. Run `node .` to start the Global Listener. The Mobile Platform Terminal window should print an authorized connection.

#### 4. Running the Scanner app

1. Build and run the Scanner app from Xcode on the Mac that is currently running the Mobile Platform and the Global Listener processes.
2. Tap the camera icon to take a photo of some text.
3. The app will then start uploading the image, and will return the results when it is complete.

