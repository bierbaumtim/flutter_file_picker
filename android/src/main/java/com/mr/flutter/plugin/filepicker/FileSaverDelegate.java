package com.mr.flutter.plugin.filepicker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.io.File;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class FileSaverDelegate implements PluginRegistry.ActivityResultListener {
    private static final String TAG = "FileSaverDelegate";
    private static final int REQUEST_CODE = (FilePickerPlugin.class.hashCode() + 44) & 0x0000ffff;

    private final Activity activity;
    private MethodChannel.Result pendingResult;
    private String type;
    private String fileName;
    private String[] allowedExtensions;

    public FileSaverDelegate(final Activity activity) {
        this(
            activity,
            null
        );
    }

    @VisibleForTesting
    FileSaverDelegate(final Activity activity, final MethodChannel.Result result) {
        this.activity = activity;
        this.pendingResult = result;
    }

    @Override
    public boolean onActivityResult(final int requestCode, final int resultCOde, final Intent data){
        if (type == null) {
            return false;
        }

        if (requestCode == REQUEST_CODE && resultCOde == Activity.RESULT_OK) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (data != null) {
                        if (data.getData() != null) {
                            Uri uri = data.getData();

                            final FileInfo file = FileUtils.openFileStream(FileSaverDelegate.this.activity, uri, false);
                            if (file != null){
                                finishWithSuccess(uri);
                            } else{
                                finishWithError("unknown_path", "Failed to retrieve path.");
                            }
                        }
                    }

                    finishWithError("unknown_activity", "Unknown activity error, please fill an issue.");
                }
            }).start();
            return true;

        } else if (requestCode == REQUEST_CODE) {
            finishWithError("unknown_activity", "Unknown activity error, please fill an issue.");
        }

        return false;
    }

    public void startFileExplorer(final String type, final String fileName, final String[] allowedExtensions, final MethodChannel.Result result){
        if (!this.setPendingMethodCallAndResult(result)) {
            finishWithAlreadyActiveError(result);
            return;
        }

        this.type = type;
        this.allowedExtensions = allowedExtensions;
        this.fileName = fileName;

        this.startFileExplorer();
    }

    @SuppressWarnings("deprecation")
    private void startFileExplorer() {

        // Temporary fix, remove this null-check after Flutter Engine 1.14 has landed on stable
        if (type == null) {
            return;
        }

        final Uri uri = Uri.parse(Environment.getExternalStorageDirectory().getPath() + File.separator);
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        Log.d(TAG, "Selected type " + type);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setDataAndType(uri, this.type);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        if (type.contains(",")) {
            allowedExtensions = type.split(",");
        }

        if (allowedExtensions != null) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, allowedExtensions);
        }

        if (intent.resolveActivity(this.activity.getPackageManager()) != null){
            this.activity.startActivityForResult(intent, REQUEST_CODE);
        } else {
            Log.e(TAG, "Can't find a valid activity to handle the request. Make sure you've a file explorer installed.");
            finishWithError("invalid_format_type", "Can't handle the provided file type.");
        }
    }

    @SuppressWarnings("unchecked")
    private void finishWithSuccess(Object data) {
        // Temporary fix, remove this null-check after Flutter Engine 1.14 has landed on stable
        if (this.pendingResult != null) {

            if(data != null && !(data instanceof String)) {
                data = ((Uri)data).getPath();
            }

            this.pendingResult.success(data);
            this.clearPendingResult();
        }
    }

    private void finishWithError(final String errorCode, final String errorMessage) {
        if (this.pendingResult == null) {
            return;
        }

        this.pendingResult.error(errorCode, errorMessage, null);
        this.clearPendingResult();
    }

    private boolean setPendingMethodCallAndResult(final MethodChannel.Result result) {
        if (this.pendingResult != null) {
            return false;
        }
        this.pendingResult = result;
        return true;
    }

    private static void finishWithAlreadyActiveError(final MethodChannel.Result result) {
        result.error("already_active", "File picker is already active", null);
    }

    private void clearPendingResult() {
        this.pendingResult = null;
    }
}