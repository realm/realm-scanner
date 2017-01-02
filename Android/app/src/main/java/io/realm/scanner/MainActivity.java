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

package io.realm.scanner;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.realm.ObjectServerError;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.SyncConfiguration;
import io.realm.SyncCredentials;
import io.realm.SyncUser;
import io.realm.scanner.model.Scan;

public class MainActivity extends AppCompatActivity implements RealmChangeListener<Scan> {
    private static final String REALM_URL = "realm://" + BuildConfig.OBJECT_SERVER_IP + ":9080/~/scanner";
    private static final String AUTH_URL = "http://" + BuildConfig.OBJECT_SERVER_IP + ":9080/auth";
    private static final String ID = "scanner@realm.io";
    private static final String PASSWORD = "password";
    private static final String TEST_IMAGE = "test_image.jpg";
    private static final int PRIME_NUMBER_1000th = 7919;
    private static final int IMAGE_LIMIT = 2 * 1024 * 1024;

    private static final int REQUEST_SELECT_PHOTO = PRIME_NUMBER_1000th;
    private static final int REQUEST_IMAGE_CAPTURE = REQUEST_SELECT_PHOTO + 1;
    private static final int REQUEST_PERMISSION_WRITE = PRIME_NUMBER_1000th;
    private static final String ANDROID_PERMISSION_WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";

    private Realm realm;
    private Scan currentScan;
    private ImageButton takePhoto;
    private ImageView image;
    private TextView description;

    private View capturePanel;
    private View scannedPanel;
    private View progressPanel;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        capturePanel = findViewById(R.id.capture_panel);
        scannedPanel = findViewById(R.id.scanned_panel);
        progressPanel = findViewById(R.id.progress_panel);

        takePhoto = (ImageButton) findViewById(R.id.take_photo);
        image = (ImageView) findViewById(R.id.image);
        description = (TextView) findViewById(R.id.description);

        takePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (realm != null) {
                    showCommandsDialog();
                }
            }
        });

        takePhoto.setVisibility(View.GONE);
        takePhoto.setClickable(false);

        showPanel(Panel.CAPTURE);

        checkPermissionAndCopyTestAssetImage();

        final SyncCredentials syncCredentials = SyncCredentials.usernamePassword(ID, PASSWORD, false);
        SyncUser.loginAsync(syncCredentials, AUTH_URL, new SyncUser.Callback() {
            @Override
            public void onSuccess(SyncUser user) {
                final SyncConfiguration syncConfiguration = new SyncConfiguration.Builder(user, REALM_URL).build();
                Realm.setDefaultConfiguration(syncConfiguration);
                realm = Realm.getDefaultInstance();
                takePhoto.setVisibility(View.VISIBLE);
                takePhoto.setClickable(true);
            }

            @Override
            public void onError(ObjectServerError error) {
            }
        });
    }

    private void checkPermissionAndCopyTestAssetImage() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (checkSelfPermission(ANDROID_PERMISSION_WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                copyTestAssetIfNeeded();
            } else {
                requestPermissions(new String[]{ANDROID_PERMISSION_WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_WRITE);
            }
        } else {
            copyTestAssetIfNeeded();
        }
    }

    private void copyTestAssetIfNeeded() {
        String imagePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + TEST_IMAGE;
        if (new File(imagePath).exists()) {
            return;
        }

        AssetManager assetManager = getAssets();
        try {
            InputStream in = assetManager.open(TEST_IMAGE);
            OutputStream out = new FileOutputStream(imagePath);
            byte[] buffer = new byte[PRIME_NUMBER_1000th];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();

            MediaScannerConnection.scanFile(this, new String[]{imagePath}, new String[]{"image/jpeg"}, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanUpCurrentLabelScanIfNeeded();
        if (realm != null) {
            realm.close();
            realm = null;
        }
        showPanel(Panel.CAPTURE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem item = menu.getItem(0);
        item.setEnabled(false);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem item = menu.getItem(0);
        if (currentScan != null) {
            final String textScanResult = currentScan.getTextScanResult();
            final String classificationResult = currentScan.getClassificationResult();
            final String faceDetectionResult = currentScan.getFaceDetectionResult();
            if (textScanResult != null && classificationResult != null && faceDetectionResult != null) {
                item.setEnabled(true);
                return true;
            }
        }
        item.setEnabled(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            setTitle(R.string.app_name);
            cleanUpCurrentLabelScanIfNeeded();
            showPanel(Panel.CAPTURE);
            invalidateOptionsMenu();
        }
        return true;
    }

    private void cleanUpCurrentLabelScanIfNeeded() {
        if (currentScan != null) {
            currentScan.removeChangeListeners();
            realm.beginTransaction();
            currentScan.deleteFromRealm();
            realm.commitTransaction();
            currentScan = null;
        }
    }

    private void dispatchTakePicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (photoFile != null) {
                currentPhotoPath = photoFile.getAbsolutePath();
                Uri photoURI = FileProvider.getUriForFile(this, "io.realm.scanner.fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void dispatchSelectPhoto() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, REQUEST_SELECT_PHOTO);
    }

    private void showPanel(Panel panel) {
        if (panel.equals(Panel.SCANNED)) {
            capturePanel.setVisibility(View.GONE);
            scannedPanel.setVisibility(View.VISIBLE);
            progressPanel.setVisibility(View.GONE);
        } else if (panel.equals(Panel.CAPTURE)) {
            capturePanel.setVisibility(View.VISIBLE);
            scannedPanel.setVisibility(View.GONE);
            progressPanel.setVisibility(View.GONE);
        } else if (panel.equals(Panel.PROGRESS)) {
            capturePanel.setVisibility(View.GONE);
            scannedPanel.setVisibility(View.GONE);
            progressPanel.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_SELECT_PHOTO:
                if (resultCode == RESULT_OK) {
                    setTitle("Saving...");
                    final Uri imageUri = data.getData();
                    try {
                        final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                        final byte[] readBytes = new byte[PRIME_NUMBER_1000th];
                        final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                        int readLength;
                        while ((readLength = imageStream.read(readBytes)) != -1) {
                            byteBuffer.write(readBytes, 0, readLength);
                        }
                        cleanUpCurrentLabelScanIfNeeded();
                        byte[] imageData = byteBuffer.toByteArray();
                        if (imageData.length > IMAGE_LIMIT) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
                            int outWidth = options.outWidth;
                            int outHeight = options.outHeight;
                            int inSampleSize = 1;
                            while (outWidth > 1600 || outHeight > 1600) {
                                inSampleSize *= 2;
                                outWidth /= 2;
                                outHeight /= 2;
                            }
                            options = new BitmapFactory.Options();
                            options.inSampleSize = inSampleSize;
                            final Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
                            byteBuffer.reset();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteBuffer);
                            imageData = byteBuffer.toByteArray();
                        }
                        uploadImage(imageData);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        showPanel(Panel.PROGRESS);
                        setTitle("Uploading...");
                    }
                }
                break;
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK && currentPhotoPath != null) {
                    setTitle("Saving...");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(currentPhotoPath, options);
                    int outWidth = options.outWidth;
                    int outHeight = options.outHeight;
                    int inSampleSize = 1;
                    while (outWidth > 1600 || outHeight > 1600) {
                        inSampleSize *= 2;
                        outWidth /= 2;
                        outHeight /= 2;
                    }
                    options = new BitmapFactory.Options();
                    options.inSampleSize = inSampleSize;
                    Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, options);
                    final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteBuffer);
                    byte[] imageData = byteBuffer.toByteArray();
                    uploadImage(imageData);
                    showPanel(Panel.PROGRESS);
                    setTitle("Uploading...");
                }
                break;
        }
    }

    private void uploadImage(byte[] imageData) {
        realm.beginTransaction();
        currentScan = realm.createObject(Scan.class);
        currentScan.setStatus(StatusLiteral.UPLOADING);
        currentScan.setImageData(imageData);
        realm.commitTransaction();
        currentScan.addChangeListener(MainActivity.this);
    }

    private void showCommandsDialog() {
        final CharSequence[] items = {
                "Take with Camera",
                "Choose from Library"
        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                switch (i) {
                    case 0:
                        dispatchTakePicture();
                        break;
                    case 1:
                        dispatchSelectPhoto();
                        break;
                }
            }
        });
        builder.create().show();
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = new File(storageDir, imageFileName + ".jpg");
        return image;
    }

    @Override
    public void onChange(Scan scan) {
        final String status = scan.getStatus();
        if (status.equals(StatusLiteral.FAILED)) {
            setTitle("Failed to Process");
            cleanUpCurrentLabelScanIfNeeded();
            showPanel(Panel.CAPTURE);
        } else if (status.equals(StatusLiteral.CLASSIFICATION_RESULT_READY) ||
                status.equals(StatusLiteral.TEXTSCAN_RESULT_READY) ||
                status.equals(StatusLiteral.FACE_DETECTION_RESULT_READY)) {
            showPanel(Panel.SCANNED);
            final byte[] imageData = scan.getImageData();
            final Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            image.setImageBitmap(bitmap);

            final String textScanResult = scan.getTextScanResult();
            final String classificationResult = scan.getClassificationResult();
            final String faceDetectionResult = scan.getFaceDetectionResult();

            StringBuilder stringBuilder = new StringBuilder();
            boolean shouldAppendNewLine = false;
            if (textScanResult != null) {
                stringBuilder.append(textScanResult);
                shouldAppendNewLine = true;
            }
            if (classificationResult != null) {
                if (shouldAppendNewLine) {
                    stringBuilder.append("\n\n");
                }
                stringBuilder.append(classificationResult);
                shouldAppendNewLine = true;
            }
            if (faceDetectionResult != null) {
                if (shouldAppendNewLine) {
                    stringBuilder.append("\n\n");
                }
                stringBuilder.append(faceDetectionResult);
            }
            description.setText(stringBuilder.toString());
            if (textScanResult != null && classificationResult != null && faceDetectionResult != null) {
                realm.beginTransaction();
                scan.setStatus(StatusLiteral.COMPLETED);
                realm.commitTransaction();
            }
        } else {
            setTitle(status);
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_WRITE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                copyTestAssetIfNeeded();
            } else {
                Toast.makeText(this, R.string.REQUEST_PERMISSION_FAILED, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private enum Panel {
        CAPTURE, SCANNED, PROGRESS
    }

    private class StatusLiteral {
        public static final String UPLOADING = "Uploading";
        public static final String FAILED = "Failed";
        public static final String CLASSIFICATION_RESULT_READY = "ClassificationResultReady";
        public static final String TEXTSCAN_RESULT_READY = "TextScanResultReady";
        public static final String FACE_DETECTION_RESULT_READY = "FaceDetectionResultReady";
        public static final String COMPLETED = "Completed";
    }
}
