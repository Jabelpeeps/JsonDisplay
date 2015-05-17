package org.jabelpeeps.jsondisplay;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CursorAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class ItemLoaderFragment extends Fragment implements LoaderManager.LoaderCallbacks {

    private ItemLoaderCallbacks mListener;
    private static GetItemsService.GetPostBinder postBinder;
    private static LoaderManager loaderManager;
    private static ItemViewActivity parentActivity;
    private Spinner spinner;
    private SpinnerAdapter spinnerAdapter;

    /** The currently selected position on the spinner */
    private static int mSpinnerPosition = 0;

    /**
     * Interface to allow this fragment to provide new Cursors to the listView when a selection
     * is made on the spinner.  It is implemented by both ItemListFragment (which hosts the listView)
     * and ItemViewActivity (which merely passes the calls along to the current ItemListFragment).
     */
    interface ItemLoaderCallbacks {
        void swapCursorForThis(Cursor cursor);
    }

    // Required empty public constructor
    public ItemLoaderFragment() {}

    @Override public void onAttach(Activity activity) {
        super.onAttach( activity );

        try {
            mListener = (ItemLoaderCallbacks) activity;
        } catch ( ClassCastException e ) {
            throw new ClassCastException( activity.toString()
                                                  + " must implement ItemLoaderCallbacks" );
        }
        parentActivity = (ItemViewActivity) getActivity();
        parentActivity.bindService( new Intent( getActivity().getApplicationContext()
                , GetItemsService.class ), myConnection, 0 );
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        // This fragment is to be retained - to prevent unneeded restarts of the CursorLoaders.
        setRetainInstance( true );
        loaderManager = getLoaderManager();
    }

    @Override public void onDetach() {
        super.onDetach();
        mListener = null;
        parentActivity.unbindService( myConnection );
        postBinder = null;
    }

    boolean isBound() { return postBinder != null; }

//------------------------------------------------------------------------------------------------
    private volatile boolean updateInProgress = false;

    private synchronized void updateListAdapter() {
        // As the update will fail unless the database in GetItemService is properly set up, this
        // method checks the flag for that, and either runs the 'startLoading' runnable directly,
        // or initiates a new thread that will sleep until the flag turns true, and then initiates
        // the same runnable - calling back to the UI thread to do so.
        if ( GetItemsService.dbLoaded ) startLoading.run();

        else if ( !updateInProgress ) {
            updateInProgress = true;

            new Thread( new Runnable() {
                @Override public void run() {
                    while ( !GetItemsService.dbLoaded ) {
                        try {
                            Thread.sleep( 100 );

                        } catch ( InterruptedException e ) { e.printStackTrace(); }
                    }
                    getActivity().runOnUiThread( startLoading );
                }
            }).start();
        }
    }

    private final Runnable startLoading = new Runnable() {
        @Override public void run ( ) {
            loaderManager.initLoader( mSpinnerPosition, null, ItemLoaderFragment.this )
                         .startLoading();
            updateInProgress = false;
        }
    };
//-------------------------------------------------------------------------------------------------

    // a private instance of the service connection callbacks for this class to use.
    private final ServiceConnection myConnection = new ServiceConnection() {

        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            // cast the IBinder to my Binder class, so that we can use its methods.
            postBinder = (GetItemsService.GetPostBinder) service;
            // stops delayed shutdown that runs to stop service when unbound for a certain time.
            postBinder.stopShutdown();
            // this initialises the cursorLoader for the initial view of the posts.
            updateListAdapter();
            // starts a thread to setup the spinner dropdown.
            setupSpinner();
        }
        // this method is only called following an unplanned disconnection from the service.
        @Override public void onServiceDisconnected(ComponentName name) {
            postBinder = null;
        }
    };

//----------------------------------------------------------spinner setup-------------------
    private volatile boolean setupActive = false;

    private synchronized void setupSpinner() {
        // a simple lock block to only start the process if it is not already running.
        if ( !setupActive ) {
            setupActive = true;
            new Thread( spinnerSetup ).start();
        }
    }

    // Runnable for use by the method above, to avoid the sleep() call blocking the UI thread.
    private final Runnable spinnerSetup = new Runnable() {
        @Override public void run() {
            // this method cannot succeed until the data has been properly downloaded and parsed,
            // so we will sleep the thread for 0.1 sec naps until it has.
            while ( !GetItemsService.dbLoaded ) {
                try {
                    Thread.sleep( 100 );
                } catch ( InterruptedException e ) {
                    e.printStackTrace();
                }
            }
            // first lets get an appropriate context, the themedContext is best, but might be null.
            Context context = parentActivity.getBaseContext();

            if ( parentActivity.getSupportActionBar() != null )
                context = parentActivity.getSupportActionBar().getThemedContext();

            // now we retrieve the spinner from the layout, and add an array adapter to provide
            // its content - which is retrieved from the data service.
            spinner = (Spinner) parentActivity.findViewById( R.id.item_list_spinner );

            spinnerAdapter = new ArrayAdapter<>( context
                                                , android.R.layout.simple_spinner_dropdown_item
                                                , postBinder.getUsers() );

            // the last stages of the setup need to run on the UI thread, so another Runnable...
            getActivity().runOnUiThread( new Runnable() {
                @Override public void run() {
                    spinner.setAdapter( spinnerAdapter );
                    spinner.setOnItemSelectedListener( spinnerListener );
                    spinner.setSelection( mSpinnerPosition );
                    setupActive = false;
                }
            } );
        }
    };
    // Having the spinner's listener as an anonymous class within the method can cause memory leaks.
    private final AdapterView.OnItemSelectedListener
                            spinnerListener = new AdapterView.OnItemSelectedListener() {
        @Override public void onItemSelected(AdapterView<?> parent, View v, int item, long id) {
            mSpinnerPosition = item;
            updateListAdapter();
        }
        @Override public void onNothingSelected(AdapterView<?> parent) {
            updateListAdapter();
        }
    };

//------------------------------------------------------------LoaderManager Callbacks--------------

    // creates the loaders when requested via a call to LoaderManage.initialise()
    @Override public Loader onCreateLoader(int id, Bundle args) {

        return new MyCursorLoader( parentActivity.getApplication(), id );
    }
    // method called when loaders find new data.
    @Override public void onLoadFinished(Loader loader, Object data) {

        if ( loader.getId() == mSpinnerPosition )
            mListener.swapCursorForThis( (Cursor) data );
    }
    // method called when loader's data is no longer available.
    @Override public void onLoaderReset(Loader loader) {

        if ( loader.getId() == mSpinnerPosition )
            mListener.swapCursorForThis( null );
    }

//----------------------------------------------------------------------------------------------
    /** A customised CursorLoader that directly queries the SQLite database, rather than needing
     * a content provider to handle the queries.  The int parameter specifies the userID of posts
     * in the returned Cursor */
    public static class MyCursorLoader extends AsyncTaskLoader<Cursor> {

        private Cursor mCursor;
        private String query = null;

        public MyCursorLoader(Context context, int user) {
            super( context );
            // the loader with userId = 0 represents the list that contains all the posts.
            // leaving the query string as null generates a Cursor containing all the rows.
            if ( user != 0 )
                query = GetItemsService.COLUMN_USER_ID + " = " + user;
        }

        /* Runs on a worker thread */
        @Override public Cursor loadInBackground() {
            return postBinder.querySQL( GetItemsService.TABLE_NAME, null, query );
        }

        /* Runs on the UI thread */
        @Override public void deliverResult(Cursor cursor) {
            if ( isReset() ) {
                // An async query came in while the loader is stopped
                if ( cursor != null ) cursor.close();
                return;
            }
            Cursor oldCursor = mCursor;
            mCursor = cursor;

            if ( isStarted() ) super.deliverResult( cursor );

            if ( oldCursor != null && oldCursor != cursor && !oldCursor.isClosed() ) {
                oldCursor.close();
            }
        }
        @Override protected void onStartLoading() {
            if ( mCursor != null ) deliverResult( mCursor );
            if ( takeContentChanged() || mCursor == null ) forceLoad();
        }

        @Override protected void onStopLoading() { cancelLoad(); }

        @Override public void onCanceled(Cursor cursor) {
            if ( cursor != null && !cursor.isClosed() ) cursor.close();
        }

        @Override protected void onReset() {
            super.onReset();
            onStopLoading();

            if ( mCursor != null && !mCursor.isClosed() ) mCursor.close();
            mCursor = null;
        }
    }
//----------------------------------------------------------------------------------------------

    /** <p>
     *  A custom CursorAdapter to provide post titles to the listView and body text to the detail
     *  views.
     *  </p><p>
     *  As the database stores these strings in an encrypted format, the adapter uses a callback
     *  on the binder interface to decrypt the strings before returning them.
     *  </p> */

    public static class PostAdapter extends CursorAdapter {

        private final LayoutInflater mInflater;

        public PostAdapter() { this( null ); }

        public PostAdapter(Cursor c) {
            super( parentActivity, c, 0 );
            mInflater = parentActivity.getLayoutInflater();
        }

        @Override public View newView(Context unused, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate( android.R.layout.simple_list_item_activated_1, parent, false );
        }

        @Override public void bindView(View view, Context context, Cursor cursor) {
            // this if/else block is responsible for the alternating backgrounds on items in the
            // scrolling listView.
            if ( cursor.getPosition() % 2 == 1 )
                view.setBackgroundColor( context.getResources()
                                                .getColor( R.color.alternate_grey ) );
            else
                view.setBackgroundColor( context.getResources()
                                                .getColor( R.color.main_grey ) );

            // background set, now we add the text.
            ( (TextView)view ).setText( getTitle( cursor ) );
        }
        private String getTitle(Cursor cursor) {
            return postBinder.unencrypt(
                    cursor.getString(
                            GetItemsService.COLUMN_TITLE_INDEX ) );
        }
        // the next three methods populate the detail views when setting up the detailFragments.
        public int getUserAt(Cursor cursor, int post) {
            cursor.moveToPosition( post );
            return cursor.getInt( GetItemsService.COLUMN_USER_ID_INDEX );
        }
        public String getTitleAt(Cursor cursor, int post) {
            cursor.moveToPosition( post );
            return getTitle( cursor );
        }
        public String getBodyAt(Cursor cursor, int post) {
            cursor.moveToPosition( post );
            return postBinder.unencrypt(
                            cursor.getString(
                                    GetItemsService.COLUMN_BODY_INDEX ) );
        }
    }
}
