package com.edenofthewest.edenradio.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public abstract class Settings
{
    public static Boolean isAuthenticated(Context context)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        //returns false if preference does not exist
        return (settings.getBoolean("auth_check", false) && !getAuthName(context).isEmpty());
    }

    public static String getAuthName(Context context)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return settings.getString("auth_name", "");
    }

    //DJ Notifications not yet implemented.
    public static Boolean notificationsEnabled(Context context)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return settings.getBoolean("notifications_check", true);
    }

    public static int getNotificationsFrequency(Context context)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return settings.getInt("notifications_frequency", 5);
    }
}
