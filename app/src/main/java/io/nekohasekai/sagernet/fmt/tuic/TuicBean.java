/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.fmt.tuic;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class TuicBean extends AbstractBean {

    public String uuid;
    public String password;
    public String caText;
    public String udpRelayMode;
    public String congestionControl;
    public String alpn;
    public Boolean disableSNI;
    public Boolean zeroRTTHandshake;
    public Integer mtu;
    public String sni;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (uuid == null) uuid = "";
        if (password == null) password = "";
        if (caText == null) caText = "";
        if (udpRelayMode == null) udpRelayMode = "native";
        if (congestionControl == null) congestionControl = "cubic";
        if (alpn == null) alpn = "";
        if (disableSNI == null) disableSNI = false;
        if (zeroRTTHandshake == null) zeroRTTHandshake = false;
        if (mtu == null) mtu = 1500;
        if (sni == null) sni = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(password);
        output.writeString(caText);
        output.writeString(udpRelayMode);
        output.writeString(congestionControl);
        output.writeString(alpn);
        output.writeBoolean(disableSNI);
        output.writeBoolean(zeroRTTHandshake);
        output.writeInt(mtu);
        output.writeString(sni);
        output.writeString(uuid);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        password = input.readString(); // was "token"
        caText = input.readString();
        udpRelayMode = input.readString();
        congestionControl = input.readString(); // was "congestionController"
        alpn = input.readString();
        disableSNI = input.readBoolean();
        zeroRTTHandshake = input.readBoolean(); // was "reduceRTT"
        mtu = input.readInt();
        sni = input.readString();
        if (version >= 1) {
            uuid = input.readString();
        }
    }

    @NotNull
    @Override
    public TuicBean clone() {
        return KryoConverters.deserialize(new TuicBean(), KryoConverters.serialize(this));
    }

    public static final Creator<TuicBean> CREATOR = new CREATOR<TuicBean>() {
        @NonNull
        @Override
        public TuicBean newInstance() {
            return new TuicBean();
        }

        @Override
        public TuicBean[] newArray(int size) {
            return new TuicBean[size];
        }
    };
}
