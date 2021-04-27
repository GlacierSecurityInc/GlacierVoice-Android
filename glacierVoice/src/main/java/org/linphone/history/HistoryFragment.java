/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.history;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.MainActivity;
import org.linphone.call.views.LinphoneLinearLayoutManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.utils.SelectableHelper;

public class HistoryFragment extends Fragment
        implements OnClickListener,
                OnItemClickListener,
                HistoryViewHolder.ClickListener,
                ContactsUpdatedListener,
                SelectableHelper.DeleteListener,
                LinphoneContext.CoreStartedListener {
    private RecyclerView mHistoryList;
    private TextView mNoCallHistory, mNoMissedCallHistory;
    private ImageView mMissedCalls, mAllCalls, mAllowCallLogs;
    private View mAllCallsSelected, mMissedCallsSelected, mAllowCallLogsSelected;
    private boolean mOnlyDisplayMissedCalls;
    private List<CallLog> mLogs;
    private HistoryAdapter mHistoryAdapter;
    private SelectableHelper mSelectionHelper;
    private CoreListenerStub mListener;

    public static boolean mAllowCallHistory = true;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.history, container, false);
        mSelectionHelper = new SelectableHelper(view, this);

        mHistoryList = view.findViewById(R.id.history_list);

        LinearLayoutManager layoutManager = new LinphoneLinearLayoutManager(getActivity());
        mHistoryList.setLayoutManager(layoutManager);
        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mHistoryList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.divider));
        mHistoryList.addItemDecoration(dividerItemDecoration);

        mAllCalls = view.findViewById(R.id.all_calls);
        mAllCalls.setOnClickListener(this);

        mAllCallsSelected = view.findViewById(R.id.all_calls_select);

        mMissedCalls = view.findViewById(R.id.missed_calls);
        mMissedCalls.setOnClickListener(this);

        mMissedCallsSelected = view.findViewById(R.id.missed_calls_select);

        mAllowCallLogs = view.findViewById(R.id.allow_call_history);
        mAllowCallLogs.setOnClickListener(this);

        mAllowCallLogsSelected = view.findViewById(R.id.allow_call_history_select);

        mNoCallHistory = view.findViewById(R.id.no_call_history);
        mNoMissedCallHistory = view.findViewById(R.id.no_missed_call_history);

        if (mAllowCallHistory) {
            mNoCallHistory.setText(R.string.no_call_history);
            mNoMissedCallHistory.setText(R.string.no_missed_call_history);
            mAllowCallLogs.setImageResource(R.drawable.allow_history_yes);
        } else {
            mNoCallHistory.setText(R.string.call_history_not_allowed);
            mNoMissedCallHistory.setText(R.string.call_history_not_allowed);
            mAllowCallLogs.setImageResource(R.drawable.allow_history_no);
        }

        mAllCalls.setEnabled(false);
        mOnlyDisplayMissedCalls = false;

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Error) {
                            reloadData();
                        }
                    }
                };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        ContactsManager.getInstance().addContactsListener(this);
        LinphoneContext.instance().addCoreStartedListener(this);
        LinphoneManager.getCore().addListener(mListener);

        reloadData();
    }

    @Override
    public void onPause() {
        ContactsManager.getInstance().removeContactsListener(this);
        LinphoneContext.instance().removeCoreStartedListener(this);
        LinphoneManager.getCore().removeListener(mListener);

        super.onPause();
    }

    @Override
    public void onContactsUpdated() {

        if (mAllowCallHistory) {
            HistoryAdapter adapter = (HistoryAdapter) mHistoryList.getAdapter();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onCoreStarted() {
        reloadData();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.all_calls) {
            if (mAllowCallHistory) {
                mAllCalls.setEnabled(false);
                mAllCallsSelected.setVisibility(View.VISIBLE);
                mMissedCallsSelected.setVisibility(View.INVISIBLE);
                mAllowCallLogsSelected.setVisibility(View.INVISIBLE);
                mMissedCalls.setEnabled(true);
                mOnlyDisplayMissedCalls = false;
                refresh();
            } else {
                mAllCalls.setEnabled(true);
                mMissedCalls.setEnabled(true);
                mAllCallsSelected.setVisibility(View.INVISIBLE);
                mMissedCallsSelected.setVisibility(View.INVISIBLE);
                mAllowCallLogsSelected.setVisibility(View.INVISIBLE);
            }
        }
        if (id == R.id.missed_calls) {
            if (mAllowCallHistory) {
                mAllCalls.setEnabled(true);
                mAllCallsSelected.setVisibility(View.INVISIBLE);
                mMissedCallsSelected.setVisibility(View.VISIBLE);
                mAllowCallLogsSelected.setVisibility(View.INVISIBLE);
                mMissedCalls.setEnabled(false);
                mOnlyDisplayMissedCalls = true;
            } else {
                mAllCalls.setEnabled(true);
                mMissedCalls.setEnabled(true);
                mAllCallsSelected.setVisibility(View.INVISIBLE);
                mMissedCallsSelected.setVisibility(View.INVISIBLE);
                mAllowCallLogsSelected.setVisibility(View.INVISIBLE);
            }
        }
        if (id == R.id.allow_call_history) {
            runCallHistoryLogOnOff();
        }

        hideHistoryListAndDisplayMessageIfEmpty();
        mHistoryAdapter =
                new HistoryAdapter((HistoryActivity) getActivity(), mLogs, this, mSelectionHelper);
        mHistoryList.setAdapter(mHistoryAdapter);
        mSelectionHelper.setAdapter(mHistoryAdapter);
        mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);
    }

    public void runCallHistoryLogOnOff() {
        if (mAllowCallHistory) {
            showDisableCallLogConfirmationDialog();
        } else {
            showEnableCallLogConfirmationDialog();
        }
    }

    /** Display Disable Call Log confirmation */
    private void showDisableCallLogConfirmationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.disable_call_log_title)
                .setMessage(R.string.disable_call_log_confirmation)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(
                        android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                mAllowCallHistory = false;

                                mAllowCallLogs.setImageResource(R.drawable.allow_history_no);

                                mNoCallHistory.setText(R.string.call_history_not_allowed);
                                mNoMissedCallHistory.setText(R.string.call_history_not_allowed);

                                mAllCalls.setEnabled(true);
                                mMissedCalls.setEnabled(true);
                                mAllCallsSelected.setVisibility(View.INVISIBLE);
                                mMissedCallsSelected.setVisibility(View.INVISIBLE);
                                mAllowCallLogsSelected.setVisibility(
                                        View.INVISIBLE);

                                mOnlyDisplayMissedCalls = false;
                                refresh();
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    /** Display Disable Call Log confirmation */
    private void showEnableCallLogConfirmationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.enable_call_log_title)
                .setMessage(R.string.enable_call_log_confirmation)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(
                        android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                mAllowCallHistory = true;

                                mAllowCallLogs.setImageResource(R.drawable.allow_history_yes);

                                mNoCallHistory.setText(R.string.no_call_history);
                                mNoMissedCallHistory.setText(R.string.no_missed_call_history);

                                mAllCalls.setEnabled(false);
                                mMissedCalls.setEnabled(true);
                                mAllCallsSelected.setVisibility(View.VISIBLE);
                                mMissedCallsSelected.setVisibility(View.INVISIBLE);
                                mAllowCallLogsSelected.setVisibility(View.INVISIBLE);

                                mOnlyDisplayMissedCalls = false;
                                refresh();
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
        if (mHistoryAdapter.isEditionEnabled()) {
            CallLog log = mLogs.get(position);
            Core core = LinphoneManager.getCore();
            core.removeCallLog(log);
            mLogs = Arrays.asList(core.getCallLogs());
        }
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        int size = mHistoryAdapter.getSelectedItemCount();
        for (int i = 0; i < size; i++) {
            CallLog log = (CallLog) objectsToDelete[i];
            LinphoneManager.getCore().removeCallLog(log);
            onResume();
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (mHistoryAdapter.isEditionEnabled()) {
            mHistoryAdapter.toggleSelection(position);
        } else {
            if (position >= 0 && position < mLogs.size()) {
                CallLog log = mLogs.get(position);
                Address address;
                if (log.getDir() == Call.Dir.Incoming) {
                    address = log.getFromAddress();
                } else {
                    address = log.getToAddress();
                }
                if (address != null) {
                    ((MainActivity) getActivity()).newOutgoingCall(address.asStringUriOnly());
                }
            }
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mHistoryAdapter.isEditionEnabled()) {
            mSelectionHelper.enterEditionMode();
        }
        mHistoryAdapter.toggleSelection(position);
        return true;
    }

    private void refresh() {
        if (mAllowCallHistory) {
            mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());
        } else {
            if (mLogs.size() > 0) {
                mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());
                RemoveAllCallHistory();
            }
            mNoCallHistory.setVisibility(View.VISIBLE);
            mHistoryList.setVisibility(View.GONE);
        }
    }

    public void displayFirstLog() {
        Address addr;
        if (mLogs != null && mLogs.size() > 0) {
            CallLog log = mLogs.get(0); // More recent one is 0
            if (log.getDir() == Call.Dir.Incoming) {
                addr = log.getFromAddress();
            } else {
                addr = log.getToAddress();
            }
            ((HistoryActivity) getActivity()).showHistoryDetails(addr);
        } else {
            ((HistoryActivity) getActivity()).showEmptyChildFragment();
        }
    }

    private void reloadData() {
        if (mAllowCallHistory) {
            mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());
            hideHistoryListAndDisplayMessageIfEmpty();
            mHistoryAdapter =
                    new HistoryAdapter(
                            (HistoryActivity) getActivity(), mLogs, this, mSelectionHelper);
            mHistoryList.setAdapter(mHistoryAdapter);
            mSelectionHelper.setAdapter(mHistoryAdapter);
            mSelectionHelper.setDialogMessage(R.string.call_log_delete_dialog);

            mAllowCallLogs.setImageResource(R.drawable.allow_history_yes);

            mNoCallHistory.setText(R.string.no_call_history);
            mNoMissedCallHistory.setText(R.string.no_missed_call_history);

            mAllCalls.setEnabled(false);
            mMissedCalls.setEnabled(true);
            mAllCallsSelected.setVisibility(View.VISIBLE);
            mMissedCallsSelected.setVisibility(View.INVISIBLE);
            mAllowCallLogsSelected.setVisibility(View.INVISIBLE);
            mOnlyDisplayMissedCalls = false;
            refresh();

        } else { // Disabled Call History Logging
            mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());

            if (mLogs.size() > 0) {
                RemoveAllCallHistory();
            }
            mNoCallHistory.setVisibility(View.VISIBLE);
            mHistoryList.setVisibility(View.GONE);

            mAllowCallLogs.setImageResource(R.drawable.allow_history_no);

            mNoCallHistory.setText(R.string.call_history_not_allowed);
            mNoMissedCallHistory.setText(R.string.call_history_not_allowed);

            mAllCalls.setEnabled(true);
            mMissedCalls.setEnabled(true);
            mAllCallsSelected.setVisibility(View.INVISIBLE);
            mMissedCallsSelected.setVisibility(View.INVISIBLE);
            mAllowCallLogsSelected.setVisibility(View.INVISIBLE);
            mOnlyDisplayMissedCalls = false;
            refresh();
        }
    }

    private void RemoveAllCallHistory() {
        if (!mAllowCallHistory) {
            if (mLogs.size() > 0) {
                DeleteAllCallHistory();
            }
        }
    }

    public void DeleteAllCallHistory() {
        int size = mLogs.size();
        for (int i = 0; i < size; i++) {
            try {
                CallLog log = mLogs.get(i);
                LinphoneManager.getCore().removeCallLog(log);
            } catch (Exception e) {
                com.glaciersecurity.glaciervoice.Log.d(
                        "Glacier",
                        " -- DeleteAllCallHistory() in HistoryFragment -- Caught an Exception");
            }
        }
        onResume();
    }

    private void removeNotMissedCallsFromLogs() {
        if (mOnlyDisplayMissedCalls) {
            List<CallLog> missedCalls = new ArrayList<>();
            for (CallLog log : mLogs) {
                if (log.getStatus() == Call.Status.Missed) {
                    missedCalls.add(log);
                }
            }
            mLogs = missedCalls;
        }
    }

    private void hideHistoryListAndDisplayMessageIfEmpty() {
        removeNotMissedCallsFromLogs();
        mNoCallHistory.setVisibility(View.GONE);
        mNoMissedCallHistory.setVisibility(View.GONE);

        if (mLogs.isEmpty()) {
            if (mOnlyDisplayMissedCalls) {
                mNoMissedCallHistory.setVisibility(View.VISIBLE);
            } else {
                mNoCallHistory.setVisibility(View.VISIBLE);
            }
            mHistoryList.setVisibility(View.GONE);
        } else {
            mNoCallHistory.setVisibility(View.GONE);
            mNoMissedCallHistory.setVisibility(View.GONE);
            mHistoryList.setVisibility(View.VISIBLE);
        }
    }
}
