package org.apache.cordova.fileTransferBackground;

import org.apache.cordova.fileTransferBackground.FileTransferPluginCommandsApi;

oneway interface FileTransferServiceCommandsApi
{
    void SendJsonCommand(String jsonMessage);

	void addListener(FileTransferPluginCommandsApi listener);
	void removeListener(FileTransferPluginCommandsApi listener);
}