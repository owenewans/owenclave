/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <sekai@neko.services>                    *
 * Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
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

package io.nekohasekai.sagernet.fmt.v2ray;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class VMessBean extends AbstractBean {

    public static VMessBean DEFAULT_BEAN = new VMessBean() {{
        serverPort = 1080;
        initDefaultValues();
    }};

    public String uuid;
    public String security;

    public int alterId;
    public String network;
    public String headerType;

    public String requestHost;
    public String path;

    public String sni;
    public boolean tls;

    public void initDefaultValues() {
        if (StrUtil.isBlank(name)) {
            name = "";
        }
        if (StrUtil.isBlank(serverAddress)) {
            serverAddress = "";
        }
        if (StrUtil.isBlank(uuid)) {
            uuid = "";
        }
        if (StrUtil.isBlank(security)) {
            security = "auto";
        }
        if (StrUtil.isBlank(network)) {
            network = "tcp";
        }
        if (StrUtil.isBlank(headerType)) {
            headerType = "none";
        }
        if (StrUtil.isBlank(requestHost)) {
            requestHost = "";
        }
        if (StrUtil.isBlank(path)) {
            path = "";
        }
        if (StrUtil.isBlank(sni)) {
            sni = "";
        }
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(uuid);
        output.writeString(security);

        output.writeInt(alterId);
        output.writeString(network);
        output.writeString(headerType);

        output.writeString(requestHost);
        output.writeString(path);

        output.writeString(sni);
        output.writeBoolean(tls);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        security = input.readString();

        alterId = input.readInt();
        network = input.readString();
        headerType = input.readString();

        requestHost = input.readString();
        path = input.readString();

        sni = input.readString();
        tls = input.readBoolean();
    }

    @NotNull
    @Override
    public VMessBean clone() {
        return KryoConverters.deserialize(new VMessBean(), KryoConverters.serialize(this));
    }
}
