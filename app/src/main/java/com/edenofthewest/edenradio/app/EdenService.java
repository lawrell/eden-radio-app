package com.edenofthewest.edenradio.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class that handles UI bindings and DJ notifications.
 */
public class EdenService extends Service{

    private final String TAG = "EdenService";

    private final String url = "http://edenofthewest.com";
    private final String jsonLink = url + "/ajax.php";
    private static String currentSong;
    public static String getCurrentSong() { return currentSong; }
    private final Handler mHandler = new Handler();

    private static boolean isRunning = false;
    public static boolean isRunning() { return isRunning; }

    private List<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());

    private Timer mTimer = new Timer(); //To get data at a regular rate

    //Flags
    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_GET_JSON = 3;
    public static final int MSG_GET_AUTH_FAVES = 4;


    /*----------Service methods----------*/
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            //Will keep running until all clients have been unbound.

            isRunning = true;

            Log.i(TAG, "Connected to service.");
        }
        catch (Exception e)
        {
            Log.e(TAG, Log.getStackTraceString(e));
            Toast.makeText(getApplicationContext(), "Could not connect to server.", Toast.LENGTH_LONG).show();
            isRunning = false;
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Stopping service.");
        super.onDestroy();
        isRunning = false;
        Log.i(TAG, "Service stopped.");
    }

    @Override
    public IBinder onBind(Intent intent) {

        /**
         * Starts the task that updates the current song every 5 seconds
         * if no clients have been bound yet, or the stream is currently playing.
         */
        if (mClients.size() == 0 || StreamService.isRunning()) {
            Log.i(TAG, "Getting current song.");
            mTimer.scheduleAtFixedRate(new CurrentSongTask(), 0, 5000);
        }
        return mMessenger.getBinder();
    }
    /*----------End Service methods----------*/


    /*----------Inner classes----------*/
    // Handler of incoming messages from clients.
    private class IncomingMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            //Log.i(TAG, "handleMessage: " + msg.what + ", " + msg.arg1);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT: //1
                    mClients.add(msg.replyTo);
                    switch (msg.arg1) { //to know which client has been bound
                        case MSG_GET_JSON:
                            Log.i(TAG, "Starting JsonTask.");
                            //checks if Json data every 3 seconds
                            mTimer.scheduleAtFixedRate(new JsonTask(), 0, 5000);
                            break;
                        case MSG_GET_AUTH_FAVES:
                            //uses the timer and the current date to run AuthFavesTask once
                            Log.i(TAG, "Starting AuthFavesTask.");
                            mTimer.schedule(new AuthFavesTask(), new Date());
                            break;
                    }
                    break;
                case MSG_UNREGISTER_CLIENT: //2
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    //Sends data from http://edenofthewest.com/ajax.php
    //This is the info you see on the main page.
    private class JsonTask extends TimerTask {
        @Override
        public void run() {
            try {
                //Log.i(TAG, "Getting Json data.");
                Bundle bundle = getJsonData();
                sendMessageToUI(MSG_GET_JSON, bundle);
            }
            catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    //Gets authentified favorites. Used with a timer, but only runs once.
    private class AuthFavesTask extends TimerTask {

        @Override
        public void run() {
            Log.i(TAG, "Getting auth faves. Run once.");
            Bundle bundle = getAuthFaves(Settings.getAuthName(getApplicationContext()));
            sendMessageToUI(MSG_GET_AUTH_FAVES, bundle);
        }
    }

    //Task used with a timer to update a field (currentSong) every 5 seconds
    //so that other clients can access it with the static method getCurrentSong()
    private class CurrentSongTask extends TimerTask {
        @Override
        public void run() {
            try {
                String mCurrentSong = getCurrentSongForTask();
                //Log.i(TAG, mCurrentSong);
                Bundle bundle = new Bundle();
                bundle.putString("currentSong", mCurrentSong);
                mHandler.post(new CurrentSongRunnable(mCurrentSong));
            }
            catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    //Class to post the current song in an accessible field instead of in a bundle (like in JsonTask).
    private class CurrentSongRunnable implements Runnable {

        private String mCurrentSong;

        public CurrentSongRunnable(String mCurrentSong) { this.mCurrentSong = mCurrentSong; }

        @Override
        public void run() {
            currentSong = mCurrentSong;
        }
    }

//    private class AuthFavesRunnable implements Runnable {
//
//        ArrayList<String> mFaves;
//        public AuthFavesRunnable(ArrayList<String> mFaves) { this.mFaves = mFaves; }
//        @Override
//        public void run() {
//            faves = mFaves;
//        }
//    }
    /*----------End Inner classes----------*/

    /*----------Network methods----------*/
    //Gets the Json data from the server.
    private Bundle getJsonData()
    {
        Bundle bundle = new Bundle();
        try {
            URL page = new URL(jsonLink);
            JsonReader reader = new JsonReader(new InputStreamReader(page.openStream()));
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("dj"))
                    bundle.putString("currentDJ", reader.nextString());
                else if (name.equals("current"))
                    bundle.putString("currentSong", reader.nextString());
                else if (name.equals("listeners"))
                    bundle.putInt("listeners", reader.nextInt());
                else if (name.equals("lastplayed"))
                    bundle.putStringArrayList("lastPlayed", readLastPlayed(reader));
                else
                    reader.skipValue();

            }
            reader.endObject();
            reader.close();

//            Log.i(TAG, currentSong);
//            Log.i(TAG, currentDJ);
//            Log.i(TAG, listeners.toString());
//            Log.i(TAG, lastPlayed.toString());
        }
        catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            bundle.putString("currentSong", "Unavailable");
            bundle.putString("currentDJ", "Unavailable");
            bundle.putInt("listeners", 0);
            ArrayList<String> lastPlayed = new ArrayList<String>();
            lastPlayed.add("Unavailable");
            bundle.putStringArrayList("lastPlayed", lastPlayed);
        }
        return bundle;
    }

    private Bundle getAuthFaves(final String nick) {
        Bundle bundle = new Bundle();
        URL page;
        ArrayList<String> faves = new ArrayList<String>();
        try {
            page = new URL(url + "/app-faves-get.php?nick=" + nick);
            BufferedReader in = new BufferedReader(new InputStreamReader(page.openStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                faves.add(inputLine);
            }
            in.close();
        }
        catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        bundle.putStringArrayList("authFaves", faves);
        return bundle;
    }

    private ArrayList<String> readLastPlayed(JsonReader reader) throws IOException {
        ArrayList<String> lastPlayed = new ArrayList<String>(10);

        reader.beginArray();
        while (reader.hasNext())
            lastPlayed.add(reader.nextString());

        reader.endArray();
        return lastPlayed;
    }

    private String getCurrentSongForTask() {
        String mCurrentSong = "Unavailable";
        try {
            URL page = new URL(jsonLink);
            JsonReader reader = new JsonReader(new InputStreamReader(page.openStream()));
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("current"))
                    mCurrentSong = reader.nextString();
                else
                    reader.skipValue();

            }
            reader.endObject();
            reader.close();
        }
        catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return mCurrentSong;
    }

    //To check if the user has a working internet connection
    public static boolean isOnline(Context context) {
        ConnectivityManager cm;
        cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }
    /*----------End Network methods----------*/


    /*----------Client methods----------*/
    private void sendMessageToUI(int flag, Bundle bundle) {
        try {
            for (Messenger messenger : mClients) {
                Message msg = Message.obtain(null, flag);
                msg.setData(bundle);
                messenger.send(msg);
            }
        }
        catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }
    /*----------End Client methods----------*/
}
