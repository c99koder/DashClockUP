package org.c99.dashclockup;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jawbone.upplatformsdk.api.ApiManager;
import com.jawbone.upplatformsdk.api.response.OauthAccessTokenResponse;
import com.jawbone.upplatformsdk.datamodel.Data;
import com.jawbone.upplatformsdk.datamodel.Meta;
import com.jawbone.upplatformsdk.oauth.OauthUtils;
import com.jawbone.upplatformsdk.oauth.OauthWebViewActivity;
import com.jawbone.upplatformsdk.utils.UpPlatformSdkConstants;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by sam on 10/4/14.
 */
public class SettingsActivity extends PreferenceActivity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setIcon(R.drawable.ic_launcher);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        addPreferencesFromResource(R.xml.settings);

        findPreference("login").setOnPreferenceClickListener(loginClickListener);
        try {
            findPreference("version").setSummary(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    Preference.OnPreferenceClickListener loginClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            ApiManager.getRequestInterceptor().clearAccessToken();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove(UpPlatformSdkConstants.UP_PLATFORM_REFRESH_TOKEN);
            editor.remove(UpPlatformSdkConstants.UP_PLATFORM_ACCESS_TOKEN);
            editor.remove("name");
            editor.remove("steps");
            editor.remove("goal");
            editor.commit();

            sendBroadcast(new Intent(DashclockExtension.REFRESH_INTENT));

            CookieSyncManager.createInstance(SettingsActivity.this);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.setAcceptCookie(true);

            List<UpPlatformSdkConstants.UpPlatformAuthScope> authScope = new ArrayList<UpPlatformSdkConstants.UpPlatformAuthScope>();
            authScope.add(UpPlatformSdkConstants.UpPlatformAuthScope.BASIC_READ);
            authScope.add(UpPlatformSdkConstants.UpPlatformAuthScope.MOVE_READ);
            Uri.Builder builder = OauthUtils.setOauthParameters(BuildConfig.CLIENT_ID, "http://localhost/dashclockup?", authScope);

            Intent intent = new Intent(SettingsActivity.this, OauthWebViewActivity.class);
            intent.putExtra(UpPlatformSdkConstants.AUTH_URI, builder.build());
            startActivityForResult(intent, UpPlatformSdkConstants.JAWBONE_AUTHORIZE_REQUEST_CODE);
            return false;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if(PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).contains("name"))
            findPreference("login").setSummary(PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString("name",""));
        else if(!PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).contains(UpPlatformSdkConstants.UP_PLATFORM_ACCESS_TOKEN))
            findPreference("login").setSummary("Tap to login");
        else
            findPreference("login").setSummary("");

        if(getIntent() != null && getIntent().hasExtra("doLogin")) {
            setIntent(new Intent(SettingsActivity.this, SettingsActivity.class));
            loginClickListener.onPreferenceClick(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UpPlatformSdkConstants.JAWBONE_AUTHORIZE_REQUEST_CODE && resultCode == RESULT_OK) {

            String code = data.getStringExtra(UpPlatformSdkConstants.ACCESS_CODE);
            if (code != null) {
                ApiManager.getRequestInterceptor().clearAccessToken();
                ApiManager.getRestApiInterface().getAccessToken(
                        BuildConfig.CLIENT_ID,
                        BuildConfig.CLIENT_SECRET,
                        code,
                        accessTokenRequestListener);
            }
        }
    }

    private Callback getUserRequestListener = new Callback<JsonObject>() {
        @Override
        public void success(JsonObject result, Response response) {
            JsonObject data = result.get("data").getAsJsonObject();
            String name = data.get("first").getAsString();
            if(data.has("last") && data.get("last").getAsString().length() > 0)
                name += " " + data.get("last").getAsString();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("name", name);
            editor.commit();

            findPreference("login").setSummary(name);
            sendBroadcast(new Intent(DashclockExtension.REFRESH_INTENT));
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Log.e("DashClockUP", "failed to get user:" + retrofitError.getMessage());
        }
    };

    private Callback accessTokenRequestListener = new Callback<OauthAccessTokenResponse>() {
        @Override
        public void success(OauthAccessTokenResponse result, Response response) {

            if (result.access_token != null) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(UpPlatformSdkConstants.UP_PLATFORM_ACCESS_TOKEN, result.access_token);
                editor.putString(UpPlatformSdkConstants.UP_PLATFORM_REFRESH_TOKEN, result.refresh_token);
                editor.commit();

                ApiManager.getRequestInterceptor().setAccessToken(result.access_token);
                ApiManager.getRestApiInterface().getUser(UpPlatformSdkConstants.API_VERSION_STRING, getUserRequestListener);
            } else {
                Log.e("DashClockUP", "accessToken not returned by Oauth call, exiting...");
            }
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Log.e("DashClockUP", "failed to get accessToken:" + retrofitError.getMessage());
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
