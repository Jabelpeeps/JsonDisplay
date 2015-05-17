package org.jabelpeeps.jsondisplay;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class ItemDetailFragment extends Fragment {
    /** The fragment argument key for the argument containing the data. */
    public static final String ARG_POS = "item_pos";
    public static final String ARG_USER = "user";
    public static final String ARG_TITLE = "title";
    public static final String ARG_BODY = "body";
    
    /** The content this fragment is presenting. */
    private int pos;
    private String user;
    private String title;
    private String body;
    
    /** Mandatory empty constructor. */
    public ItemDetailFragment() {}

    int getPos() { return pos; }
    
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );

        if ( getArguments().containsKey( ARG_POS ) )
            pos = getArguments().getInt( ARG_POS );
        if ( getArguments().containsKey( ARG_USER ) )
            user = getArguments().getString( ARG_USER );
        if ( getArguments().containsKey( ARG_TITLE ) )
            title = getArguments().getString( ARG_TITLE );
        if ( getArguments().containsKey( ARG_BODY ) )
            body = getArguments().getString( ARG_BODY );
    }
    
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle b) {

        View rootView = inflater.inflate( R.layout.fragment_item_detail, container, false );
        
        // Show the content as text in the appropriate TextView.
        if ( user != null ) ( (TextView)rootView.findViewById( R.id.item_user ) ).setText( user );
        if ( title != null ) ( (TextView)rootView.findViewById( R.id.item_title ) ).setText( title );
        if ( body != null ) ( (TextView)rootView.findViewById( R.id.item_body ) ).setText( body );
        
        return rootView;
    }
}
