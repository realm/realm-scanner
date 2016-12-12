# Scanner Demo app

Scanner is an app that demonstrates the event handling capabilities of the Realm Mobile Platform. Combining the deep machine learning capabilities of IBM's Bluemix technology, it is able to take any photograph, and return a textual analysis of the contents in that photograph.

## Run The Demo

### Dependencies

* [Realm Mobile Platform](https://realm.io/docs/realm-mobile-platform/get-started/)
* [CocoaPods](https://cocoapods.org) (iOS Only)
* Node.js

### Steps To Run

For a fully in-depth tutorial on running the Scanner app along with the Realm Object Server, please see [the Scanner tutorial](https://realm.io/docs/realm-mobile-platform/textscanner/) on the Realm website.

#### 1. Setting up the Mobile App

##### iOS
1. Navigate to the Scanner directory in Terminal and enter `pod install` to install Realm into the Scanner app.
2. Open the `Scanner.xcworkspace` file in Xcode.

##### Android
1. In the command line, go to the `Android` directory and run `./gradlew installDebug`

#### 2. Setting up the Realm Mobile Platform

1. Download the latest copy of [Realm Mobile Platform](https://realm.io/docs/realm-mobile-platform/get-started/) from the Realm website.
2. Start the Mobile Platform by running the `start-object-server.command`. Take note of the admin access token that is displayed.
3. If your web browser doesn't open automatically, open it, and navigate to 'http://localhost:9080'.
4. Register a user account with the username `scanner@realm.io` and the password `password`.

#### 3. Running the Scanner Global Listener

1. Open `Server/index.js` and replace the `REALM_ACCESS_TOKEN` value with the access token you previously noted. 
2. Navigate to the `Server` directory in Terminal, and run `npm install` to unpackage the Global Listener.
3. Run `node .` to start the Global Listener. The Mobile Platform Terminal window should print an authorized connection.

#### 4. Running the Scanner app

1. Build and run the Scanner app from Xcode / Android Studio on the Mac that is currently running the Mobile Platform and the Global Listener processes.
2. Tap the camera icon to take a photo of some text.
3. The app will then start uploading the image, and will return the results when it is complete.

