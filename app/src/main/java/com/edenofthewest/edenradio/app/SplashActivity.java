package com.edenofthewest.edenradio.app;

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;

public class SplashActivity extends Activity {

    //private TextView txtLoading;
    private final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //remove title bar
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_splash);

        //txtLoading = (TextView)findViewById(R.id.txtLoading);



            new Runnable() {
                @Override
                public void run() {
                    new AppStart().execute();
                }
            }.run();


    }

    private class AppStart extends AsyncTask<Void, Void, String> {
        String errorText = "";
        final Intent intent = new Intent(SplashActivity.this, EdenService.class);
        @Override
        protected String doInBackground(Void... voids) {
            try{
                //Freezes the UI for 3 seconds so that the user can see the logo
                Thread.sleep(3000);
                if (!EdenService.isRunning()) {
                    //starts the service even if user has no internet connection.
                    //this is done so that the bound clients will get the returned info automatically if the user suddenly gets internet.
                    startService(intent);
                }


            }
            catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
                errorText = "Error has occured.";
            }
            return errorText;
        }

        @Override
        protected void onPostExecute(String result) {
            Intent i = new Intent(SplashActivity.this, MainActivity.class)
                            .putExtra("errorText", result);
                            //.putExtra("successfulConnection", successfulConnection);
            startActivity(i);
            //to prevent the user from going back to the splash screen
            finish();
        }
    }

}
