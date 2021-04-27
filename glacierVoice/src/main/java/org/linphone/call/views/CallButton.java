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
package org.linphone.call.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import org.linphone.LinphoneManager;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;
import org.linphone.dialer.views.AddressAware;
import org.linphone.dialer.views.AddressText;
import org.linphone.settings.LinphonePreferences;

@SuppressLint("AppCompatCustomView")
public class CallButton extends ImageView implements OnClickListener, AddressAware {
    private AddressText mAddress;
    private boolean mIsTransfer;

    public CallButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        mIsTransfer = false;
        setOnClickListener(this);
    }

    public void setAddressWidget(AddressText a) {
        mAddress = a;
    }

    public void setIsTransfer(boolean isTransfer) {
        mIsTransfer = isTransfer;
    }

    public String getSipNumber(String uri) {
        if ((uri != null) && (uri.contains("@"))) {
            if (uri.contains(":")) {
                // format = <sip:10110@192.168.1.1>
                return uri.split("@")[0].split(":")[1];
            }

            // format = 1011@192.168.1.1
            return uri.split("@")[0];
        }
        // not of 10110@192.168.1.1 format
        return uri;
    }

    public void onClick(View v) {
        if (mAddress.getText().length() > 0) {
            if (mIsTransfer) {
                Core core = LinphoneManager.getCore();
                if (core.getCurrentCall() == null) {
                    return;
                }
                core.getCurrentCall().transfer(mAddress.getText().toString());
            } else {
                LinphoneManager.getCallManager().newOutgoingCall(mAddress);
            }

            // clear address screen so dialed number is not saved
            mAddress.setText("");

        } else {
            if (LinphonePreferences.instance().isBisFeatureEnabled()) {
                Core core = LinphoneManager.getCore();
                CallLog[] logs = core.getCallLogs();
                CallLog log = null;
                for (CallLog l : logs) {
                    if (l.getDir() == Call.Dir.Outgoing) {
                        log = l;
                        break;
                    }
                }
                if (log == null) {
                    return;
                }

                ProxyConfig lpc = core.getDefaultProxyConfig();
                if (lpc != null && log.getToAddress().getDomain().equals(lpc.getDomain())) {
                    mAddress.setText(log.getToAddress().getUsername());
                    mAddress.setSelection(mAddress.getText().toString().length());
                    mAddress.setDisplayedName(log.getToAddress().getDisplayName());
                } else {
                    mAddress.setText(log.getToAddress().asStringUriOnly());
                    if (mAddress != null) {
                        String fullURI = mAddress.getText().toString();
                        String caller_sip = getSipNumber(fullURI);
                        mAddress.setText(caller_sip);
                    }
                    mAddress.setSelection(mAddress.getText().toString().length());
                    mAddress.setDisplayedName(log.getToAddress().getDisplayName());
                }
            }
        }
    }
}
