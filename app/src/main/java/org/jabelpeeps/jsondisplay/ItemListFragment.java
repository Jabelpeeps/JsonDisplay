package org.jabelpeeps.jsondisplay;

import android.app.Activity;
import android.app.ListFragment;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ListView;

/**
 * A list fragment representing a list of Items. This fragment also supports tablet devices by
 * allowing list items to be given an 'activated' state upon selection. This helps indicate which
 * item is currently being viewed in a {@link ItemDetailFragment}.
 * <p/>
 * Activities containing this fragment MUST implement the {@link ListFragmentCallbacks} interface.
 */
public class ItemListFragment extends ListFragment implements ItemLoaderFragment.ItemLoaderCallbacks {

    /** Bundle key representing the activated item position. */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";
    
    /** The fragment's current callback object, which is notified of list item clicks. */
    private ListFragmentCallbacks myCallbacks = sDummyCallbacks;
    
    /** The current activated item position. Only used on tablets. */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    private ItemLoaderFragment.PostAdapter listAdapter;
    private View mProgressContainer;
    private View mListContainer;
    private boolean mListShown = false;

    /** A callback interface that all activities containing this fragment must implement. */
    public interface ListFragmentCallbacks {
        /** Callback for when an item has been selected. */
        void onItemDetailRequested(int id);
        /** Callback to check the display mode of the activity */
        boolean isInTwoPanes();
    }
    /** dummy implementation of ListFragmentCallbacks interface. Used when fragment is not attached to an activity. */
    private static final ListFragmentCallbacks sDummyCallbacks = new ListFragmentCallbacks() {
        @Override public void onItemDetailRequested(int id) {}
        @Override public boolean isInTwoPanes() { return false; }
    };

    /** Mandatory empty constructor */
    public ItemListFragment() {}

    @Override public void onAttach(Activity activity) {
        super.onAttach( activity );

        // Activities containing this fragment must implement its callbacks.
        if ( !( activity instanceof ListFragmentCallbacks ) ) {
            throw new IllegalStateException( "Activity must implement fragment's callbacks." );
        }
        myCallbacks = (ListFragmentCallbacks) activity;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );

        setRetainInstance( true );
        listAdapter = new ItemLoaderFragment.PostAdapter();
        setListAdapter( listAdapter );
    }

    @Override
    public View onCreateView(LayoutInflater infl, ViewGroup container, Bundle savedInstanceState) {
        // supply my custom layout .xml file to the be the listView.
        return getActivity().getLayoutInflater()
                             .inflate( R.layout.view_list_content, container, false );
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated( view, savedInstanceState );

        // In order to use the setListShown method with a custom layout, I have imported a slightly
        // modified version of the framework's method (that does not allow custom layouts), at the
        // end of this class file.  As far as I can see, the only result of this will be the need
        // for us to be sure to maintain views with the following names in the layout:-
        mProgressContainer = view.findViewById( R.id.progressContainer );
        mListContainer = view.findViewById( R.id.listContainer );
        setListShown( false );

        getListView().setFastScrollEnabled( true );

        // Restore the previously serialized activated item position.
        if ( savedInstanceState != null
                && savedInstanceState.containsKey( STATE_ACTIVATED_POSITION ) )
            setActivatedPosition( savedInstanceState.getInt( STATE_ACTIVATED_POSITION ) );
    }

    @Override public void onStart() {
        super.onStart();
        // In two-pane mode, list items should be given the 'activated' state when touched.
        // We use a boolean from the callback to call the next method.
        setActivateOnItemClick( myCallbacks.isInTwoPanes() );
    }

    @Override public void onDetach() {
        super.onDetach();
        // Reset the active callbacks interface to the dummy implementation.
        myCallbacks = sDummyCallbacks;
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState( outState );

        if ( mActivatedPosition != ListView.INVALID_POSITION ) {
            // Serialize and persist the activated item position.
            outState.putInt( STATE_ACTIVATED_POSITION, mActivatedPosition );
        }
    }

    @Override public void onListItemClick(ListView listView, View view, int position, long id) {

        setActivatedPosition( position );
        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        myCallbacks.onItemDetailRequested( position );
    }

    private void setActivateOnItemClick(boolean activateOnItemClick) {
        // in CHOICE_MODE_SINGLE, ListView will give touched items the 'activated' state.
        getListView().setChoiceMode( activateOnItemClick ? ListView.CHOICE_MODE_SINGLE
                                                         : ListView.CHOICE_MODE_NONE );
    }
    
    private void setActivatedPosition(int position) {
        if ( position == ListView.INVALID_POSITION )
            getListView().setItemChecked( mActivatedPosition, false );
        else
            getListView().setItemChecked( position, true );
        mActivatedPosition = position;
    }

    // callback from ItemLoaderFragment
    public void swapCursorForThis(Cursor cursor) {
        listAdapter.swapCursor( cursor );

        if ( cursor != null && cursor.getCount() != 0 )
            setListShown( true );
        else
            setListShown( false );
    }
    // See explanation in onViewCreated() regarding these methods.
    @Override public void setListShown(boolean shown) {
        setListShown( shown, true );
    }

    private void setListShown(boolean shown, boolean animate) {

        if ( mListShown == shown ) return;

        mListShown = shown;

        if ( shown ) {
            if ( animate ) {
                mProgressContainer.startAnimation( AnimationUtils.loadAnimation(
                        getActivity(), android.R.anim.fade_out ) );
                mListContainer.startAnimation( AnimationUtils.loadAnimation(
                        getActivity(), android.R.anim.fade_in ) );
            }
            else {
                mProgressContainer.clearAnimation();
                mListContainer.clearAnimation();
            }
            mProgressContainer.setVisibility( View.GONE );
            mListContainer.setVisibility( View.VISIBLE );
        }
        else {
            if ( animate ) {
                mProgressContainer.startAnimation( AnimationUtils.loadAnimation(
                        getActivity(), android.R.anim.fade_in ) );
                mListContainer.startAnimation( AnimationUtils.loadAnimation(
                        getActivity(), android.R.anim.fade_out ) );
            }
            else {
                mProgressContainer.clearAnimation();
                mListContainer.clearAnimation();
            }
            mProgressContainer.setVisibility( View.VISIBLE );
            mListContainer.setVisibility( View.GONE );
        }
    }

}
