Cordova Plugin Filetransfer Background for Apache Cordova

==================================

API provides an advanced file download functionality that persists beyond app termination, runs in the background and continues even when the user closed/suspended the application. The plugin includes progress updates and primarily designed for long-term transfer operations for resources like video, music, and large images.

**Sample usage**

```javascript
var settings = {
            "action": "startUpload",
            "fileName": "dum13mb.pkg",
            "filePath": "file:///private/var/mobile/Containers/Bundle/Application/ABA72822-72DA-4DF5-8C64-260E82469B57/cordovaPluginFileTransfer.app/dum13mb.pkg",
            "serverUrl": "http://192.168.10.171:3000/api/upload",
            "apiKey": "a4gg8545402c6ee292ed3a8ed3cec04d",
            "apiUser": "user_key",
            "apiPass": "user_pass",
            "postUrlForCompleted": "http://192.168.10.171:3000/api/upload/completed",
            "postUrlForError": "http://192.168.10.171:3000/api/upload/error",
            "deleteOnCompleted": false,
            "notificationText": "Uploading File in Background",
            "notificationDescription": "{{fileName}} - Progress {{progress}} %",
            "notificationTextCompleted": "Completed",
            "notificationDescriptionCompleted": "Upload successfull",
            "showNotification": true,
            "hideNotificationWhenCompleted": false
}

var fileTransferManager = new FileTransferFacade.FileTransferManager();
var uploader = fileTransferManager.createFileUpload(settings);
var downloadPromise2 = uploader.startUpload().then(success, error, progress);

```

**Configuration**
 * action 
 command that signals the plugin to start the upload 
 * fileName
 file name of the file to upload, name can be displayed in notifications


**Supported platforms**
 * iOS 7.0 or later
 * Android
 

**Quirks**
 * Concurrent background downloads are NOT currently supported on iOS.