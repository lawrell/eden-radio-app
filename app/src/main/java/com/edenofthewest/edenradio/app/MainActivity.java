package com.edenofthewest.edenradio.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Handler;

import java.util.ArrayList;

public class MainActivity extends FragmentActivity {

    //Controls
    private ImageButton btnPlay;
    private ImageButton btnFave;
    private Drawable btnPlayIcon;
    private Drawable btnStopIcon;
    private Drawable btnFaveIcon;
    private Drawable btnUnfaveIcon;
    private Drawable btnFaveDisabledIcon;
    private TextView txtCurrentDJ;
    private ListView lstLastPlayed;
    private TextView txtCurrentSong;
    private TextView txtListeners;
    private TextView txtPlayerCurrentSong;
    private ArrayAdapter<String> adapter;

    private static final String TAG = "MainActivity";

    private String currentSong;

    //Members related to the service

    /**
     * mServiceMessenger:
     * Used in ServiceConnection objects to send a message back to the service
     * after they have received the IBinder from the service
     */
    private Messenger mServiceMessenger = null;

    /**
     * mMessenger:
     * this is where messages from the services go to.
     * IncomingMessageHandler handles what happens after the message has been received.
     */
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    /**
     * mJsonConnection:
     * ServiceConnection interfaces have callback methods called after the client (UI) has been bound to a service.
     */
    private final ServiceConnection mJsonConnection = new ServiceConnection() {

        /**
         * onServiceConnected: called after a service has been bound.
         * @param name: The concrete component name of the service that has been connected.
         * @param service: Used to send a message back to the service this client has been bound to.
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Log.i(TAG, "mJsonConnection");
            mServiceMessenger = new Messenger(service);
            try {
                //Uses flags to indicate what exactly this message is about
                Message msg = Message.obtain(null, EdenService.MSG_REGISTER_CLIENT, EdenService.MSG_GET_JSON, 0);
                //After the message has been sent, the service will send another message back to mMessenger.
                //IncomingMessageHandler will then handle what is done after the message has been received.
                msg.replyTo = mMessenger;
                mServiceMessenger.send(msg);
            }
            catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                //The service should never disconnect abruptly, but we'll handle it anyway.
                Toast.makeText(getApplicationContext(), "Error has occured.", Toast.LENGTH_LONG).show();
            }
        };

    private boolean mJsonBound;
    private DatabaseHandler db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize controls
        btnPlay = (ImageButton)findViewById(R.id.btnPlay);
        btnFave = (ImageButton)findViewById(R.id.btnFave);
        btnPlayIcon = getResources().getDrawable(R.drawable.ic_play);
        btnStopIcon = getResources().getDrawable(R.drawable.ic_pause);
        btnFaveIcon = getResources().getDrawable(R.drawable.ic_fave);
        btnUnfaveIcon = getResources().getDrawable(R.drawable.ic_unfave);
        btnFaveDisabledIcon = getResources().getDrawable(R.drawable.ic_fave_disabled);
        txtCurrentSong = (TextView)findViewById(R.id.txtCurrentSong);
        txtListeners = (TextView)findViewById(R.id.txtListeners);
        txtCurrentDJ = (TextView)findViewById(R.id.txtCurrentDJ);
        lstLastPlayed = (ListView)findViewById(R.id.lstLastPlayed);
        txtPlayerCurrentSong = (TextView)findViewById(R.id.txtPlayerCurrentSong);

        //Initialize database
        db = new DatabaseHandler(getApplicationContext());

        //Get extras from the SplashActivity
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            //gets the error text from the last activity if something bad has happened, and shows it to the user
            String errorText = extras.getString("errorText");
            if (!errorText.isEmpty())
                Toast.makeText(this, errorText, Toast.LENGTH_LONG).show();
        }

        if (!EdenService.isOnline(getApplicationContext()))
            Toast.makeText(this, "No internet connection detected, please try again later.", Toast.LENGTH_LONG).show();

    }

    /**
     * Called every time the activity is in focus.
     * Mostly used to make sure buttons have the right icons after the user has navigated to another activity,
     * and came back here.
     */
    @Override
    protected void onResume() {
        super.onResume();
        bindJsonData();
        if (StreamService.isRunning())
            btnPlay.setImageDrawable(btnStopIcon);
        if (db.alreadyFavorited(currentSong))
            btnFave.setImageDrawable(btnUnfaveIcon);
        setBtnFaveIcon(EdenService.getCurrentSong());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Service needs to be running to auto update the persistent notification from the stream and the DJ notifications.
        if (!StreamService.isRunning() && !Settings.notificationsEnabled(getApplicationContext())) {
            stopService(new Intent(MainActivity.this, EdenService.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //Used to navigate from one activity to another, via the inflated menu.
        Intent intent;
        switch (item.getItemId())
        {
            case R.id.favorites:
                intent = new Intent(MainActivity.this, FavoritesActivity.class);
                startActivity(intent);
                break;
            case R.id.action_settings:
                intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    //Called every time the activity goes out of focus, including when the user navigates to another activity.
    @Override
    protected void onStop() {
        super.onStop();
        //UI doesn't need to get data if it's not in focus.
        if (mJsonBound)
            unbindJsonData();
    }


    /*----------Player onClick methods----------*/
    public void play (View v) {
        Intent intent = new Intent(MainActivity.this, StreamService.class);
        if (!StreamService.isRunning()) {
            if (EdenService.isOnline(getApplicationContext())) {
                startService(intent);
                btnPlay.setImageDrawable(btnStopIcon);
            }
            else
                Toast.makeText(getApplicationContext(), "Please check your internet connection.", Toast.LENGTH_LONG).show();
        }
        else {
            stopService(intent);
            btnPlay.setImageDrawable(btnPlayIcon);
        }
    }

    public void fave (View v)
    {
        try
        {
            currentSong = txtPlayerCurrentSong.getText().toString();
            if (!Settings.isAuthenticated(getApplicationContext())) {
                if (!db.alreadyFavorited(currentSong)) {
                    if (currentSong != null && !currentSong.isEmpty() && !currentSong.equals("Unavailable")) {
                        db.addAnonymousFavorite(currentSong);
                        Toast.makeText(getApplicationContext(), "Song has been added to favorites.", Toast.LENGTH_LONG).show();
                        btnFave.setImageDrawable(btnUnfaveIcon);
                    }
                    else
                        Toast.makeText(getApplicationContext(), "Song title cannot be empty.", Toast.LENGTH_LONG).show();
                }
                else {
                    try {
                        db.removeAnonymousFavorite(currentSong);
                        Toast.makeText(getApplicationContext(), "Song has been removed from favorites.", Toast.LENGTH_LONG).show();
                        btnFave.setImageDrawable(btnFaveIcon);
                    }
                    catch (Exception e) {
                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
            else {
                btnFave.setImageDrawable(btnFaveDisabledIcon);
                Toast.makeText(this, "Adding faves through the app is currently not allowed. Coming soon...", Toast.LENGTH_LONG).show();
            }

        }
        catch (Exception e)
        {
            Toast.makeText(getApplicationContext(), "Could not add song to favorites.", Toast.LENGTH_LONG).show();
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }
    /*----------End Player onClick methods----------*/

    /*-----------Service binding methods----------*/
    private void bindJsonData() {
        //Binds client to service regardless of the state of the user's internet connection.
        //The service will keep trying to fetch info and post it back here.
        try {
            Log.i(TAG, "Binding Json data to UI.");
            bindService(new Intent(MainActivity.this, EdenService.class), mJsonConnection, Context.BIND_AUTO_CREATE);
        }
        catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

    }

    private void unbindJsonData() {
        Log.i(TAG, "Unbinding Json data.");
        if (mJsonBound) {
            if (mServiceMessenger != null) {
                try {
                    Message msg = Message.obtain(null, EdenService.MSG_UNREGISTER_CLIENT, EdenService.MSG_GET_JSON, 0);
                    msg.replyTo = mMessenger;
                    mServiceMessenger.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mJsonConnection);
            mJsonBound = false;
            Log.i(TAG, "mJsonBound: " + mJsonBound);
        }
    }

    private void setJsonData(Bundle data) {
        currentSong = data.getString("currentSong");
        txtCurrentSong.setText(currentSong);
        txtPlayerCurrentSong.setText(currentSong);
        setBtnFaveIcon(currentSong);
        txtCurrentDJ.setText(data.getString("currentDJ"));
        Integer listeners = data.getInt("listeners");
        txtListeners.setText(listeners.toString());
        ArrayList<String> lastPlayed = data.getStringArrayList("lastPlayed");
        adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.listitem, lastPlayed);
        lstLastPlayed.setAdapter(adapter);
        mJsonBound = true;
    }

    private void setBtnFaveIcon(String currentSong) {
        if (Settings.isAuthenticated(getApplicationContext()))
            btnFave.setImageDrawable(btnFaveDisabledIcon);
        else if (db.alreadyFavorited(currentSong))
            btnFave.setImageDrawable(btnUnfaveIcon);
        else
            btnFave.setImageDrawable(btnFaveIcon);
    }

    //This is where the client get messages from the service
    private class IncomingMessageHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EdenService.MSG_GET_JSON:
                    Bundle data = msg.getData();
                    setJsonData(data);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }
    /*-----------End Service binding methods----------*/
}
