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

package io.nekohasekai.sagernet.fmt.shadowtls;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ShadowTLSBean extends AbstractBean {

    public String sni;
    public String password;
    public String alpn;
    public Boolean v3;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (sni == null) sni = "";
        if (password == null) password = "";
        if (alpn == null) alpn = "";
        if (v3 == null) v3 = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(sni);
        output.writeString(password);
        output.writeString(alpn);
        output.writeBoolean(v3);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        sni = input.readString();
        password = input.readString();
        alpn = input.readString();
        v3 = input.readBoolean();
    }

    @NonNull
    @Override
    public ShadowTLSBean clone() {
        return KryoConverters.deserialize(new ShadowTLSBean(), KryoConverters.serialize(this));
    }

    public static final Creator<ShadowTLSBean> CREATOR = new CREATOR<ShadowTLSBean>() {
        @NonNull
        @Override
        public ShadowTLSBean newInstance() {
            return new ShadowTLSBean();
        }

        @Override
        public ShadowTLSBean[] newArray(int size) {
            return new ShadowTLSBean[size];
        }
    };


}
