package com.edenofthewest.edenradio.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler extends SQLiteOpenHelper {

    //Database info
    private static final int DATABASE_VERSION = 5;
    private static final String DATABASE_NAME = "EdenRadio";
    private final String TAG = "DatabaseHandler";
    private String methodTag;

    //AnonymousFavorites table
    private static final String TABLE_ANONYMOUSFAVORITES = "AnonymousFavorites";
        //Columns
        private static final String PK_ANONYMOUSFAVORITES = "anonymousfavoriteid";
        private static final String KEY_SONG = "song";
       // private static final String KEY_DATEADDED = "dateadded";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String methodTag = ".onCreate";
        String CREATE_ANONYMOUSFAVORITES_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_ANONYMOUSFAVORITES +
            "(" +
                PK_ANONYMOUSFAVORITES + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                KEY_SONG + " TEXT" +
            ")";
        try
        {
            db.execSQL(CREATE_ANONYMOUSFAVORITES_TABLE);
            //Log.i(TAG + methodTag, "Table AnonymousFavorites has been created.");
        }
        catch (SQLiteException e)
        {
            Log.e(TAG + methodTag, Log.getStackTraceString(e));
            return;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        methodTag = ".onUpgrade";
        try
        {
            Log.w(TAG + methodTag, "Tables will be dropped on upgrade.");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ANONYMOUSFAVORITES);
            onCreate(db);
        }
        catch (SQLiteException e)
        {
            Log.e(TAG + methodTag, Log.getStackTraceString(e));
            return;
        }
    }

    public void addAnonymousFavorite(String song) throws Exception
    {
        methodTag = ".addAnonymousFavorite";
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_SONG, song);
        getWritableDatabase().insert(TABLE_ANONYMOUSFAVORITES, null, initialValues);
        Log.i(TAG + methodTag, "Song has been added to favorites.");
    }

    public List<String> getAnonymousFavorites() throws SQLiteException
    {
        methodTag = ".getAnonymousFavorites";
        List<String> faves = new ArrayList<String>();
        String selectQuery = "SELECT * FROM " + TABLE_ANONYMOUSFAVORITES;

        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToFirst())
        {
            do
            {
                try
                {
                    faves.add(cursor.getString(cursor.getColumnIndex(KEY_SONG)));
                }
                catch (Exception e)
                {
                    Log.e(TAG + methodTag, Log.getStackTraceString(e));
                }
            } while (cursor.moveToNext());

        }

        return faves;
    }

    public Boolean alreadyFavorited(String currentSong)
    {
        return getAnonymousFavorites().contains(currentSong);
    }

    public void removeAnonymousFavorite(String song) throws Exception
    {
        SQLiteDatabase db = this.getWritableDatabase();
        if (alreadyFavorited(song))
        {
            db.delete(TABLE_ANONYMOUSFAVORITES, KEY_SONG + " = ?",
                    new String[] { song });
            db.close();
            Log.i(TAG, "Song has been deleted from favorites.");
        }
        else
            throw new Exception("This song is not in your favorites.");
    }
}
