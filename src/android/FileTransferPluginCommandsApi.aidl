package org.apache.cordova.fileTransferBackground;

oneway interface FileTransferPluginCommandsApi
{
    void MessageFromService(String jsonMessage);
    void ErrorInService(String errorMessage);
    void CompletedService();
    void ProgressFromService(int progress);
}