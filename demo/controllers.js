angular.module('starter.controllers', [])

  .controller('AppCtrl', function($scope, $ionicModal, $timeout) {

    // With the new view caching in Ionic, Controllers are only called
    // when they are recreated or on app start, instead of every page change.
    // To listen for when this page is active (for example, to refresh data),
    // listen for the $ionicView.enter event:
    //$scope.$on('$ionicView.enter', function(e) {
    //});
  })
.controller('UploadCtrl', function($scope, $timeout)
{
  $scope.settings = {
    "action": "startUpload",
    "fileName": "img.jpg",
    "filePath": "storage/emulated/0/img.jpg",
    "serverUrl": "http://192.168.10.171:3000/api/photo",
    "apiKey": "b1ff8545402c6ee292ed3a8ed3cec02e",
    "apiUser": "api_public",
    "apiPass": "api_public",
    "postUrlForCompleted": "http://192.168.10.171:3000/api/photo/completed",
    "postUrlForError": "http://192.168.10.171:3000/api/photo/error",
    "deleteOnCompleted": false,
    "notificationText": "Appsolute Mobility QXXX",
    "notificationDescription": "{{fileName}} - Progress {{progress}} %",
    "notificationTextCompleted": "Appsolute Mobility",
    "notificationDescriptionCompleted": "Submission upload completed",
    "showNotification": true,
    "hideNotificationWhenCompleted": false
  }

  $scope.logText = null;
  $scope.state = "Idle";
  var success = function () {
    $timeout(function()
    {
      console.log('Success');
      $scope.logText += 'Success <br>';
      $scope.state = "Success";
    },100);
  };
  var error = function (err) {
    $timeout(function() {
      console.log('Error: ' + err);
      $scope.logText += 'Error: ' + err + '<br>';
      $scope.state = "Error";
    },100);
  };
  var progress = function (progress) {
    if (!progress) return;
    $timeout(function() {
      var percent = progress.percentage;
      $scope.progress = percent;
      $scope.state = "InProgress";
    },100);
  };
  $scope.startUpload = function() {
    $scope.logText += 'Start Upload <br> ';
    $scope.state = "Upload";
    $scope.progress = 0;
    $scope.bytesReceived = 0;
    $scope.totalBytesToReceive = 0;


    var fileTransferManager = new FileTransferFacade.FileTransferManager();
    $scope.uploader = fileTransferManager.createFileUpload($scope.settings);
    var downloadPromise2 = $scope.uploader.startUpload().then(success, error, progress);

    $scope.logText += 'Upload Started<br> ';
  }
});
