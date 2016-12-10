////////////////////////////////////////////////////////////////////////////
//
// Copyright 2016 Realm Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////

package io.realm.scanner.model;

import io.realm.RealmObject;
import io.realm.annotations.Required;

public class Scan extends RealmObject{
    @Required
    private String scanId;
    @Required
    private String status;
    private String textScanResult;
    private String classificationResult;
    private String faceDetectionResult;
    private byte[] imageData;

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTextScanResult() {
        return textScanResult;
    }

    public void setTextScanResult(String textScanResult) {
        this.textScanResult = textScanResult;
    }

    public String getClassificationResult() {
        return classificationResult;
    }

    public void setClassificationResult(String classificationResult) {
        this.classificationResult = classificationResult;
    }

    public String getFaceDetectionResult() {
        return faceDetectionResult;
    }

    public void setFaceDetectionResult(String faceDetectionResult) {
        this.faceDetectionResult = faceDetectionResult;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }
}
