'use strict';

var fs = require('fs');
var wildcard = require('wildcard');
var request = require('sync-request');
var Realm = require('realm');
var mkdirp = require('mkdirp');
var VisualRecognition = require('watson-developer-cloud/visual-recognition/v3');

var local_root_dir = './serverListenerRealms';
var server_base_url = 'realm://127.0.0.1:9080';

var kUploadingStatus = "Uploading";
var kProcessingStatus = "Processing";
var kFailedStatus = "Failed";
var kClassificationResultReady = "ClassificationResultReady";
var kTextScanResultReady = "TextScanResultReady";
var kFaceDetectionResultReady = "FaceDetectionResultReady";
var kCompletedStatus = "Completed";

// THIS IS THE ACCESS TOKEN PRINTED WHEN STARTING SERVER
var REALM_ACCESS_TOKEN = "";

// The path used by the global notifier to listen for changes across all
// Realms that match.
var NOTIFIER_PATH = "/*/scanner";

var visual_recognition = new VisualRecognition({
    api_key: '',
    version_date: ''
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

var filter_callback = function(realm_name) {
    if (wildcard(NOTIFIER_PATH, realm_name)) {
        console.log('NEW REALM: ' + realm_name);
        return true;
    }
    return false;
};

var change_notification_callback = function(realm_name, realm, changes) {
    // Get the label scan object to processes
    var scans = realm.objects("LabelScan");

    var unprocessedScan = realm.objects("LabelScan").filtered("status = $0", kUploadingStatus)[0];
    if (isRealmObject(unprocessedScan)) {
        console.log("New scan received: " + realm_name);
        console.log(JSON.stringify(unprocessedScan))

        realm.write(function() {
            unprocessedScan.status = kProcessingStatus;
        });

        try {
            fs.unlinkSync("./subject.jpeg");
        } catch (err) {
            // ignore
        }

        var imageBytes = new Uint8Array(unprocessedScan.imageData);
        var imageBuffer = new Buffer(imageBytes);
        fs.writeFileSync("./subject.jpeg", imageBuffer);
        var params = {
            images_file: fs.createReadStream('./subject.jpeg')
        };

        function errorReceived(err) {
            console.log(err);
            realm.write(function() {
                unprocessedScan.status = kFailedStatus;
            });
        }

        // recognize text
        visual_recognition.recognizeText(params, function(err, res) {
            if (err) {
                errorReceived(err);
            } else {
                var result = res.images[0];
                var finalText = "";
                if (result.text && result.text.length > 0) {
                    finalText = "**Text Scan Result**\n\n";
                    finalText += result.text;
                }
                console.log("Found Text: " + finalText);
                realm.write(function() {
                    unprocessedScan.result.textScanResult = finalText;
                    unprocessedScan.status = kTextScanResultReady;
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
                    unprocessedScan.result.classificationResult = classificationResult;
                    unprocessedScan.status = kClassificationResultReady;
                });
            }
        });

        // Detect Faces
        visual_recognition.detectFaces(params, function(err, res) {
            if (err) {
                errorReceived(err);
            } else {
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
                    unprocessedScan.result.faceDetectionResult = faceDetectionResult;
                    unprocessedScan.status = kFaceDetectionResultReady;
                });
            }
        });
    }
};

if (!fs.existsSync(local_root_dir)) {
    fs.mkdirSync(local_root_dir);
}

var notifier_dir = './notifier';
mkdirp.sync(notifier_dir);

//Create the admin user
var admin_user = Realm.Sync.User.adminUser(REALM_ACCESS_TOKEN);

//Callback on Realm changes
Realm.Sync.setGlobalListener(notifier_dir, server_base_url, admin_user, filter_callback, change_notification_callback);

console.log('Listening for Realm changes across: ' + NOTIFIER_PATH);
