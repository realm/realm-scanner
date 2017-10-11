'use strict';

var fs = require('fs');
var Realm = require('realm');
var VisualRecognition = require('watson-developer-cloud/visual-recognition/v3');

// Insert the Realm admin token
// Linux: `cat /etc/realm/admin_token.base64`
// macOS (from within zip): `cat realm-object-server/admin_token.base64`
var REALM_ADMIN_TOKEN = "INSERT_YOUR_REALM_ADMIN_TOKEN";

// API KEY for IBM Bluemix Watson Visual Recognition
// Register for an API Key: https://console.ng.bluemix.net/registration/
var BLUEMIX_API_KEY = "INSERT_YOUR_API_KEY";

// The URL to the Realm Object Server
var SERVER_URL = 'realm://127.0.0.1:9080';

// The path used by the global notifier to listen for changes across all
// Realms that match.
var NOTIFIER_PATH = ".*/scanner";

//Insert the Realm access token which came with your download of Realm Mobile Platform Professional Edition
Realm.Sync.setAccessToken('INSERT_YOUR_REALM_ACCESS_TOKEN');

/*
Common status text strings

The mobile app listens for changes to the scan.status text value to update
it UI with the current state. These values must be the same in both this file
and the mobile client code.
*/
var kUploadingStatus = "Uploading";
var kProcessingStatus = "Processing";
var kFailedStatus = "Failed";
var kClassificationResultReady = "ClassificationResultReady";
var kTextScanResultReady = "TextScanResultReady";
var kFaceDetectionResultReady = "FaceDetectionResultReady";
var kCompletedStatus = "Completed";

// Setup IBM Bluemix SDK
var visual_recognition = new VisualRecognition({
    api_key: BLUEMIX_API_KEY,
    version_date: '2016-05-20'
});

/*
Utility Functions

Various functions to check the integrity of data.
*/
function isString(x) {
    return x !== null && x !== undefined && x.constructor === String
}

function isNumber(x) {
    return x !== null && x !== undefined && x.constructor === Number
}

function isBoolean(x) {
    return x !== null && x !== undefined && x.constructor === Boolean
}

function isObject(x) {
    return x !== null && x !== undefined && x.constructor === Object
}

function isArray(x) {
    return x !== null && x !== undefined && x.constructor === Array
}

function isRealmObject(x) {
    return x !== null && x !== undefined && x.constructor === Realm.Object
}

function isRealmList(x) {
    return x !== null && x !== undefined && x.constructor === Realm.List
}

var change_notification_callback = function(change_event) {
    let realm = change_event.realm;
    let changes = change_event.changes.Scan;
    let scanIndexes = changes.insertions;
    
    console.log(changes);
    
    // Get the scan object to processes
    var scans = realm.objects("Scan");
    
    for (var i = 0; i < scanIndexes.length; i++) {
        let scanIndex = scanIndexes[i];
        // Retrieve the scan object from the Realm with index
        let scan = scans[scanIndex];
        if (isRealmObject(scan)) {
          if (scan.status == kUploadingStatus) {
            console.log("New scan received: " + change_event.path);
            console.log(JSON.stringify(scan))

            realm.write(function() {
                scan.status = kProcessingStatus;
            });

            try {
                fs.unlinkSync("./subject.jpeg");
            } catch (err) {
                // ignore
            }

            var imageBytes = new Uint8Array(scan.imageData);
            var imageBuffer = new Buffer(imageBytes);
            fs.writeFileSync("./subject.jpeg", imageBuffer);
            var params = {
                images_file: fs.createReadStream('./subject.jpeg')
            };

            function errorReceived(err) {
                console.log("Error: " + err);
                realm.write(function() {
                    scan.status = kFailedStatus;
                });
            }

            // recognize text
            visual_recognition.recognizeText(params, function(err, res) {
                if (err) {
                    errorReceived(err);
                } else {
                    console.log("Visual Result: " + res);
                    var result = res.images[0];
                    var finalText = "";
                    if (result.text && result.text.length > 0) {
                        finalText = "**Text Scan Result**\n\n";
                        finalText += result.text;
                    }
                    console.log("Found Text: " + finalText);
                    realm.write(function() {
                        scan.textScanResult = finalText;
                        scan.status = kTextScanResultReady;
                    });
                }
            });

            // classify image
            /*{
                "custom_classes": 0,
                "images": [{
                    "classifiers": [{
                        "classes": [{
                            "class": "coffee",
                            "score": 0.900249,
                            "type_hierarchy": "/products/beverages/coffee"
                        }, {
                            "class": "cup",
                            "score": 0.645656,
                            "type_hierarchy": "/products/cup"
                        }, {
                            "class": "food",
                            "score": 0.524979
                        }],
                        "classifier_id": "default",
                        "name": "default"
                    }],
                    "image": "subject.jpeg"
                }],
                "images_processed": 1
            }*/
            visual_recognition.classify(params, function(err, res) {
                if (err) {
                    errorReceived(err);
                } else {
                    console.log("Classify Result: " + res);
                    var classes = res.images[0].classifiers[0].classes;
                    console.log(JSON.stringify(classes));
                    realm.write(function() {
                        var classificationResult = "";
                        if (classes.length > 0) {
                            classificationResult += "**Classification Result**\n\n";
                        }
                        for (var i = 0; i < classes.length; i++) {
                            var imageClass = classes[i];
                            classificationResult += "Class: " + imageClass.class + "\n";
                            classificationResult += "Score: " + imageClass.score + "\n";
                            if (imageClass.type_hierarchy) {
                                classificationResult += "Type: " + imageClass.type_hierarchy + "\n";
                            }
                            classificationResult += "\n";
                        }
                        scan.classificationResult = classificationResult;
                        scan.status = kClassificationResultReady;
                    });
                }
            });

            // Detect Faces
            visual_recognition.detectFaces(params, function(err, res) {
                if (err) {
                    errorReceived(err);
                } else {
                    console.log("Faces Result: " + res);
                    console.log(JSON.stringify(res));
                    realm.write(function() {
                        var faces = res.images[0].faces;
                        var faceDetectionResult = "";
                        if (faces.length > 0) {
                            faceDetectionResult = "**Face Detection Result**\n\n";
                            faceDetectionResult += "Number of faces detected: " + faces.length + "\n";
                            for (var i = 0; i < faces.length; i++) {
                                var face = faces[i];
                                faceDetectionResult += "Gender: " + face.gender.gender + ", Age: " + face.age.min + " - " + face.age.max;
                                faceDetectionResult += "\n";
                            }
                        }
                        scan.faceDetectionResult = faceDetectionResult;
                        scan.status = kFaceDetectionResultReady;
                    });
                }
            });
          }
        }
    }
};

//Create the admin user
var admin_user = Realm.Sync.User.adminUser(REALM_ADMIN_TOKEN);

//Callback on Realm changes
Realm.Sync.addListener(SERVER_URL, admin_user, NOTIFIER_PATH, 'change', change_notification_callback);

console.log('Listening for Realm changes across: ' + NOTIFIER_PATH);
