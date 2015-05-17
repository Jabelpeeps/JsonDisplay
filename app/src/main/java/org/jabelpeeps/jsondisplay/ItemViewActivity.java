package org.jabelpeeps.jsondisplay;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

/**
 * An activity representing a list of Items. This activity has different presentations for handset
 * and tablet-size devices.
 * <p/>
 * The activity makes heavy use of fragments. The list of items is a {@link ItemListFragment} and
 * the item details (if present) is a {@link ItemDetailFragment}.  A retained {@link ItemLoaderFragment}
 * manages some customised CursorLoader across configuration changes.
 * <p/>
 * This activity also implements the required {@link ItemListFragment.ListFragmentCallbacks} interface to listen
 * for item selections.
 */
public class ItemViewActivity extends AppCompatActivity
                              implements ItemListFragment.ListFragmentCallbacks
                                        , ItemLoaderFragment.ItemLoaderCallbacks {

    private final static String TAG_LOADER_FRAGMENT = "ItemLoaderFragment";
    private final static String TAG_LIST_FRAGMENT = "ItemListFragment";
    private final static String TAG_DETAIL_FRAGMENT = "ItemDetailFragment";

    private static final String KEY_IN_DETAIL_VIEW = "InDetailView";

    private ItemListFragment listFragment;
    private ItemLoaderFragment loaderFragment;
    private ItemDetailFragment detailFragment;
    private FragmentManager fragManager;

    /** Whether or not the activity is in two-pane mode, i.e. running on a tablet device. */
    private boolean inTwoPaneMode = false;
    /** Whether a detail view is being displayed */
    private boolean inDetailView = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_item_view );
        // store a reference to the FragmentManager - we'll be using that often.
        fragManager = getFragmentManager();

        // setup a Toolbar in place of the older-type ActionBar.
        setSupportActionBar( (Toolbar) findViewById( R.id.item_view_toolbar ) );
        ActionBar actionBar = getSupportActionBar();

        if ( actionBar != null ) {
            actionBar.setDisplayShowTitleEnabled( false );
            actionBar.setDisplayHomeAsUpEnabled( true );
        }
        // Now to fragment setup... first we need to find any existing retained fragments.
        // These will only exist if this method has been called following a configuration change.
        loaderFragment = (ItemLoaderFragment) fragManager.findFragmentByTag( TAG_LOADER_FRAGMENT );
        listFragment = (ItemListFragment) fragManager.findFragmentByTag( TAG_LIST_FRAGMENT );
        detailFragment = (ItemDetailFragment) fragManager.findFragmentByTag( TAG_DETAIL_FRAGMENT );

        // ItemLoaderFragment hosts the LoaderManager for several cursorLoaders, and the setup for
        // the spinner.  If it is non-null, then it is already being retained, otherwise we need to
        // start it now.  It does not host any views, and therefore does not need to be placed
        // in a container in the layout, and can be safely initialised now.
        if ( loaderFragment == null ) {
            loaderFragment = new ItemLoaderFragment();

            fragManager.beginTransaction()
                       .add( loaderFragment, TAG_LOADER_FRAGMENT )
                       .commit();

            fragManager.executePendingTransactions();
        }
    }
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate( savedInstanceState );

        displayListFragment();

        // check for detail container in the frameLayout (only present in tablet & landscape mode)
        // and use it to set boolean.
        inTwoPaneMode = ( findViewById( R.id.item_detail_container ) != null );

        // add a detailFragment (if needed) to appropriate container.
        if ( detailFragment != null ) {
            new Thread( waitForBinding ).start();
        }
    }
    private final Runnable waitForBinding = new Runnable() {
        @Override public void run() {
            while ( !loaderFragment.isBound() ) {
                try {
                    Thread.sleep( 100 );

                } catch ( InterruptedException e ) { e.printStackTrace(); }
            }
            runOnUiThread( new Runnable() {
                @Override public void run() {
                    // as the old detail fragment may have been placed in the wrong container for
                    // this configuration, we'll just get it's item position, and simulate a user
                    // click on the list to produce a new detailFragment.
                    int detail = detailFragment.getPos();
                    fragManager.beginTransaction().remove( detailFragment ).commit();
                    fragManager.executePendingTransactions();
                    listFragment.onListItemClick( null, null, detail, 0 );
                }
            });
        }
    };

    /** <p>
     * Creates a ListFragment if needed, displaying it.
     * </p><p>
     * Otherwise this method will redisplay the existing this fragment, using the appropriate
     * transaction depending on the displayMode.
     * </p>*/
    private void displayListFragment() {
    // make a new listFragment if we don't have one, adding it to its container and the FragmentManager.
        if ( listFragment == null ) {
            listFragment = new ItemListFragment();
            fragManager.beginTransaction()
                       .add( R.id.item_list_container, listFragment, TAG_LIST_FRAGMENT )
                       .commit();
            fragManager.executePendingTransactions();
        } else {
            // as we do seem to have an existing listFragment, lets see it!
            fragManager.beginTransaction().show( listFragment ).commit();

            if ( !inTwoPaneMode )
                fragManager.beginTransaction()
                            .replace( R.id.item_list_container, listFragment, TAG_LIST_FRAGMENT )
                            .commit();

            fragManager.executePendingTransactions();
        }
    }

    /** Displays the detailFragment in the appropriate container depending on the current
     *  display mode (one or two panes). */
    private void displayDetailFragment() {

        if ( inTwoPaneMode )
            fragManager.beginTransaction()
                       .add( R.id.item_detail_container, detailFragment, TAG_DETAIL_FRAGMENT )
                       .commit();
        else {
            // lets just hide the listFragment so we don't trigger too many lifecycle events.
            fragManager.beginTransaction().hide( listFragment ).commit();
            fragManager.executePendingTransactions();

            // lets hide the spinner too.
            hideSpinnerForDetailView( true );

            // now we can put the detailFragment on top of the listFragment. (temporarily)
            fragManager.beginTransaction()
                       .add( R.id.item_list_container, detailFragment, TAG_DETAIL_FRAGMENT )
                       .commit();
        }
    }

    private void hideSpinnerForDetailView(boolean hidden) {
        inDetailView = hidden;
        findViewById( R.id.item_list_spinner ).setVisibility( hidden ? View.INVISIBLE
                                                                     : View.VISIBLE );
    }

    /** Callback from ListFragmentCallbacks indicating the item with the given ID was selected. */
    @Override public void onItemDetailRequested(int id) {

        // first we need a reference to the adapter, which we use to get the body text for the item.
        // (the getBodyAt() method calls the un-encrypt method on the GetItemService.)
        ItemLoaderFragment.PostAdapter adapter = (ItemLoaderFragment.PostAdapter) listFragment.getListAdapter();
        String body = adapter.getBodyAt( adapter.getCursor(), id );
        // and now the sme for the title.
        String title = adapter.getTitleAt( adapter.getCursor(), id );
        // and finally the user ID.
        int user = adapter.getUserAt( adapter.getCursor(), id );

        // setup a detailFragment, adding the required Strings for display to the args Bundle.
        Bundle arguments = new Bundle();
        arguments.putInt( ItemDetailFragment.ARG_POS, id );
        arguments.putString( ItemDetailFragment.ARG_USER, ( "Post by userID:- " + user ).intern() );
        arguments.putString( ItemDetailFragment.ARG_TITLE, title );
        arguments.putString( ItemDetailFragment.ARG_BODY, body );

        detailFragment = new ItemDetailFragment();
        detailFragment.setArguments( arguments );

        // display the detailFragment in the appropriate container.
        displayDetailFragment();
    }

    /** Callback from ListFragmentCallbacks indicating the item indicating the number of panes. */
    @Override public boolean isInTwoPanes() { return inTwoPaneMode; }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate( R.menu.menu_item_view, menu );
        hideSpinnerForDetailView( !inTwoPaneMode && inDetailView );
        return super.onCreateOptionsMenu( menu );
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch ( item.getItemId() ) {

            case R.id.action_refresh_posts:
                // a call to startService initiates a new Http request.
                startService( new Intent( getApplication(), GetItemsService.class ) );
                return true;
            case android.R.id.home:
                // in this app, the home and back buttons do the same thing.
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected( item );
        }
    }
    @Override public void onBackPressed() {
        if ( inDetailView ) {
            fragManager.beginTransaction().remove( detailFragment ).commit();
            fragManager.executePendingTransactions();
            detailFragment = null;
            // lets display the spinner again (false arg for hide = see)
            hideSpinnerForDetailView( false );
            // and now the listFragment.
            displayListFragment();
            return;
        }
        // if not in DetailView, return to the pin entry activity - if confirmed by this dialog.
        ConfirmExitDialog.newInstance( this ).show( fragManager, "exit_dialog" );
    }

    @Override public void onStop() {
        super.onStop();

        // returns to the login screen, for data security, unless app is changing configuration.
        if ( !isChangingConfigurations() )
            returnToLogin();
    }

    /** Method called following a positive response in the exit dialog, or automatically when the
     * activity is no-longer being shown. */
    private void returnToLogin() {
        // this transaction is to ensure the loaderFragment has unbound from the data service.
        fragManager.beginTransaction().detach( loaderFragment ).commitAllowingStateLoss();
        fragManager.executePendingTransactions();
        // now we can leave the activity with a clean handover.
        supportNavigateUpTo( new Intent( getApplication(), LoginActivity.class ) );
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState( outState );
        outState.putBoolean( KEY_IN_DETAIL_VIEW, inDetailView );
    }

//--------------------------------------------------------------------------------
    // this call is from the LoaderFragment Callbacks, and is passed directly on to the
    // listFragment, which also implements the interface.
    @Override public void swapCursorForThis(Cursor cursor) {
        listFragment.swapCursorForThis( cursor );
    }
//------------------------------------------------------------------------------------
    public static class ConfirmExitDialog extends DialogFragment {

        private static ItemViewActivity p;
        public ConfirmExitDialog() {}

        public static ConfirmExitDialog newInstance( ItemViewActivity parent ) {
            p = parent;
            return new ConfirmExitDialog();
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
           // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();

            // Inflate and set the layout for the dialog
            View dialog = inflater.inflate( R.layout.dialog_fragment_twobox, null );

            TextView title = (TextView) dialog.findViewById( R.id.dialog_title );
            title.setText( R.string.title_imageView_exit_dialog );

            TextView message = (TextView) dialog.findViewById( R.id.dialog_message );
            message.setText( R.string.message_itemView_exit_dialog );

            TextView confirm = (TextView) dialog.findViewById( R.id.dialog_message2 );
            confirm.setText( R.string.prompt_itemView_exit_dialog );

            MyListener clickListener = new MyListener( this );

            return new AlertDialog.Builder( getActivity() )
                    .setView( dialog )
                    .setPositiveButton( R.string.yes, clickListener )
                    .setNegativeButton( R.string.no, clickListener )
                    .create();
        }
        // not written as an anonymous class to prevent memory leaks.
        private static class MyListener implements DialogInterface.OnClickListener {

            private final ConfirmExitDialog exitDialog;

            MyListener(ConfirmExitDialog dialog) { exitDialog = dialog; }

            @Override public void onClick(DialogInterface dialog, int which) {
                switch ( which ) {
                    case DialogInterface.BUTTON_POSITIVE:
                        p.returnToLogin();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        exitDialog.dismiss();
                        break;
                }
            }
        }
    }
}
