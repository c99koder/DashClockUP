package org.c99.dashclockup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jawbone.upplatformsdk.api.ApiManager;
import com.jawbone.upplatformsdk.api.response.OauthAccessTokenResponse;
import com.jawbone.upplatformsdk.utils.UpPlatformSdkConstants;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by sam on 10/4/14.
 */
public class DashclockExtension extends DashClockExtension {
    public final static String REFRESH_INTENT = "org.c99.DashClockUP.REFRESH";
    RefreshReceiver receiver;

    class RefreshReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            onUpdateData(0);
        }
    }

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        if (receiver != null)
            try {
                unregisterReceiver(receiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
        IntentFilter intentFilter = new IntentFilter(REFRESH_INTENT);
        receiver = new RefreshReceiver();
        registerReceiver(receiver, intentFilter);

        setUpdateWhenScreenOn(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiver != null)
            try {
                unregisterReceiver(receiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    @Override
    protected void onUpdateData(int reason) {
        if(PreferenceManager.getDefaultSharedPreferences(this).contains(UpPlatformSdkConstants.UP_PLATFORM_ACCESS_TOKEN)) {
            ApiManager.getRequestInterceptor().setAccessToken(PreferenceManager.getDefaultSharedPreferences(this).getString(UpPlatformSdkConstants.UP_PLATFORM_ACCESS_TOKEN, ""));
            ApiManager.getRestApiInterface().getUser(UpPlatformSdkConstants.API_VERSION_STRING, getUserRequestListener);
            ApiManager.getRestApiInterface().getUsersGoals(UpPlatformSdkConstants.API_VERSION_STRING, getGoalRequestListener);
        } else {
            Intent i = new Intent(this, SettingsActivity.class);
            i.putExtra("doLogin", true);
            publishUpdate(new ExtensionData()
                            .visible(true)
                            .icon(R.drawable.ic_up)
                            .status("?")
                            .expandedTitle("Not logged in")
                            .expandedBody("Tap to login to Jawbone UP")
                            .clickIntent(i)
            );
        }
    }

    private void update() {
        if(PreferenceManager.getDefaultSharedPreferences(this).contains("steps") && PreferenceManager.getDefaultSharedPreferences(this).contains("goal")) {
            int steps = PreferenceManager.getDefaultSharedPreferences(this).getInt("steps", 0);
            int goal = PreferenceManager.getDefaultSharedPreferences(this).getInt("goal", 0);
            int calories = (int)PreferenceManager.getDefaultSharedPreferences(this).getFloat("calories", 0);
            String body = (int) (((float) steps / (float) goal) * 100.0) + "% of your " + NumberFormat.getInstance().format(goal) + " step goal";
            if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("showCalories", false)) {
                body += "\n" + NumberFormat.getInstance().format(Math.abs(calories)) + " Calories ";
                if(calories < 0)
                    body += "over";
                else
                    body += "to go";
            }
            Intent i = getPackageManager().getLaunchIntentForPackage("com.jawbone.up");
            if(i == null)
                i = getPackageManager().getLaunchIntentForPackage("com.jawbone.upopen");

            publishUpdate(new ExtensionData()
                            .visible(true)
                            .icon(R.drawable.ic_up)
                            .status(NumberFormat.getInstance().format(steps))
                            .expandedTitle(NumberFormat.getInstance().format(steps) + " Steps Today")
                            .expandedBody(body)
                            .clickIntent(i)
            );
        }
    }

    private Callback getGoalRequestListener = new Callback<JsonObject>() {
        @Override
        public void success(JsonObject result, Response response) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(DashclockExtension.this);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("goal", result.get("data").getAsJsonObject().get("move_steps").getAsInt());
            if(PreferenceManager.getDefaultSharedPreferences(DashclockExtension.this).getBoolean("showCalories", false)) {
                if (result.get("data").getAsJsonObject().get("remaining_for_day").getAsJsonObject().has("intake_calories_remaining")) {
                    editor.putFloat("calories", result.get("data").getAsJsonObject().get("remaining_for_day").getAsJsonObject().get("intake_calories_remaining").getAsFloat());
                } else {
                    editor.remove(UpPlatformSdkConstants.UP_PLATFORM_REFRESH_TOKEN);
                    editor.remove(UpPlatformSdkConstants.UP_PLATFORM_ACCESS_TOKEN);
                    editor.remove("name");
                    editor.remove("steps");
                    editor.remove("goal");
                    editor.remove("calories");
                }
            }
            editor.commit();
            ApiManager.getRestApiInterface().getMoveEventsList(UpPlatformSdkConstants.API_VERSION_STRING, null, getMovesRequestListener);
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Log.e("DashClockUP", "failed to get user:" + retrofitError.getMessage());
        }
    };

    private Callback getMovesRequestListener = new Callback<JsonObject>() {
        @Override
        public void success(JsonObject result, Response response) {
            JsonArray items = result.get("data").getAsJsonObject().get("items").getAsJsonArray();
            if(items != null && items.size() > 0) {
                int steps = 0;
                JsonObject item = items.get(0).getAsJsonObject();
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd");
                if(item.get("date").getAsString().equals(f.format(new Date()))) {
                    steps = item.get("details").getAsJsonObject().get("steps").getAsInt();
                }

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(DashclockExtension.this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("steps", steps);
                editor.commit();
            }
            update();
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Log.e("DashClockUP", "failed to get user:" + retrofitError.getMessage());
        }
    };

    private Callback getUserRequestListener = new Callback<JsonObject>() {
        @Override
        public void success(JsonObject result, Response response) {
            JsonObject data = result.get("data").getAsJsonObject();
            String name = data.get("first").getAsString();
            if (data.has("last") && data.get("last").getAsString().length() > 0)
                name += " " + data.get("last").getAsString();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(DashclockExtension.this);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("name", name);
            editor.commit();
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Log.e("DashClockUP", "failed to get user:" + retrofitError.getMessage());
        }
    };
}
