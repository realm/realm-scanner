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

import UIKit
import RealmSwift

enum Status: String {
    case Uploading  = "Uploading"
    case Processing = "Processing"
    case Failed     = "Failed"
    case TextScanResultReady = "TextScanResultReady"
    case ClassificationResultReady = "ClassificationResultReady"
    case FaceDetectionResultReady = "FaceDetectionResultReady"
    case Completed  = "Completed"
}

class ViewController: UITableViewController, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

    var realm: Realm?
    var currentScan: Scan?

    @IBOutlet var resetButton: UIBarButtonItem?
    var headerView: PhotoButtonView?
    
    var image: UIImage?
    var result: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        
        headerView = Bundle.main.loadNibNamed("PhotoButtonView", owner: self, options: nil)?.first as? PhotoButtonView
        headerView?.photoButtonTapped = { self.showPhotoPrompt() }
        tableView.tableHeaderView = headerView
    }
    
    func submitImageToRealm() {
        let username = "scanner@realm.io"
        let password = "password"
        SyncUser.logIn(with: .usernamePassword(username: username, password: password, register: false), server: URL(string: "http://\(localIPAddress):9080")!, onCompletion: { user, error in
            DispatchQueue.main.async {
                guard let user = user else {
                    let alertController = UIAlertController(title: "Error", message: error?.localizedDescription, preferredStyle: .alert)
                    alertController.addAction(UIAlertAction(title: "Try Again", style: .default, handler: { (action) in
                        self.submitImageToRealm()
                    }))
                    alertController.addAction(UIAlertAction(title: "Cancel", style: .cancel, handler: nil))
                    
                    self.updateCell(nil, text: nil, loading: false, reload: true)
                    
                    self.present(alertController, animated: true)
                    return
                }
                
                // Open Realm
                let configuration = Realm.Configuration(
                    syncConfiguration: SyncConfiguration(user: user, realmURL: URL(string: "realm://\(localIPAddress):9080/~/scanner")!)
                )
                self.realm = try! Realm(configuration: configuration)
                
                // Prepare the scan object
                self.prepareToScan()
                self.currentScan?.imageData = self.image!.data()
                self.saveScan()
            }
        })
    }

    // MARK: Button Actions
    @IBAction func resetButtonTapped() {
        image = nil
        result = nil
    
        updateImage(nil)
        updateCell(nil, text: nil, loading: false)
        updateResetButton()
        
        self.tableView.reloadRows(at: [IndexPath(row: 0, section: 0)], with: .none)
    }
    
    func showPhotoPrompt() {
        let alertController = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        let cancelAction = UIAlertAction(title: "Cancel", style: .cancel)
        alertController.addAction(cancelAction)
        
        let takePhotoAction = UIAlertAction(title: "Take with Camera", style: .default) { action in
            self.presentPhotoPicker(camera: true)
        }
        alertController.addAction(takePhotoAction)
        
        let choosePhotoAction = UIAlertAction(title: "Choose from Library", style: .default) { action in
            self.presentPhotoPicker(camera: false)
        }
        alertController.addAction(choosePhotoAction)
        
        present(alertController, animated: true, completion: nil)
    }

    func presentPhotoPicker(camera: Bool) {
        if UIImagePickerController.isCameraDeviceAvailable(.rear) == false {
            image = UIImage(named: "demo-image.jpg")
            updateImage(image)
            beginImageLookup()
            
            return
        }
    
        let imagePickerController = UIImagePickerController()
        imagePickerController.sourceType = camera ? .camera : .photoLibrary
        imagePickerController.delegate = self
        present(imagePickerController, animated: true, completion: nil)
    }

    func updateResetButton() {
        resetButton?.isEnabled = (result != nil || image != nil)
    }

    // MARK: UI State Tracking
    func updateImage(_ image: UIImage?) {
        headerView?.imageView?.image = image
        headerView?.imageView?.isHidden = (image == nil)
        headerView?.photoButton?.isHidden = (image != nil)
    }

    func updateCell(_ cell: LabelResultsCellTableViewCell?, text: String?, loading: Bool = false, reload: Bool = false) {
        var cell = cell
        if cell == nil {
            cell = tableView.cellForRow(at: IndexPath(row: 0, section: 0)) as? LabelResultsCellTableViewCell
        }
    
        guard cell != nil else { return }
    
        cell?.introLabel?.isHidden = (text != nil || loading || reload )
        cell?.activityView?.isHidden = (loading == false)
        if loading {
            cell?.activityView?.startAnimating()
        }
        
        cell?.textView?.isHidden = (text == nil)
        cell?.textView?.text = text
        
        cell?.reloadButton?.isHidden = !reload
    }

    // MARK: Image Handling
    
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [String : Any]) {
        image = info[UIImagePickerControllerOriginalImage] as! UIImage?
        updateImage(image)
        beginImageLookup()
        
        dismiss(animated: true, completion: nil)
    }

    // MARK: Processing Methods
    
    func beginImageLookup() {
        updateCell(nil, text: nil, loading: true)
        updateResetButton()
        submitImageToRealm()

    }
    
    func prepareToScan() {
        if let realm = currentScan?.realm {
            try! realm.write {
                realm.delete(currentScan!)
            }
        }
        
        currentScan = Scan()
    }
    
    func saveScan() {
        guard currentScan?.realm == nil else {
            return
        }
        
        self.title = "Saving..."
        
        try! realm?.write {
            realm?.add(currentScan!)
            currentScan?.status = Status.Uploading.rawValue
        }
        
        self.title = "Uploading..."
        
        self.currentScan?.addObserver(self, forKeyPath: "status", options: .new, context: nil)
    }
    
    override func observeValue(forKeyPath keyPath: String?, of object: Any?,
                        change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        guard keyPath == "status" && change?[NSKeyValueChangeKey.newKey] != nil else {
            return
        }
        
        let currentStatus = Status(rawValue: change?[NSKeyValueChangeKey.newKey] as! String)!
        print(currentStatus)
        switch currentStatus {
        case .ClassificationResultReady, .TextScanResultReady, .FaceDetectionResultReady:
            self.result =
                    [self.currentScan?.classificationResult, self.currentScan?.faceDetectionResult, self.currentScan?.textScanResult]
                        .flatMap({$0}).joined(separator:"\n\n")
            self.tableView.reloadRows(at: [IndexPath(row: 0, section: 0)], with: .none)
            
            if (self.currentScan?.textScanResult != nil &&
                self.currentScan?.classificationResult != nil &&
                self.currentScan?.faceDetectionResult != nil) {
                self.updateResetButton()
                
                try! self.currentScan?.realm?.write {
                    self.currentScan?.status = Status.Completed.rawValue
                }
            }
        case .Processing:
            self.title = "Processing"
        case .Failed:
            self.title = "Failed to Process"
            
            try! self.currentScan?.realm?.write {
                realm?.delete(self.currentScan!)
            }
            self.currentScan = nil
        case .Completed:
            self.title = "Completed"
        default: return
        }
    }

    // MARK: Table View Delegate / Data Source
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return 1
    }
    
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "ResultsCell") as! LabelResultsCellTableViewCell
        updateCell(cell, text: result)
        cell.reloadButtonTapped = { self.beginImageLookup() }
        return cell
    }
    
    override func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        guard result != nil else {
            return 44
        }
        
        var tableWidth = tableView.frame.size.width
        tableWidth -= 50 // margin padding
        
        let constrainHeight = CGSize(width: tableWidth, height: CGFloat.greatestFiniteMagnitude)
        let font = UIFont.systemFont(ofSize: 17)
        let bounds = result?.boundingRect(with: constrainHeight, options: NSStringDrawingOptions.usesLineFragmentOrigin, attributes: [NSFontAttributeName: font], context: nil)
        
        return bounds!.size.height + 44
    }
}
