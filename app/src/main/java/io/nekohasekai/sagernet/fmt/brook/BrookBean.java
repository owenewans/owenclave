/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.brook;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class BrookBean extends AbstractBean {

    public String protocol;
    public String password;
    public String wsPath;
    public Boolean insecure;
    public Boolean withoutBrookProtocol;
    public Boolean udpovertcp;
    public String tlsfingerprint;
    public String fragment;
    public String sni;
    public Boolean udpoverstream;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocol == null) protocol = "";
        if (password == null) password = "";
        if (wsPath == null) wsPath = "";
        if (insecure == null) insecure = false;
        if (withoutBrookProtocol == null) withoutBrookProtocol = false;
        if (udpovertcp == null) udpovertcp = false;
        if (tlsfingerprint == null) tlsfingerprint = "";
        if (fragment == null) fragment = "";
        if (sni == null) sni = "";
        if (udpoverstream == null) udpoverstream = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(7);
        super.serialize(output);
        output.writeString(protocol);
        output.writeString(password);
        output.writeBoolean(udpovertcp);
        switch (protocol) {
            case "ws":
                output.writeString(wsPath);
                output.writeBoolean(withoutBrookProtocol);
                break;
            case "wss": {
                output.writeString(wsPath);
                output.writeBoolean(insecure);
                output.writeBoolean(withoutBrookProtocol);
                output.writeString(tlsfingerprint);
                output.writeString(fragment);
                output.writeString(sni);
                break;
            }
            case "quic": {
                output.writeBoolean(insecure);
                output.writeBoolean(withoutBrookProtocol);
                output.writeString(sni);
                output.writeBoolean(udpoverstream);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        protocol = input.readString();
        password = input.readString();
        if (version >= 5) {
            udpovertcp = input.readBoolean();
        }
        if (version >= 1) switch (protocol) {
            case "ws":
                wsPath = input.readString();
                if (version >= 2) {
                    withoutBrookProtocol = input.readBoolean();
                }
                if (version >= 7) {
                    break;
                }
            case "wss": {
                wsPath = input.readString();
                if (version >= 2) {
                    insecure = input.readBoolean();
                    withoutBrookProtocol = input.readBoolean();
                }
                if (version >= 5) {
                    tlsfingerprint = input.readString();
                    fragment = input.readString();
                    sni = input.readString();
                }
                break;
            }
            case "quic": {
                if (version >= 4) {
                    insecure = input.readBoolean();
                    withoutBrookProtocol = input.readBoolean();
                }
                if (version >= 5) {
                    sni = input.readString();
                }
                if (version >= 6) {
                    udpoverstream = input.readBoolean();
                }
                break;
            }
            default:
                if (version == 2) {
                    input.readBoolean(); // uot, removed
                }
                break;
        }
    }

    @Override
    public boolean canTCPing() {
        return !protocol.equals("quic");
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        super.applyFeatureSettings(other);
        if (!(other instanceof BrookBean bean)) return;
        if (insecure) {
            bean.insecure = true;
        }
    }

    @NonNull
    @Override
    public BrookBean clone() {
        return KryoConverters.deserialize(new BrookBean(), KryoConverters.serialize(this));
    }

    public static final Creator<BrookBean> CREATOR = new CREATOR<BrookBean>() {
        @NonNull
        @Override
        public BrookBean newInstance() {
            return new BrookBean();
        }

        @Override
        public BrookBean[] newArray(int size) {
            return new BrookBean[size];
        }
    };


}
