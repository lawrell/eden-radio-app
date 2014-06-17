package com.edenofthewest.edenradio.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.JsonReader;
import android.util.Log;
import android.widget.MediaController;
import android.widget.Toast;

import java.io.InputStreamReader;
import java.net.URL;

/**
 * Service used exclusively to get the music stream.
 */
public class StreamService extends Service implements MediaPlayer.OnPreparedListener, MediaController.MediaPlayerControl {

    private final String TAG = "StreamService";
    //Arbitrary ID for notifications
    private final int CLASS_ID = 8080;

    private final String streamLink = "http://edenofthewest.com:8080/eden.mp3";
    private MediaPlayer mp;
    private MediaController mc;
    private static boolean isRunning;
    public static boolean isRunning() { return isRunning; }
    private final Handler mHandler = new Handler();


    /*----------MediaPlayer methods----------*/
    @Override
    public void start() {

        new Thread(new Runnable() {

            @Override
            public void run() {
                Log.d(TAG, "Starting stream.");
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        mp.start();
                        showNotification();
                        isRunning = true;
                    }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                        Log.e(TAG, "Error while starting stream.");
                        String errorText = "";
                        switch (what) {
                            //what = 1
                            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                                errorText += "Error has occured: ";
                                break;
                            //what = 100
                            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                                errorText += "Server has died. ";
                                break;
                        }

                        switch (extra) {
                            //extra = -1004
                            case MediaPlayer.MEDIA_ERROR_IO:
                                errorText += "Please check your internet connection.";
                                break;
                            //extra = -110
                            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                                errorText += "Connection has timed out.";
                                break;
                        }
                        mHandler.post(new ErrorRunnable(errorText));
                        return false;
                    }
                });

            }
        }).start();

    }

    private void showNotification() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        //intent = new Intent(getApplicationContext(), StreamService.class);
        //PendingIntent play = PendingIntent.getService(this, 0, intent, 0);

        //Drawable play = getResources().getDrawable(R.drawable.ic_play);



        Notification notification = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle("Eden Radio")
                .setContentText(EdenService.getCurrentSong())
                .setSmallIcon(R.drawable.ic_stat_notify_playing)
                .setContentIntent(pi).build();
        startForeground(CLASS_ID, notification);
    }

    @Override
    public void pause() {
        mp.stop();
        mp.release();
        mp = null;
        stopForeground(true);
        isRunning = false;
        Log.d(TAG, "Stream stopped.");
    }

    @Override
    public int getDuration() {
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        return 0;
    }

    @Override
    public void seekTo(int i) {

    }

    //will not work, needs an object reference, and a service is never instanciated
    @Override
    public boolean isPlaying() {
        return false;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return false;
    }

    @Override
    public boolean canSeekBackward() {
        return false;
    }

    @Override
    public boolean canSeekForward() {
        return false;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {

    }
    /*----------End MediaPlayer methods----------*/

    /*----------Service methods----------*/
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate ()
    {
        Log.i(TAG, "onCreate");
        super.onCreate();
        try
        {
            mp = new MediaPlayer();
            mc = new MediaController(this);
            mp.setDataSource(streamLink);
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mc.setMediaPlayer(this);
            mp.prepareAsync();


            //Log.d(TAG + ".onCreate()", Boolean.toString(isRunning));
        }
        catch (Exception e)
        {
            Log.e(TAG, Log.getStackTraceString(e));
            Toast.makeText(getApplicationContext(), "Could not connect to stream.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
            try {
                start();
                isRunning = true;
                Log.i(TAG, "Connected to stream.");
            }
            catch (Exception e) {
                Log.e(TAG, "Could not connect to stream.");
                Log.e(TAG, Log.getStackTraceString(e));
                //TODO:Replace error toasts with alerts
                Toast.makeText(getApplicationContext(), "Could not connect to stream.", Toast.LENGTH_LONG);
            }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pause();
    }

    /*----------End Service methods----------*/

    //Runnable to show error messages
    private class ErrorRunnable implements Runnable {

        private String errorText;
        public ErrorRunnable(String errorText) { this.errorText = errorText; }

        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), errorText, Toast.LENGTH_LONG).show();
        }
    }

}
