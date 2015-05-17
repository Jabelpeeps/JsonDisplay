package org.jabelpeeps.jsondisplay;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.securepreferences.SecurePreferences;

public class LoginActivity extends AppCompatActivity {

    private NumberPicker[] pickers;

    private String pin = "000000";
    private boolean firstRun = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_login );
        setSupportActionBar( (Toolbar) findViewById( R.id.login_toolbar ) );
        ActionBar actionBar = getSupportActionBar();

        if ( actionBar != null )
            actionBar.setDisplayShowTitleEnabled( false );

        String prefsFile = getString( R.string.file_name_secure_prefs );
        if ( getSharedPreferences( prefsFile, 0 ).getAll().isEmpty() )
            firstRun = true;
    }

    @Override protected void onStart() {
        super.onStart();
        // NB the NumberPickers are numbered in the layout from left to right.
        pickers = new NumberPicker[] { (NumberPicker) findViewById( R.id.pin1 ),
                                       (NumberPicker) findViewById( R.id.pin2 ),
                                       (NumberPicker) findViewById( R.id.pin3 ),
                                       (NumberPicker) findViewById( R.id.pin4 ),
                                       (NumberPicker) findViewById( R.id.pin5 ),
                                       (NumberPicker) findViewById( R.id.pin6 ) };

        for ( NumberPicker each : pickers ) {
            each.setMaxValue( 9 );
            each.setMinValue( 0 );
            each.setOnValueChangedListener( pickerListener );
        }

        Button checkPin = ( (Button) findViewById( R.id.check_pin_button ) );

        if ( firstRun ) {
            ( (TextView) findViewById( R.id.top_prompt ) ).setText( R.string.prompt_first_use );
            checkPin.setText( R.string.action_set_pin );
        }
        checkPin.setOnClickListener( buttonListener );
    }
    private final View.OnClickListener buttonListener = new View.OnClickListener(){
        @Override public void onClick(View v) {
            if ( firstRun )
                checkValidityAndSetPin();
            else
                attemptLogin();
        }
    };
    private final NumberPicker.OnValueChangeListener pickerListener = new NumberPicker.OnValueChangeListener() {
        @Override public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            pin = "";
            for ( int i = 0; i < 6; i++ )
                pin = pin + pickers[ i ].getValue();
        }
    };
    @Override protected void onStop() {
        super.onStop();
        Button checkPin = ( (Button) findViewById( R.id.check_pin_button ) );
        checkPin.setOnClickListener( null );

        for ( NumberPicker each : pickers )
            each.setOnValueChangedListener( null );
    }

    // checks inputted pin against various regex filters to exclude easy to guess values.
    private void checkValidityAndSetPin() {

        if ( pin.matches( ".*(\\d)\\1{2,}.*" ) )
            MessageDialogFragment.newInstance( R.string.error_pin_disallowed,
                                               R.string.error_repeated_digits )
                                 .show( getFragmentManager(), "error" );

        else if ( pin.matches( ".*(012|123|234|345|456|567|678|789|890).*" ) )
            MessageDialogFragment.newInstance( R.string.error_pin_disallowed,
                                               R.string.error_running_digits )
                                 .show( getFragmentManager(), "error" );

        else if ( pin.matches( ".*(098|987|876|765|654|543|432|321|210).*" ) )
            MessageDialogFragment.newInstance( R.string.error_pin_disallowed,
                                               R.string.error_reverse_digits )
                                 .show( getFragmentManager(), "error" );

        else {
            getprefs().edit().putString( "pin", pin ).apply();
            login();
        }
    }

    private SecurePreferences getprefs() {
        return new SecurePreferences( getApplication()
                                    , pin
                                    , getString( R.string.file_name_secure_prefs ) );
    }
    private void attemptLogin() {
        if ( pin.equalsIgnoreCase( getprefs().getString( "pin", "" ) ) )
            login();
        else
            MessageDialogFragment.newInstance( R.string.error_pin_disallowed,
                                               R.string.error_incorrect_password )
                                 .show( getFragmentManager(), "error" );
    }

    // passes the (by now) checked & valid pin to the data loading service, and clears the local
    // fields, before initialising the next activity.
    private void login() {
        Toast toast = Toast.makeText( this, R.string.prompt_loading, Toast.LENGTH_SHORT );
        toast.setGravity( Gravity.CENTER, 0, 0 );
        toast.show();

        Intent service = new Intent( getApplication(), GetItemsService.class );
        startService( service.putExtra( "pin", pin ) );

        // reset both pin field, and numberPickers to 0, so that pin is no longer displayed (or recorded).
        pin = "000000";
        for ( NumberPicker each : pickers ) each.setValue( 0 );

        Intent intent = new Intent( this, ItemViewActivity.class );
        startActivity( intent );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar.
        getMenuInflater().inflate( R.menu.menu_main, menu );
        return true;
    }

    // shows the help text when the user presses the button.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch ( item.getItemId() ) {
            case R.id.action_pin_help:
                MessageDialogFragment.newInstance( R.string.help_title_valid_pins,
                                                   R.string.help_text_valid_pins )
                                     .show( getFragmentManager(), "pin_help" );
                return true;
        }
        return super.onOptionsItemSelected( item );
    }
//----------------------------------------------------------------------------------------
    // persists any digits that the user has entered if they rotate the screen.
    @Override
    public void onSaveInstanceState(Bundle saveInstanceState) {
        saveInstanceState.putString( "pin", pin );
        super.onSaveInstanceState( saveInstanceState );
    }

    // restores the above.
    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState( savedInstanceState );
        pin = savedInstanceState.getString( "pin" );

        char[] tempPin = pin.toCharArray();

        // loop uses old-style syntax, as value of i is used twice in the block.
        for ( int i = 0; i < 6; i++ )
            pickers[ i ].setValue( Character.getNumericValue( tempPin[ i ] ) );
    }

//---------------------------------------------------------------------------------------
    /** a simple cut-down fragment to display arbitrary messages to users. */
    public static class MessageDialogFragment extends DialogFragment {

        public static MessageDialogFragment newInstance(int title, int message) {
            MessageDialogFragment frag = new MessageDialogFragment();
            Bundle args = new Bundle();
            args.putInt( "title", title );
            args.putInt( "message", message );
            frag.setArguments( args );
            return frag;
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();

            // Inflate and set the layout for the dialog
            View dialog = inflater.inflate( R.layout.dialog_fragment_simple, null );

            TextView title = (TextView) dialog.findViewById( R.id.dialog_title );
            title.setText( getArguments().getInt( "title" ) );

            TextView message = (TextView) dialog.findViewById( R.id.dialog_message );
            message.setText( getArguments().getInt( "message" ) );

            return new AlertDialog.Builder( getActivity() )
                                  .setView( dialog )
                                  .create();
        }
    }
}
