/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.apache.commons.lang3.StringUtils;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.widget.EditPassword;

public class LoginCredentialsFragment extends Fragment {
    EditText editUserName;
    EditPassword editUrlPassword;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.login_credentials_fragment, container, false);

        editUserName = (EditText) v.findViewById(R.id.user_name);
        editUrlPassword = (EditPassword) v.findViewById(R.id.url_password);

        if (savedInstanceState == null) {
            Activity activity = getActivity();
            Intent intent = (activity != null) ? activity.getIntent() : null;
            if (intent != null) {
                // we've got initial login data
                String username = intent.getStringExtra(LoginActivity.EXTRA_USERNAME),
                        password = intent.getStringExtra(LoginActivity.EXTRA_PASSWORD);

                editUserName.setText(username);
                editUrlPassword.setText(password);
            }
        }

        final Button createAccount = (Button) v.findViewById(R.id.create_account);
        createAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri createUri = Constants.registrationUrl.buildUpon().appendQueryParameter("email", editUserName.getText().toString()).build();
                Intent intent = new Intent(Intent.ACTION_VIEW, createUri);
                startActivity(intent);
            }
        });

        final Button login = (Button) v.findViewById(R.id.login);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginCredentials credentials = validateLoginData();
                if (credentials != null)
                    DetectConfigurationFragment.newInstance(credentials).show(getFragmentManager(), null);
            }
        });

        return v;
    }

    protected LoginCredentials validateLoginData() {
        boolean valid = true;

        URI uri = null;
        try {
            uri = new URI(Constants.serviceUrl.toString());
        } catch (URISyntaxException e) {
            App.log.severe("Should never happen, it's a constant");
        }

        String userName = editUserName.getText().toString();
        if (userName.isEmpty()) {
            editUserName.setError(getString(R.string.login_user_name_required));
            valid = false;
        }

        String password = editUrlPassword.getText().toString();
        if (password.isEmpty()) {
            editUrlPassword.setError(getString(R.string.login_password_required));
            valid = false;
        }

        return valid ? new LoginCredentials(uri, userName, password) : null;
    }

}
