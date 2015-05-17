package org.jabelpeeps.jsondisplay;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.securepreferences.SecurePreferences;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.tozny.crypto.android.AesCbcWithIntegrity;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.tozny.crypto.android.AesCbcWithIntegrity.decryptString;
import static com.tozny.crypto.android.AesCbcWithIntegrity.encrypt;
import static com.tozny.crypto.android.AesCbcWithIntegrity.generateKey;
import static com.tozny.crypto.android.AesCbcWithIntegrity.keyString;
import static com.tozny.crypto.android.AesCbcWithIntegrity.keys;

public final class GetItemsService extends Service
                                    implements com.squareup.okhttp.Callback {

    private static final boolean DEBUG = false;
    private static final String TAG = "GetItemsService";
    private static final String POSTS_URL = "http://jsonplaceholder.typicode.com/posts";
    private static final String DB_NAME = "posts_data";
    private static final int DB_VERSION = 1;

    static final String TABLE_NAME = "posts";
    private static final String COLUMN_POST_ID = "_id";
    static final String COLUMN_USER_ID = "userId";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_BODY = "body";
    static final int COLUMN_USER_ID_INDEX = 1;
    static final int COLUMN_TITLE_INDEX = 2;
    static final int COLUMN_BODY_INDEX = 3;

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TABLE_NAME
                    + "("   + COLUMN_POST_ID + " INTEGER PRIMARY KEY, "
                            + COLUMN_USER_ID + " INTEGER, "
                            + COLUMN_TITLE + " TEXT, "
                            + COLUMN_BODY + " TEXT )";

    private static final int mStartMode = START_NOT_STICKY; // indicates how to behave if the service is killed
    private static final boolean mAllowRebind = false;      // indicates whether onRebind should be used

    private final IBinder mBinder = new GetPostBinder();  // interface for clients that bind
    private static PostData postData;               // a private nested subclass of SQLiteOpenHelper
    private final OkHttpClient httpClient = new OkHttpClient();
    private static SharedPreferences prefs;
    private ServiceHandler mServiceHandler;
    private static SQLiteDatabase database;

    static boolean dbLoaded = false;
    /** holds the strings for the drop-down navigation views in the spinner. */
    private static final Set<String> USERS = new LinkedHashSet<String>( 16 );

    public GetItemsService() {}

    @Override public void onCreate() {
        // Start a thread to assist the service, with background tasks.
        HandlerThread thread =
                new HandlerThread( "GetPostServiceHandlerThread",
                                                  Process.THREAD_PRIORITY_BACKGROUND ) {
            @Override protected void onLooperPrepared() {
                // Get the HandlerThread's Looper and use it for our nested Handler class.
                mServiceHandler = new ServiceHandler( getLooper() );
            }
        };
        thread.start();

        // save a reference to the database.
        postData = new PostData();
        database = postData.getWritableDatabase();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // The service has been starting, by a call to startService()
        if ( DEBUG ) Log.i( TAG, "onStartCommand() called." );

        if ( prefs == null ) {
            if ( intent.hasExtra( "pin" ) ) {
                // open an instance of securePrefs using the pin supplied in the intent as the password.
                prefs = new SecurePreferences( getApplication()
                                                , intent.getStringExtra( "pin" )
                                                , getString( R.string.file_name_secure_prefs ) );
                intent.removeExtra( "pin" );
            }
            else throw new RuntimeException( "Attempt to start GetItemsService without valid pin." );
        }
        // prepare an Http request - including a header checking the source Etag, if one has been
        // saved from a previous download of the JsonData.
        Request request;
        if ( prefs.contains( "Etag" ) )
            request = new Request.Builder()
                                 .url( POSTS_URL )
                                 .header( "If-None-Match", prefs.getString( "Etag", "" ) )
                                 .build();
        else
            request = new Request.Builder().url( POSTS_URL ).build();

        // this call is run in a separate thread, calling onResponse() when it is finished.
        httpClient.newCall( request ).enqueue( this );

        // If the service is killed, after returning from here, it will restart in this mode.
        return mStartMode;
    }

    @Override public IBinder onBind(Intent intent) {
        if ( prefs == null )
            throw new RuntimeException( "Attempt to bind GetItemsService when service not started." );

        return mBinder;
    }

    private final Runnable timedShutdown = new Runnable() {
        @Override public void run() {
            if ( DEBUG ) Log.i( TAG, "timedShutDown has become active, stopping Service..." );
            // stop the service (rendering it unable to be restarted without the valid pin).
            stopSelf();
        }
    };

    // called when All clients have unbound with unbindService(), or have been killed.
    @Override public boolean onUnbind(Intent intent) {
        // start runnable to shutdown service in 2 seconds. (more than long enough to cover fragment
        // lifecycle events.)
        mServiceHandler.postDelayed( timedShutdown, 500 );

        return mAllowRebind;
    }

    @Override public void onDestroy() {
        // called when service is no longer used and is being destroyed
        database = null;
        postData.close();
        mServiceHandler.getLooper().quit();
    }

    private void resetUSERS() {
        USERS.clear();
        // add the String for all users at position 0 in USERS.
        USERS.add( getString( R.string.user_list_first_prompt) );
    }

    @Override public void onFailure(Request request, IOException e) {
        if ( DEBUG ) Log.i( TAG, "onFailure callback called" );
    }
    // callback used by OkHttp with the response to the http request made in onStartCommand().
    @Override public void onResponse(final Response response) {
        if ( DEBUG ) Log.i( TAG, "Response code from OkHttp = " + response.code() );

        if ( response.code() == 304 ) {
            // first the code block to use when a 'resource unmodified' response is received.
            mServiceHandler.post( new Runnable() {
                @Override
                public void run() {
                    // a quick toast to the result!
                    Toast toast = Toast.makeText( getApplication()
                            , R.string.prompt_304_returned
                            , Toast.LENGTH_SHORT );
                    toast.setGravity( Gravity.CENTER, 0, 0 );
                    toast.show();

                    // we only need to setup the USERS array, not the whole db. First a reset...
                    resetUSERS();
                    // get a temporary cursor containing just the userId column.
                    Cursor cursor = database.query( TABLE_NAME, new String[] { COLUMN_USER_ID }
                            , null, null, null, null, null );

                    // run down the column adding a line to USERS (a Set) for each unique value.
                    for ( cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext() ) {
                        USERS.add( ( getString( R.string.user_list_other_prompts) + cursor.getInt( 0 ) ).intern() );
                    }
                    dbLoaded = true;
                    cursor.close();
                }
            } );
        }
        else if( response.isSuccessful() ) {
            // This block is entered when new data is received.
            // It uses another background process to handle the json parsing.
            mServiceHandler.post( new Runnable() {
                @Override
                public void run() {
                    // another quick toast.
                    Toast toast = Toast.makeText( getApplication()
                            , R.string.prompt_new_json_data
                            , Toast.LENGTH_LONG );
                    toast.setGravity( Gravity.CENTER, 0, 0 );
                    toast.show();

                    // save a copy of the Etag for this response, if one has been sent.
                    String eTag = response.header( "Etag" );

                    if ( eTag != null ) {
                        prefs.edit().putString( "Etag", eTag ).apply();
                    }

                    if ( DEBUG ) Log.i( TAG, "Json parsing task started" );
                    try {
                        // this deletes the current contents of the SQL table only, not the db file.
                        database.delete( TABLE_NAME, null, null );

                        // save new random encryption key to securePreferences.
                        prefs.edit()
                             .putString( "db_key", keyString( generateKey() ) )
                             .apply();

                        // get json Data from Http response.
                        String jsontext = response.body().string().trim();

                        // get parser for Json data.
                        JsonParser json = new JsonFactory().createParser( jsontext );

                        // get a values object for writing to the db.
                        ContentValues values = new ContentValues();

                        // setup temporary map to use in parsing loops.
                        Map<String, Integer> fieldMap = new HashMap<String, Integer>( 4 );
                        fieldMap.put( "userId", 1 );
                        fieldMap.put( "id", 2 );
                        fieldMap.put( "title", 3 );
                        fieldMap.put( "body", 4 );

                        resetUSERS();
                        // advance parser off its initial null token...
                        json.nextToken();

                        // ...and iterate recursively over remaining json object fields, putting
                        // entries into the values object for each and adding them as a row in the
                        // database before moving on to the next object..
                        while ( json.nextValue() != JsonToken.END_ARRAY ) {

                            while ( json.nextToken() != JsonToken.END_OBJECT ) {
                                String fieldName = json.getCurrentName();
                                json.nextToken();

                                switch ( fieldMap.get( fieldName ) ) {
                                    case 1:
                                        int u = json.getIntValue();
                                        values.put( COLUMN_USER_ID, u );
                                        USERS.add( ( getString( R.string.user_list_other_prompts ) + u ).intern() );
                                        break;
                                    case 2:
                                        values.put( COLUMN_POST_ID, json.getIntValue() );
                                        break;
                                    case 3:
                                        values.put( COLUMN_TITLE,
                                                    encrypt( json.getText(),
                                                             keys( prefs.getString( "db_key", "" ) ) )
                                                            .toString() );
                                        break;
                                    case 4:
                                        values.put( COLUMN_BODY,
                                                    encrypt( json.getText(),
                                                             keys( prefs.getString( "db_key", "" ) ) )
                                                            .toString() );
                                }
                            }
                            // insert json object values into db, with the text fields in their encrypted state.
                            database.insert( TABLE_NAME, null, values );

                            // clear values for next loop.
                            values.clear();
                        }
                        dbLoaded = true;
                        // close parser and underlying File reader
                        json.close();
                        response.body().close();

                    } catch ( GeneralSecurityException | IOException e ) {
                        e.printStackTrace();
                    }
                    if ( DEBUG ) Log.i( TAG, "Json parsing task finished successfully" );
                }
            } );
        }
    }
//----------------------------------------------------------------------------------
    private final class PostData extends SQLiteOpenHelper {

        private PostData() {
            // the super constructor opens, or initialises a db with the specified params.
            // If needed, it calls the 'onCreate' method below to generate the table.
            super( getApplication(), DB_NAME, null, DB_VERSION );
        }
        // NB despite the similar method name, this is not a app lifecycle call.
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL( SQL_CREATE_ENTRIES );
        }
        // not needed for this demo App.
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }
//----------------------------------------------------------------------------------
    final class GetPostBinder extends Binder {
        // private constructor - to prevent outside instantiation.
        private GetPostBinder() {}

        /** method to populate the spinner with userId's */
        String[] getUsers() { return USERS.toArray( new String[ USERS.size() ] ); }

        /** returns cursor from the database according to args. (NB data remains encrypted) */
        Cursor querySQL(String table, String[] columns, String selection) {
            return database.query( table, columns, selection, null, null, null, null );
        }
        /** call to abort the timed shutdown that is started by onUnbind() */
        void stopShutdown() {
            mServiceHandler.removeCallbacks( timedShutdown );
        }

        /** use the saved key to decrypt strings that have been retrieved from the database.
        * (NB key is only accessible inside this service as it is saved in securePrefs.) */
        String unencrypt(String scrambled) {
            String unscrambled = "";
            try { unscrambled = decryptString(
                                    new AesCbcWithIntegrity.CipherTextIvMac( scrambled )
                                                    , keys( prefs.getString( "db_key", "" ) ) );

            } catch ( UnsupportedEncodingException | GeneralSecurityException e ) {
                e.printStackTrace();
            }
            return unscrambled;
        }
    }
//------------------------------------------------------------------------------------
    private final class ServiceHandler extends Handler {
        // Handler that receives messages from the thread
        public ServiceHandler(Looper deloop) {
            super( deloop );
        }
    }
}
