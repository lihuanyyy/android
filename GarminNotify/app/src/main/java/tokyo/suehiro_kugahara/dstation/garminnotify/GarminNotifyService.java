package tokyo.suehiro_kugahara.dstation.garminnotify;


import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Handler;
import android.util.Log;


import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener;
import com.garmin.android.connectiq.ConnectIQ.IQConnectType;
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener;
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.IQDevice.IQDeviceStatus;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;
import com.garmin.android.connectiq.ConnectIQ.IQApplicationInfoListener;
import com.garmin.android.connectiq.ConnectIQ.IQSendMessageListener;
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus;

public class GarminNotifyService extends NotificationListenerService {

    public static final String IQDEVICE = "IQDevice";
    public static final String MY_APP = "DE6752440F2F456AA4FC2A7FA895FCE5";

    private String TAG = "Notification";
    private MyHTTPD server;
    private static final int PORT = 8080;
    private Handler handler = new Handler();
    private ConnectIQ mConnectIQ;
    private boolean mSdkReady = false;
    private List<IQDevice> mDevices = new ArrayList<IQDevice>();
    private IQApp mMyApp;

    private class MyAppInfoListener implements IQApplicationInfoListener {
        private IQDevice mDevice;
        public MyAppInfoListener(IQDevice device) {
            mDevice = device;
        }
        public void onApplicationInfoReceived(IQApp app) {
            // This is a good thing. Now we can show our list of message options.
            mDevices.add(mDevice);
            Log.d(TAG, mDevice.getFriendlyName());
        }

        @Override
        public void onApplicationNotInstalled(String applicationId) {
            // The Comm widget is not installed on the device so we have
            // to let the user know to install it.
            Log.d(TAG, mDevice.getFriendlyName() + "Not Installed");
        }

    }
    private IQDeviceEventListener mDeviceEventListener = new IQDeviceEventListener() {

        @Override
        public void onDeviceStatusChanged(IQDevice device, IQDeviceStatus status) {

        }

    };

    private ConnectIQListener mListener = new ConnectIQListener() {

        @Override
        public void onInitializeError(IQSdkErrorStatus errStatus) {
            mSdkReady = false;
        }

        @Override
        public void onSdkReady() {
            loadDevices();
            mSdkReady = true;
        }

        @Override
        public void onSdkShutDown() {
            mSdkReady = false;
        }

    };
    public void loadDevices() {
        // Retrieve the list of known devices
        try {
            List<IQDevice> devices = mConnectIQ.getKnownDevices();

            if (devices != null) {
                //mAdapter.setDevices(devices);

                // Let's register for device status updates.  By doing so we will
                // automatically get a status update for each device so we do not
                // need to call getStatus()
                for (IQDevice device : devices) {
                    Log.d(TAG, "getApplicationInfo:" + device.getFriendlyName());
                    //mConnectIQ.registerForDeviceEvents(device, mDeviceEventListener);
                    // Let's check the status of our application on the device.
                    try {
                        mConnectIQ.getApplicationInfo(MY_APP, device, new MyAppInfoListener(device));
                    } catch (InvalidStateException e1) {
                        Log.d(TAG, e1.getLocalizedMessage());
                    } catch (ServiceUnavailableException e1) {
                        Log.d(TAG, e1.getLocalizedMessage());
                    }

                }
            }

        } catch (InvalidStateException e) {
            // This generally means you forgot to call initialize(), but since
            // we are in the callback for initialize(), this should never happen
        } catch (ServiceUnavailableException e) {
            // This will happen if for some reason your app was not able to connect
            // to the ConnectIQ service running within Garmin Connect Mobile.  This
            // could be because Garmin Connect Mobile is not installed or needs to
            // be upgraded.

        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mConnectIQ = ConnectIQ.getInstance(this, IQConnectType.WIRELESS);
        mConnectIQ.initialize(this, true, mListener);
        mMyApp = new IQApp(MY_APP);

        try {
            server = new MyHTTPD();
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onDestroy() {
        server.stop();
        try {
            mConnectIQ.unregisterAllForEvents();
            mConnectIQ.shutdown(this);
        } catch (InvalidStateException e) {
            // This is usually because the SDK was already shut down
            // so no worries.
        }
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        StatusBarNotification[] notifications = getActiveNotifications();
        //通知更新
        for (IQDevice device : mDevices) {
            sendMessageToDevice(device, String.valueOf(notifications.length));
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        StatusBarNotification[] notifications = getActiveNotifications();
        //通知削除
        for (IQDevice device : mDevices) {
            sendMessageToDevice(device, String.valueOf(notifications.length));
        }
    }
    public void sendMessageToDevice(IQDevice device, String Message) {
        Log.d(TAG, "send " + device.getFriendlyName() +": " +  Message);
        try {
            mConnectIQ.sendMessage(device, mMyApp, Message, new IQSendMessageListener() {

                @Override
                public void onMessageStatus(IQDevice device, IQApp app, IQMessageStatus status) {
                }
            });
        } catch (InvalidStateException e) {
        } catch (ServiceUnavailableException e) {
        }

    }

    private class MyHTTPD extends NanoHTTPD {
        private MyCanvas canvas = new MyCanvas(150, 150);
        public MyHTTPD() throws IOException {
            super(PORT);
        }
         @Override
        public Response serve(IHTTPSession session) {
            Method method = session.getMethod();
            String uri = session.getUri();
            if (session.getHeaders().get("remote-addr") == "127.0.0.1") {
                Map<String, String> parms = session.getParms();
                StatusBarNotification[] notifications = GarminNotifyService.this.getActiveNotifications();
                if (parms.get("id") == null) {
                    if (parms.get("message") != null) {
                        String msg = parms.get("message");
                        int width = 150;
                        int height = 150;
                        if (parms.get("width") != null) {
                            width = Integer.parseInt(parms.get("width"));
                        }
                        if (parms.get("height") != null) {
                            height = Integer.parseInt(parms.get("height"));
                        }
                        if (canvas == null) {
                            canvas = new MyCanvas(width, height);
                        } else if (width != canvas.getWidth() || height != canvas.getHeight()) {
                            canvas = new MyCanvas(width, height);
                        }
                        canvas.Clear(Color.WHITE);
                        canvas.DrawText(msg, Color.BLACK);
                        return new NanoHTTPD.Response(Response.Status.OK,
                                "image/png",
                                canvas.getPngImageStream());
                    } else {
                        String json_data = "{\"count\": \"" + notifications.length + "\"}";
                        Log.d(TAG, json_data);
                        return new NanoHTTPD.Response(Response.Status.OK, "application/json", json_data);
                    }
                } else {
                    int count = 0;
                    int id = Integer.parseInt(parms.get("id"));

                    if (id >= notifications.length) {
                        return new NanoHTTPD.Response(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "BAD REQUEST");
                    }
                    StatusBarNotification object = notifications[id];
                    Bundle extras = object.getNotification().extras;
                    CharSequence title = extras.getCharSequence("android.title");
                    CharSequence infotext = extras.getCharSequence(Notification.EXTRA_INFO_TEXT);
                    CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
                    CharSequence summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT);
                    CharSequence[] textlines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);

                    canvas.Clear(Color.WHITE);
                    if (title != null) {
                        canvas.DrawText(title.toString(), Color.BLACK);
                    }
                    if (text != null) {
                        canvas.DrawText(text.toString(), Color.BLACK);
                    }

                    if (textlines != null && textlines.length > 0) {
                        for (CharSequence line : textlines) {
                            if (line != null) {
                                canvas.DrawText(line.toString(), Color.BLACK);
                            }
                        }
                    }

                    return new NanoHTTPD.Response(Response.Status.OK,
                            "image/png",
                            canvas.getPngImageStream());
                }
            }
            else {
                return new NanoHTTPD.Response(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "SERVICE FORBIDDEN");
           }
        }

    }
}
