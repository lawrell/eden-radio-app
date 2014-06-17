package com.edenofthewest.edenradio.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Handler;
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

import java.util.ArrayList;

public class FavoritesActivity extends FragmentActivity {

    //Log tag
    private final String TAG = "FavoritesActivity";

    //Controls
    private ImageButton btnPlay;
    private ImageButton btnFave;
    private Drawable btnPlayIcon;
    private Drawable btnStopIcon;
    private Drawable btnFaveIcon;
    private Drawable btnUnfaveIcon;
    private Drawable btnFaveDisabledIcon;
    private ListView lstFaves;
    private ArrayAdapter<String> adapter;
    private TextView txtPlayerCurrentSong;

    //Members related to the service
    private Messenger mServiceMessenger = null;
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    private final ServiceConnection mAuthFavesConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Log.i(TAG, "mJsonConnection");
            mServiceMessenger = new Messenger(service);
            try {
                Message msg = Message.obtain(null, EdenService.MSG_REGISTER_CLIENT, EdenService.MSG_GET_AUTH_FAVES, 0);
                msg.replyTo = mMessenger;
                mServiceMessenger.send(msg);
            }
            catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    private boolean mAuthFavesBound;

    private String currentSong;
    private final Handler mHandler = new Handler();
    //Database
    private DatabaseHandler db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        btnPlay = (ImageButton)findViewById(R.id.btnPlay);
        btnFave = (ImageButton)findViewById(R.id.btnFave);
        btnPlayIcon = getResources().getDrawable(R.drawable.ic_play);
        btnStopIcon = getResources().getDrawable(R.drawable.ic_pause);
        btnFaveIcon = getResources().getDrawable(R.drawable.ic_fave);
        btnUnfaveIcon = getResources().getDrawable(R.drawable.ic_unfave);
        btnFaveDisabledIcon = getResources().getDrawable(R.drawable.ic_fave_disabled);
        lstFaves = (ListView)findViewById(R.id.lstFaves);

        txtPlayerCurrentSong = (TextView)findViewById(R.id.txtPlayerCurrentSong);

        db = new DatabaseHandler(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentSong = EdenService.getCurrentSong();

        //Checks the current song every five seconds and changes the player's TextView accordingly
        txtPlayerCurrentSong.post(new Runnable(){

            @Override
            public void run() {
                txtPlayerCurrentSong.setText(EdenService.getCurrentSong());
                mHandler.postDelayed(this, 5000);
            }
        });
        if (StreamService.isRunning())
            btnPlay.setImageDrawable(btnStopIcon);
        if (db.alreadyFavorited(currentSong))
            btnFave.setImageDrawable(btnUnfaveIcon);
        adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.listitem);
        setBtnFaveIcon(currentSong);
        //getFaves is in onResume in case the user authentifies themselves in the settings and goes back to this activity
        getFaves(Settings.isAuthenticated(getApplicationContext()));
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Doesn't need to be bound if it's not in focus.
        if (mAuthFavesBound)
            unbindAuthFaves();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!StreamService.isRunning() && !Settings.notificationsEnabled(getApplicationContext())) {
            stopService(new Intent(FavoritesActivity.this, EdenService.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.favorites, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Intent intent;
        switch (item.getItemId())
        {
            case R.id.main:
                intent = new Intent(FavoritesActivity.this, MainActivity.class);
                startActivity(intent);
                break;
            case R.id.action_settings:
                intent = new Intent(FavoritesActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    /*----------Player onClick methods----------*/
    public void play (View v) {
        Intent intent = new Intent(FavoritesActivity.this, StreamService.class);
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


    /*----------Service methods----------*/
    private void setAuthFaves(Bundle data) {
        adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.listitem);
        ArrayList<String> authFaves = data.getStringArrayList("authFaves");
        adapter.addAll(authFaves);
        lstFaves.setAdapter(adapter);
        mAuthFavesBound = true;
    }

    private void setBtnFaveIcon(String currentSong) {
        if (Settings.isAuthenticated(getApplicationContext()))
            btnFave.setImageDrawable(btnFaveDisabledIcon);
        else {
            if (db.alreadyFavorited(currentSong))
                btnFave.setImageDrawable(btnUnfaveIcon);
            else
                btnFave.setImageDrawable(btnFaveIcon);
        }
    }

    private void getFaves(boolean isAuthenticated) {
        Intent intent = new Intent(FavoritesActivity.this, EdenService.class);
        if (isAuthenticated) {
            //The service doesn't auto update auth faves, so there is no need to bind the client if the user has no working connection.
            //Should the user suddenly get a connection, they would need to go to another view and return here.
            if (EdenService.isOnline(getApplicationContext()))
                bindService(intent, mAuthFavesConnection, Context.BIND_AUTO_CREATE);
            else {
                Toast.makeText(getApplicationContext(), "Cannot get authentified favorites, please check your internet connection.", Toast.LENGTH_LONG).show();
                adapter.add("You currently have no favorites.");
                lstFaves.setAdapter(adapter);
            }

        }
        else {
            if (db.alreadyFavorited(currentSong))
                btnFave.setImageDrawable(btnUnfaveIcon);
            adapter.addAll(db.getAnonymousFavorites());
            lstFaves.setAdapter(adapter);
        }
    }

    private void unbindAuthFaves() {
        if (mAuthFavesBound) {
            if (mServiceMessenger != null) {
                try {
                    Log.i(TAG, "Unbinding auth faves.");
                    Message msg = Message.obtain(null, EdenService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mServiceMessenger.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mAuthFavesConnection);
            mAuthFavesBound = false;
        }
    }

    private void setCurrentSong(String mCurrentSong) {
        txtPlayerCurrentSong.setText(mCurrentSong);
        setBtnFaveIcon(mCurrentSong);
    }
    /*----------End Service methods----------*/


    /*----------Private classes----------*/
    //This is where the client get messages from the service
    private class IncomingMessageHandler extends Handler {

        Bundle data;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EdenService.MSG_GET_AUTH_FAVES:
                    data = msg.getData();
                    setAuthFaves(data);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }
    /*----------End private classes----------*/
}
