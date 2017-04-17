package org.apache.cordova.fileTransferBackground;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class FileTransferBackgroundService extends Service
{
  private boolean _isCanceled = false;
  private IFileTransferServiceFacade _fileTransferServiceFacade;
  private IFileTransferServiceFacade GetServiceFacade()
  {
    if(_fileTransferServiceFacade == null)
    {
      _fileTransferServiceFacade = new FileTransferServiceFacade(this);
      _isCanceled = false;
    }
    return _fileTransferServiceFacade;
  }

    @Override
  public void onCreate()
  {
    if(android.os.Debug.isDebuggerConnected())
      android.os.Debug.waitForDebugger();

    if(_isCanceled)
    {
      Log.d("FileTransferBGService", "onCreate called while service shutting down. Aborted");
      return;
    }

    Log.d("FileTransferBGService", "onCreate");

    GetServiceFacade().onCreate();
  }

    @Override
  public int onStartCommand(android.content.Intent intent, int flags, int startId)
{
  Log.d("FileTransferBGService", "onStartCommand");

  if(_isCanceled)
  {
    Log.d("FileTransferBGService", "onCreate called while service shutting down. Aborted");
    return START_NOT_STICKY;
  }

  String settings = intent.getStringExtra("argument");

  GetServiceFacade().startUpload(settings);

  return START_STICKY;
}

@Override
public void onDestroy()
{
  super.onDestroy();

  Log.d("FileTransferBGService", "onDestroy");
  if(_isCanceled)
  {
    Log.d("FileTransferBGService", "onDestroy called while service already shutting down. Aborted");
    return;
  }

  GetServiceFacade().onDestroy();
  _fileTransferServiceFacade = null;
}

@Override
public IBinder onBind(Intent intent)
{
  Log.d("FileTransferBGService", "onBind");
  if(_isCanceled)
  {
    Log.d("FileTransferBGService", "onBind called while service already shutting down. Aborted");
    return null;
  }
  return GetServiceFacade().onBind(intent);
}

}

class FileTransferServiceFacade implements IFileTransferServiceFacade
{
  private Service _service;
  private IFileTransferUploadManager _fileTransferUploadManager;
  private NotificationCompat.Builder _notificationBuilder;
  private NotificationManager _notificationMngr;
  private FileTransferSettings _currentTransferSettings;

  private boolean _isCanceled = false;
  private int notificationId = 999;
  private int _imageIconId;
  public FileTransferServiceFacade(Service service)
{
  _isCanceled = false;
  _service = service;
  _notificationMngr = (NotificationManager) _service.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);


  _imageIconId = _service.getResources().getIdentifier("filetransferbackground_icon", "drawable", _service.getPackageName());

  final int[] lastSetProgress = {-1};

_fileTransferUploadManager = new FileTransferUploadManager(new IBackgroundUploaderEvents() {
@Override
  public void onProgressUpdate(int progress)
  {
    if(_isCanceled) return;

    if(_currentTransferSettings.ShowNotification && progress > lastSetProgress[0])
    {
      String desc = _currentTransferSettings.NotificationDescription.replace("{{progress}}", progress+"").replace("{{fileName}}", _currentTransferSettings.FileName);
      String text = _currentTransferSettings.NotificationText.replace("{{progress}}", progress+"").replace("{{fileName}}", _currentTransferSettings.FileName);

      _notificationBuilder
        .setContentTitle(text)
        .setContentText(desc)
        .setProgress(100, progress, false);

      synchronized(_notificationMngr) {
      _notificationMngr.notify(notificationId, _notificationBuilder.build());
    }
    }

    lastSetProgress[0] = progress;

    SendProgressNotification(progress);
  }

            @Override
  public void onCompleted()
  {
    if(_isCanceled) return;
    DeleteFile();

    Intent notificationIntent = new Intent(GetService().getApplicationContext(), FileTransferBackground.class);
    PendingIntent contentIntent = PendingIntent.getActivity(GetService().getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_NO_CREATE);

    _notificationMngr.cancel(notificationId);
    if(!_currentTransferSettings.HideNotificationWhenCompleted)
    {
      _notificationBuilder = new NotificationCompat.Builder(GetService().getApplicationContext())
        .setContentTitle(_currentTransferSettings.NotificationTextCompleted)
        .setContentText(_currentTransferSettings.NotificationDescriptionCompleted)
        .setSmallIcon(_imageIconId)
        .setAutoCancel(true)
        .setContentIntent(contentIntent).setDeleteIntent(contentIntent).setFullScreenIntent(contentIntent, false);

      Notification notification = _notificationBuilder.build();
      notification.flags = Notification.FLAG_AUTO_CANCEL;
      synchronized(_notificationMngr) {
      _notificationMngr.notify(333, notification);
    }
    }

    SendCompletedNotification();

    CloseServiceWithDelay();
  }

            @Override
  public void onError(String errorMessage)
  {
    if(_isCanceled) return;
    SendErrorInServiceNotification(errorMessage);
    CloseServiceWithDelay();
    _isCanceled = true;
  }
});
}


private Service GetService()
{
  return _service;
}

private boolean DeleteFile()
{
  if(_isCanceled) return false;
  if(_currentTransferSettings.DeleteOnCompleted) {
    try {
      Log.d("FileTransferBGService", "Delete File " + _currentTransferSettings.FilePath);
      File uploadedFile = new File(_currentTransferSettings.FilePath);
      return uploadedFile.delete();
    } catch (Exception ex) {
      SendErrorInServiceNotification("Could not delete file after uploading..." + ex.getMessage());
      SendCompletedNotification();
    }
  }
  return false;
}

private void CloseServiceWithDelay()
{
  if(_isCanceled) return;
  Handler handler = new Handler(Looper.getMainLooper());

  handler.postDelayed(new Runnable() {
@Override
  public void run() {
    if(_service != null)
      _service.stopSelf();
  }
}, 1000 );
}

@Override
public void onCreate()
{

}

@Override
public void startUpload(final String settings)
{
  if(_isCanceled) return;

  new Thread(new Runnable() {
  public void run() {

    try
    {
      _currentTransferSettings = new FileTransferSettings(settings);
    }
    catch (Exception e)
    {
      Log.e("FileTransferBGService", "startUpload", e);
      SendErrorInServiceNotification(e.getMessage());
      CloseServiceWithDelay();
      return;
    }

    if(_isCanceled) return;
    ShowTaskNotificationForService(_service);
    _fileTransferUploadManager.startUpload(_currentTransferSettings);
  }
}).start();
}

@Override
public void onDestroy()
{
  if(_isCanceled) return;
  _isCanceled = true;
  listeners.clear();
  if(_fileTransferUploadManager != null)
    _fileTransferUploadManager.onDestroy();
  _fileTransferUploadManager= null;
  _notificationMngr = null;
  _notificationBuilder = null;
  _service = null;
  _currentTransferSettings = null;
}

@Override
public IBinder onBind(Intent intent) {
  return apiEndpoint;
}


private void ShowTaskNotificationForService(Service service) {

  if(_isCanceled) return;
  if(_currentTransferSettings.ShowNotification)
  {
    int progress = 0;
    String desc = _currentTransferSettings.NotificationDescription.replace("{{progress}}", progress+"").replace("{{fileName}}", _currentTransferSettings.FileName);
    String text = _currentTransferSettings.NotificationText.replace("{{progress}}", progress+"").replace("{{fileName}}", _currentTransferSettings.FileName);


    Intent notificationIntent = new Intent(service, FileTransferBackground.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(service.getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    _notificationBuilder = new NotificationCompat.Builder(service.getApplicationContext())
      .setContentTitle(text)
      .setContentText(desc)
      .setContentIntent(pendingIntent)
      .setCategory(Notification.CATEGORY_PROGRESS)
      .setSmallIcon(_imageIconId)
      .setDefaults(Notification.FLAG_NO_CLEAR)
      .setWhen(System.currentTimeMillis())
      .setAutoCancel(false)
      .setShowWhen(true);

    Notification notification = _notificationBuilder.build();
    notification.flags = Notification.FLAG_ONGOING_EVENT;

    service.startForeground(notificationId, notification);
  }
}

private void SendCompletedNotification()
{

  if(_isCanceled) return;
  Thread thread = new Thread() {
@Override
  public void run() {
    for (FileTransferPluginCommandsApi listener : listeners) {
      try
      {
        listener.CompletedService();
      }
      catch (RemoteException ex)
      {
        Log.d("FileTransferBGService", "Error: " + ex.getMessage());
      }
    }
  }
};
  thread.start();
}

private void SendErrorInServiceNotification(final String error) {
  if(_isCanceled) return;
  Thread thread = new Thread() {
  @Override
    public void run() {
      for (FileTransferPluginCommandsApi listener : listeners) {
        try
        {
          Log.d("FileTransferBGService", "SendErrorInServiceNotification: " + error);
          listener.ErrorInService(error);
        }
        catch (RemoteException ex)
        {
          Log.d("FileTransferBGService", "Error: " + ex.getMessage());
        }
      }
    }
  };
  thread.start();
}

private void SendProgressNotification(final int progress) {
  if(_isCanceled) return;
  Thread thread = new Thread() {
  @Override
    public void run() {
      for (FileTransferPluginCommandsApi listener : listeners) {
        try
        {
          listener.ProgressFromService(progress);
        }
        catch (RemoteException ex)
        {
          Log.d("FileTransferBGService", "Error: " + ex.getMessage());
          synchronized (listeners) {
          listeners.remove(listener);
        }
        }
      }
    }
  };
  thread.start();
}

private void SendMessageNotification(final String message) {
  Thread thread = new Thread() {
  @Override
    public void run() {
      for (FileTransferPluginCommandsApi listener : listeners) {
        try
        {
          listener.MessageFromService(message);
        }
        catch (RemoteException ex)
        {
          Log.d("FileTransferBGService", "Error: " + ex.getMessage());
        }
      }
    }
  };
  thread.start();
}

private List<FileTransferPluginCommandsApi> listeners = new ArrayList<FileTransferPluginCommandsApi>();
private FileTransferServiceCommandsApi.Stub apiEndpoint = new FileTransferServiceCommandsApi.Stub() {
@Override
  public void SendJsonCommand(String jsonMessage) throws RemoteException
  {
    Log.d("FileTransferBGService", "Service received message from activity: " + jsonMessage);
    try
    {
      JSONObject transferSettings = new JSONObject(jsonMessage);
      if (transferSettings.has("action"))
      {
        String action = transferSettings.getString("action").toString();
        if (action.equals("startUpload"))
        {
          startUpload(transferSettings.toString());
        }
        else if (action.equals("stopUpload"))
        {
          CloseServiceWithDelay();
        }
      }
    }
    catch (Exception e)
    {
      SendErrorInServiceNotification(e.getMessage());
      CloseServiceWithDelay();
    }
  }

        @Override
  public void addListener(FileTransferPluginCommandsApi listener) throws RemoteException {
    Log.d("FileTransferBGService", "addListener");
    if(_isCanceled) return;
    synchronized (listeners) {
      listeners.clear();
      listeners.add(listener);
    }
  }

        @Override
  public void removeListener(FileTransferPluginCommandsApi listener) throws RemoteException {
    Log.d("FileTransferBGService", "removeListener");
    synchronized (listeners)
    {
      listeners.clear();
      listeners.remove(listener);
    }
  }
};



class FileTransferSettings
{
  public FileTransferSettings(String jsonSettings) throws Exception {
  try
{
  JSONObject settings = new JSONObject(jsonSettings);
  Action    = settings.getString("action");
  FileName  = settings.getString("fileName");
  FilePath  = settings.getString("filePath");
  ServerUrl = settings.getString("serverUrl");
  ApiKey    = settings.getString("apiKey");
  ApiUser   = settings.getString("apiUser");
  ApiPass   = settings.getString("apiPass");
  PostUrlForCompleted = settings.getString("postUrlForCompleted");
  PostUrlForError = settings.getString("postUrlForError");
  DeleteOnCompleted = settings.getBoolean("deleteOnCompleted");
  NotificationText = settings.getString("notificationText");
  NotificationDescription = settings.getString("notificationDescription");
  NotificationTextCompleted = settings.getString("notificationTextCompleted");
  NotificationDescriptionCompleted = settings.getString("notificationDescriptionCompleted");
  ShowNotification = settings.getBoolean("showNotification");
  HideNotificationWhenCompleted = settings.getBoolean("hideNotificationWhenCompleted");

  doesFileExist(FilePath);
  isServerAvailable(ServerUrl);
  if(!PostUrlForCompleted.trim().equals(""))
  isServerAvailable(PostUrlForCompleted);
  if(!PostUrlForError.trim().equals(""))
  isServerAvailable(PostUrlForError);
}
catch (Exception e)
{
  throw e;
}
}

public boolean doesFileExist(String path) throws Exception
{
  if(!new File(path).exists())
    throw new IOException("File not found: " + path);

  return true;
}

public boolean isServerAvailable(String url) throws Exception
{
  /*
   URL urli = new URL(url);
   HttpURLConnection.setFollowRedirects(false);
   HttpURLConnection con =  (HttpURLConnection) new URL(url).openConnection();
   con.setRequestMethod("HEAD");
   boolean isAvail = (con.getResponseCode() == HttpURLConnection.HTTP_OK);

   if(!isAvail)
   throw  new Exception("The server is not available... Url: " + url);

   return isAvail;
   */
  return true;
}

String Action   = "";
String FileName = "";
String FilePath = "";
String ServerUrl = "";
String ApiKey    = "";
String ApiUser   = "";
String ApiPass   = "";
String PostUrlForCompleted = "";
String PostUrlForError = "";
boolean DeleteOnCompleted = false;
String NotificationText = "";
String NotificationDescription = "";
String NotificationTextCompleted = "";
String NotificationDescriptionCompleted = "";
boolean ShowNotification = false;
boolean HideNotificationWhenCompleted = false;


}
}

interface IFileTransferServiceFacade
{
  void onCreate();
  void onDestroy();
  IBinder onBind(Intent intent);
  void startUpload(String settings);
}

class FileTransferUploadManager implements IFileTransferUploadManager
{
  private IBackgroundUploaderEvents _fileTransferEvent;
  private Timer _timer;
  private boolean isCanceled = false;
  public FileTransferUploadManager(IBackgroundUploaderEvents fileTransferEvent)
{
  _fileTransferEvent = fileTransferEvent;
}

@Override
public void startUpload(final FileTransferServiceFacade.FileTransferSettings settings)
{
  if(isCanceled)return;
  try {
    isCanceled = false;
    final File targetFile = new File(settings.FilePath);

    if(!targetFile.exists() && targetFile.canRead())
    {
      throw new FileNotFoundException("File not found: " + targetFile.getAbsolutePath());
    }
    final String authToken = "Basic " + Base64.encode((settings.ApiUser+":"+settings.ApiPass).getBytes(), Base64.NO_WRAP);

    new Thread(new Runnable() {
      public void run() {
        uploadFile(settings.ApiKey, authToken, settings.FileName, targetFile.getAbsolutePath(), targetFile.length() , settings.ServerUrl);
      }
    }).start();
  }
  catch (Exception e)
  {
    _fileTransferEvent.onError("Error in Upload API: "+e.toString());
  }
}

@Override
public void onDestroy() {
  isCanceled = true;
  if(_timer != null) _timer.cancel();
  _timer = null;
  _fileTransferEvent = null;
}

private void uploadFile(String apiKey, String authToken, String fileName, String filePath, long fileLen, String url) {
  HttpURLConnection.setFollowRedirects(false);
  HttpURLConnection connection = null;
  String responseMsg;
  try {
    connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestMethod("POST");
    String boundary = "---------------------------AboundaryA";
    String tail = "\r\n--" + boundary + "--\r\n";
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    connection.setRequestProperty("X-API-Key", apiKey);
    connection.setRequestProperty("Authorization", authToken);
    connection.setDoOutput(true);

    long fileLength = fileLen + tail.length();
    String fileHeader = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\""+fileName+"\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n";

    long requestLength = fileHeader.length() + fileLength;
    connection.setRequestProperty("Content-Length", "" + requestLength);
    connection.setFixedLengthStreamingMode((int) requestLength);
    connection.connect();


    DataOutputStream out = new DataOutputStream(connection.getOutputStream());
    out.writeBytes(fileHeader);
    out.flush();

    // read file and write to stream in chunks
    int maxBufferSize = 4096;
    FileInputStream fileInputStream = new FileInputStream(new File(filePath));
    int bytesAvailable = fileInputStream.available();
    int bufferSize = Math.min(bytesAvailable, maxBufferSize);
    byte[] buffer = new byte[bufferSize];
    int totalBytesRead = 0;
    int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
    while (bytesRead > 0) {
      if (isCanceled) return;
      out.write(buffer, 0, bytesRead);
      out.flush();

      totalBytesRead += bytesRead;
      publishProgress(totalBytesRead, fileLen);

      bytesAvailable = fileInputStream.available();
      bufferSize = Math.min(bytesAvailable, maxBufferSize);
      bytesRead = fileInputStream.read(buffer, 0, bufferSize);
    }


    // Write closing boundary and close stream
    out.writeBytes(tail);
    out.flush();
    out.close();


    // Get server response
    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    String line = "";
    StringBuilder builder = new StringBuilder();
    while((line = reader.readLine()) != null) {
      builder.append(line);
    }

    responseMsg = builder.toString();

    List<Integer> validSuccesCodes = new ArrayList<Integer>();
    validSuccesCodes.add(200);
    validSuccesCodes.add(201);
    validSuccesCodes.add(202);
    validSuccesCodes.add(203);
    validSuccesCodes.add(204);
    validSuccesCodes.add(301);
    if(!validSuccesCodes.contains(connection.getResponseCode()))
    {
      if(_fileTransferEvent != null)
        _fileTransferEvent.onError("Error in Upload API: " + connection.getResponseMessage() + " HttpStatusCode: " + connection.getResponseCode());
    }
    else
    {
      Log.d("FileTransferBGService", "API Result: " + connection.getResponseMessage() + " HttpStatusCode: " + connection.getResponseCode() + " content: " + responseMsg);
    }
    Log.d("FileTransferBGService", "Downloaded Finished");
    if(_fileTransferEvent != null && !isCanceled)
      _fileTransferEvent.onCompleted();
  }
  catch (Exception e) {
    if (_fileTransferEvent != null) {
      try {
        if (connection != null && connection.getResponseMessage() != null) {
          _fileTransferEvent.onError("Error in Upload API: " + connection.getResponseMessage() + " HttpStatusCode: " + connection.getResponseCode());
        }
      } catch (IOException e1) {
        _fileTransferEvent.onError("Error in Upload API: " + e.toString());
      }
    }
    e.printStackTrace();
  }
finally {
    if (connection != null) connection.disconnect();
  }
}

protected void publishProgress(long downloadedBytes, long totalBytes)
{
  if(isCanceled)return;
  long progPerc = (100 * downloadedBytes / totalBytes);
  _fileTransferEvent.onProgressUpdate((int)progPerc);
}

}

interface IBackgroundUploaderEvents
{
  void onProgressUpdate(int progress);
  void onCompleted();
  void onError(String errorMessage);
}

interface IFileTransferUploadManager
{
  void startUpload(FileTransferServiceFacade.FileTransferSettings settings);

  void onDestroy();
}
