/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.journalmanager.Exceptions;
import at.bitfire.davdroid.journalmanager.JournalManager;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import lombok.Cleanup;
import okhttp3.HttpUrl;

public class CreateCollectionFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Exception> {
    private static final String
            ARG_ACCOUNT = "account",
            ARG_COLLECTION_INFO = "collectionInfo";

    protected Account account;
    protected CollectionInfo info;

    public static CreateCollectionFragment newInstance(Account account, CollectionInfo info) {
        CreateCollectionFragment frag = new CreateCollectionFragment();
        Bundle args = new Bundle(2);
        args.putParcelable(ARG_ACCOUNT, account);
        args.putSerializable(ARG_COLLECTION_INFO, info);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getArguments().getParcelable(ARG_ACCOUNT);
        info = (CollectionInfo)getArguments().getSerializable(ARG_COLLECTION_INFO);

        getLoaderManager().initLoader(0, null, this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progress = new ProgressDialog(getContext());
        progress.setTitle(R.string.create_collection_creating);
        progress.setMessage(getString(R.string.please_wait));
        progress.setIndeterminate(true);
        progress.setCanceledOnTouchOutside(false);
        setCancelable(false);
        return progress;
    }


    @Override
    public Loader<Exception> onCreateLoader(int id, Bundle args) {
        return new CreateCollectionLoader(getContext(), account, info);
    }

    @Override
    public void onLoadFinished(Loader<Exception> loader, Exception exception) {
        dismissAllowingStateLoss();

        Activity parent = getActivity();
        if (parent != null) {
            if (exception != null)
                getFragmentManager().beginTransaction()
                        .add(ExceptionInfoFragment.newInstance(exception, account), null)
                        .commitAllowingStateLoss();
            else
                parent.finish();
        }

    }

    @Override
    public void onLoaderReset(Loader<Exception> loader) {
    }


    protected static class CreateCollectionLoader extends AsyncTaskLoader<Exception> {
        final Account account;
        final CollectionInfo info;

        public CreateCollectionLoader(Context context, Account account, CollectionInfo info) {
            super(context);
            this.account = account;
            this.info = info;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public Exception loadInBackground() {
            ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());

            try {
                // now insert collection into database:
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                // 1. find service ID
                String serviceType;
                if (info.type == CollectionInfo.Type.ADDRESS_BOOK)
                    serviceType = ServiceDB.Services.SERVICE_CARDDAV;
                else if (info.type == CollectionInfo.Type.CALENDAR)
                    serviceType = ServiceDB.Services.SERVICE_CALDAV;
                else
                    throw new IllegalArgumentException("Collection must be an address book or calendar");
                @Cleanup Cursor c = db.query(ServiceDB.Services._TABLE, new String[] { ServiceDB.Services.ID },
                        ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?",
                        new String[] { account.name, serviceType }, null, null, null
                );
                if (!c.moveToNext())
                    throw new IllegalStateException();
                long serviceID = c.getLong(0);

                AccountSettings settings = new AccountSettings(getContext(), account);
                HttpUrl principal = HttpUrl.get(settings.getUri());

                JournalManager journalManager = new JournalManager(HttpClient.create(getContext(), account), principal);
                journalManager.putJournal(new JournalManager.Journal(settings.password(), info.toJson(), info.url));

                // 2. add collection to service
                ContentValues values = info.toDB();
                values.put(ServiceDB.Collections.SERVICE_ID, serviceID);
                db.insert(ServiceDB.Collections._TABLE, null, values);
            } catch(IllegalStateException|Exceptions.HttpException e) {
                return e;
            } catch (InvalidAccountException e) {
                return e;
            } finally {
                dbHelper.close();
            }

            return null;
        }
    }

}
