package org.apache.cordova.fileTransferBackground;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class FileTransferBackground extends CordovaPlugin
{

  IFileTransferAndroidFacade _fileTransferAndroidFacade;
@Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
  try
{


  final int[] lastSetProgress = {-1};

IFileTransferEvent event = new IFileTransferEvent() {
@Override
  public void onMessageReceived(String message) {
    LogMessage("App onMessageReceived: " + message);
  }
@Override
  public void onError(String error)
  {
    LogMessage("App onError: " + error);

    lastSetProgress[0] = 0;
    callbackContext.error(error);
  }
@Override
  public void onCompleted()
  {
    LogMessage("App onCompleted");
    lastSetProgress[0] = 0;
    callbackContext.success();
  }
@Override
  public void onProgress(int progress)
  {
    if(progress > lastSetProgress[0])
    {
      LogMessage("App onProgress: " + progress);


      try
      {
        JSONObject jsonResultProgress = new JSONObject();
        jsonResultProgress.put("percentage", progress);
        JSONObject objResult = new JSONObject();
        objResult.put("progress", jsonResultProgress);
        PluginResult progressUpdate = new PluginResult(PluginResult.Status.OK, objResult);
        progressUpdate.setKeepCallback(true);
        callbackContext.sendPluginResult(progressUpdate);
      } catch (JSONException e)
      {
        e.printStackTrace();
      }
    }
    lastSetProgress[0] = progress;
  }
};

_fileTransferAndroidFacade  = new FileTransferAndroidFacade(this.cordova.getActivity().getApplicationContext(),event);
JSONObject objSettings = new JSONObject(args.get(0).toString()); // GetSettingsDummy();
_fileTransferAndroidFacade.execute("startUpload", new JSONArray().put(objSettings) , null);

/* if (action.equals("startUpload"))
 {
 startUpload(args, callbackContext);
 return true;
 }
 return false; // invalid action
 */
}


catch (Exception ex) {
  callbackContext.error(ex.getMessage());
}
return true;
}

private void LogMessage(String message)
{

  Log.d("FileTransferBG", message);
}

private void startUpload(JSONArray args, CallbackContext callbackContext) throws JSONException
{
  JSONObject transferSettings = new JSONObject(args.get(0).toString());

  JSONObject jsonResult = new JSONObject();
  jsonResult.put("filePath", transferSettings.get("filePath"));
  jsonResult.put("fileName", transferSettings.get("fileName"));
  JSONObject obj = new JSONObject();
  obj.put("completed", jsonResult);
  PluginResult completedUpdate = new PluginResult(PluginResult.Status.OK, obj);
  completedUpdate.setKeepCallback(true);
  callbackContext.sendPluginResult(completedUpdate);

  JSONObject jsonResultProgress = new JSONObject();
  jsonResultProgress.put("bytesReceived", 3344);
  jsonResultProgress.put("totalBytesToReceive", 6789);
  JSONObject objResult = new JSONObject();
  objResult.put("progress", jsonResultProgress);
  PluginResult progressUpdate = new PluginResult(PluginResult.Status.OK, objResult);
  progressUpdate.setKeepCallback(true);
  callbackContext.sendPluginResult(progressUpdate);

  callbackContext.success();
  callbackContext.error("Fehler aber keiner");
}
}

class FileTransferAndroidFacade implements IFileTransferAndroidFacade
{
  private IFileTransferServiceCommunicationFacade _serviceCommunicationFacade;
  private IFileTransferEvent _fileTransferEvent;
  public FileTransferAndroidFacade(Context context, final IFileTransferEvent fileTransferEvent)
{
  _serviceCommunicationFacade = new FileTransferServiceCommunicationFacade(context, new IFileTransferEvent() {
@Override
  public void onMessageReceived(String message) {
  fileTransferEvent.onMessageReceived(message);
}

@Override
public void onError(String error) {
  fileTransferEvent.onError(error);
}

@Override
public void onCompleted() {
  fileTransferEvent.onCompleted();
}

@Override
public void onProgress(int progress) {
  fileTransferEvent.onProgress(progress);
}
});

_fileTransferEvent = fileTransferEvent;
}

@Override
public void execute(String action, JSONArray args, Object callbackContext)
{
  try
  {
    _serviceCommunicationFacade.startService((args.get(0)).toString());
  }
  catch (Exception e)
  {
    Log.e("FileTransferBGService", "Exception at startup: " + e.getMessage());
    _fileTransferEvent.onError("Exception starting Background Service");
  }
}

@Override
public void sendMessage(String message)
{
  _serviceCommunicationFacade.sendMessage(message);
}

@Override
public void bindService() {
  _serviceCommunicationFacade.AddListener();
}

@Override
public void unbindService() {
  _serviceCommunicationFacade.RemoveListener();
}
}

class FileTransferServiceCommunicationFacade implements IFileTransferServiceCommunicationFacade
{
  private Context _context;
  private IFileTransferEvent _fileTransferEvent;
  private FileTransferServiceCommandsApi api;
  public FileTransferServiceCommunicationFacade(Context context, IFileTransferEvent fileTransferEvent)
{
  _context = context;
  _fileTransferEvent = fileTransferEvent;
}

@Override
public void startService(String data) {

  if(api != null || serviceConnection == null) return;
  Intent newIntent = new Intent(_context.getApplicationContext(), FileTransferBackgroundService.class);
  newIntent.putExtra("argument", data);
  _context.startService(newIntent);
  _context.bindService(newIntent, serviceConnection, 0);
}

@Override
public void sendMessage(String message) {
  try
  {
    if(api != null)
      api.SendJsonCommand(message);
  } catch (Exception e) {
    LogMessage("Error: " + e);
  }
}

private FileTransferPluginCommandsApi.Stub collectorListener = new FileTransferPluginCommandsApi.Stub() {
@Override
  public void MessageFromService(final String jsonMessage) throws RemoteException {
    Thread thread = new Thread() {
    @Override
      public void run() {
        _fileTransferEvent.onMessageReceived(jsonMessage);
      }
    };
    thread.start();
  }

        @Override
  public void ErrorInService(final String errorMessage) throws RemoteException {
    Thread thread = new Thread() {
    @Override
      public void run() {
        _fileTransferEvent.onError(errorMessage);
      }
    };
    thread.start();
  }

        @Override
  public void CompletedService() throws RemoteException {
    Thread thread = new Thread() {
    @Override
      public void run() {
        _fileTransferEvent.onCompleted();
      }
    };
    thread.start();
  }

        @Override
  public void ProgressFromService(final int progress) throws RemoteException {
    Thread thread = new Thread() {
    @Override
      public void run() {
        _fileTransferEvent.onProgress(progress);
      }
    };
    thread.start();
  }
};
private ServiceConnection serviceConnection = new ServiceConnection() {
@Override
  public void onServiceConnected(ComponentName name, IBinder service)
  {
    LogMessage("onServiceConnected");
    try
    {
      api = FileTransferServiceCommandsApi.Stub.asInterface(service);
      AddListener();
    }
    catch (Exception ex)
    {
      LogMessage("Error: " + ex.getMessage());
    }
  }

        @Override
  public void onServiceDisconnected(ComponentName name)
  {
    LogMessage("onServiceDisconnected");
    RemoveListener();
  }
};


@Override
public void AddListener() {
  try {
    if (api != null && collectorListener != null) {
      api.addListener(collectorListener);
    }
  }
  catch (Exception e) {
    LogMessage("Error AddListener: " + e.getMessage());
  }
}

@Override
public void RemoveListener() {
  try
  {
    if(api != null && collectorListener != null)
    {
      api.removeListener(collectorListener);
    }
  } catch (Exception e) {
    LogMessage("Error RemoveListener: " + e.getMessage());
  }
}

private void LogMessage(final String msg)
{
  Log.d("FileTransferApp", msg);
}
}

interface IFileTransferServiceCommunicationFacade
{
  void startService(String data);
  void sendMessage(String message);

  void AddListener();

  void RemoveListener();
}

interface IFileTransferAndroidFacade
{
  void execute(String action, JSONArray args, Object callbackContext);
  void sendMessage(String message);
  void bindService();
  void unbindService();
}

interface IFileTransferEvent
{
  void onMessageReceived(String message);
  void onError(String error);
  void onCompleted();
  void onProgress(int progress);
}
