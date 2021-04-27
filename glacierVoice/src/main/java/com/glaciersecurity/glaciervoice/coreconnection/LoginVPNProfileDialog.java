package com.glaciersecurity.glaciervoice.coreconnection;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.linphone.R;

/** Created by glaciersecurity on 5/24/18. */
public class LoginVPNProfileDialog extends Dialog implements View.OnClickListener {
    int mNum;
    String profileList = null;

    public Button cancelButton;
    public Button okButton;

    Spinner profileSpinner = null;

    String[] profileArray = null;

    private LoginVPNProfileDialog profileDialog = null;

    private String profileNames = null;
    private Context context = null;

    /**
     * Testing parameter
     *
     * @param context
     */
    public LoginVPNProfileDialog(Context context, String _profileNames) {
        super(context);

        profileNames = _profileNames;
        this.context = context;
        profileDialog = this;
    }

    public void setProfiles(String _profileNames) {
        profileNames = _profileNames;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.core_connection_profile_dialog);
        View v = this.findViewById(android.R.id.content);

        setTitle(R.string.open_vpn_profile_dialog_title);
        profileSpinner = (Spinner) v.findViewById(R.id.file_spinner);

        List<String> listStr = new ArrayList<String>();

        if (profileNames != null) {
            profileArray = profileNames.split("::");
        } else {
            profileArray = null;
        }

        // check for available profiles.  Key is not to crash.  Should probably
        // pop up error or something.
        if (profileArray != null) {
            for (int i = 0; i < profileArray.length; i++) {
                listStr.add(profileArray[i]);
            }

            // sort list of profiles
            Collections.sort(listStr);

            ArrayAdapter<String> spinnerAdapter =
                    new ArrayAdapter<String>(
                            getContext(), android.R.layout.simple_spinner_item, listStr);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            profileSpinner.setAdapter(spinnerAdapter);
            spinnerAdapter.notifyDataSetChanged();

            TextView textView = v.findViewById(R.id.message);
            textView.setText(R.string.open_vpn_profile_dialog_message);

            // prevent dialog from disappearing before button press finish
            this.setCancelable(false);

            // this.setCanceledOnTouchOutside(false);
            okButton = (Button) v.findViewById(R.id.ok_button);
            okButton.setOnClickListener(this);

        } else {
            // No profiles, set appropriate text and button.
            // This is not exactly how we should handle this situation
            // but it should never get this point if
            // everything is set up correctly.
            TextView textView = v.findViewById(R.id.message);
            textView.setText(R.string.no_open_vpn_profile_dialog_message);

            // this.setCanceledOnTouchOutside(false);
            okButton = (Button) v.findViewById(R.id.ok_button);
            okButton.setVisibility(View.INVISIBLE);
        }

        // this.setCanceledOnTouchOutside(false);
        cancelButton = (Button) v.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        LoginVPNProfileListener activity = (LoginVPNProfileListener) context;
        Bundle bundle = new Bundle();
        Intent intent = null;

        switch (view.getId()) {
            case R.id.ok_button:
                dismiss();
                bundle.putString("PROFILE_SELECTED", (String) profileSpinner.getSelectedItem());
                intent = new Intent().putExtras(bundle);
                // getTargetFragment()
                //        .onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
                activity.onReturnValue((String) profileSpinner.getSelectedItem());
                break;
            case R.id.cancel_button:
                dismiss();
                bundle.putString("PROFILE_SELECTED", null);
                intent = new Intent().putExtras(bundle);
                // getTargetFragment()
                //         .onActivityResult(getTargetRequestCode(), Activity.RESULT_CANCELED,
                // intent);
                activity.onReturnValue(null);
                break;
        }
    }
}
