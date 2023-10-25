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

package io.nekohasekai.sagernet.fmt.mieru2;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class Mieru2Bean extends AbstractBean {

    public static final int PROTOCOL_TCP = 0;
    public static final int PROTOCOL_UDP = 1;

    public static final int MULTIPLEXING_OFF = 1;
    public static final int MULTIPLEXING_LOW = 0;
    public static final int MULTIPLEXING_MIDDLE = 2;
    public static final int MULTIPLEXING_HIGH = 3;

    public Integer protocol;
    public String username;
    public String password;
    public Integer mtu;
    public Integer muxLevel;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocol == null) protocol = PROTOCOL_TCP;
        if (username == null) username = "";
        if (password == null) password = "";
        if (mtu == null) mtu = 1400;
        if (muxLevel == null) muxLevel = MULTIPLEXING_LOW;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeInt(protocol);
        output.writeString(username);
        output.writeString(password);
        if (protocol == PROTOCOL_UDP) {
            output.writeInt(mtu);
        }
        output.writeInt(muxLevel);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        protocol = input.readInt();
        username = input.readString();
        password = input.readString();
        if (protocol == PROTOCOL_UDP) {
            mtu = input.readInt();
        }
        if (version >= 1) {
            muxLevel = input.readInt();
        }
    }

    @NotNull
    @Override
    public Mieru2Bean clone() {
        return KryoConverters.deserialize(new Mieru2Bean(), KryoConverters.serialize(this));
    }

    public static final Creator<Mieru2Bean> CREATOR = new CREATOR<Mieru2Bean>() {
        @NonNull
        @Override
        public Mieru2Bean newInstance() {
            return new Mieru2Bean();
        }

        @Override
        public Mieru2Bean[] newArray(int size) {
            return new Mieru2Bean[size];
        }
    };
}
