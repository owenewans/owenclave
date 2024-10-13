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

package io.nekohasekai.sagernet.fmt.v2ray;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.util.List;
import java.util.Objects;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.ktx.NetsKt;
import io.nekohasekai.sagernet.ktx.UUIDsKt;

public abstract class StandardV2RayBean extends AbstractBean {

    public String uuid;
    public String encryption;
    public String type;
    public String host;
    public String path;
    public String headerType;
    public String mKcpSeed;
    public String quicSecurity;
    public String quicKey;
    public String security;
    public String sni;
    public String alpn;

    public String grpcServiceName;
    public Integer wsMaxEarlyData;
    public String earlyDataHeaderName;
    public String meekUrl;

    public String certificates;
    public String pinnedPeerCertificateChainSha256;
    public String utlsFingerprint;
    public String echConfig;
    public String echDohServer;

    public Boolean wsUseBrowserForwarder;
    public Boolean shUseBrowserForwarder;
    public Boolean allowInsecure;
    public String packetEncoding;

    public String realityPublicKey;
    public String realityShortId;
    public String realitySpiderX;
    public String realityFingerprint;

    public Integer hy2DownMbps;
    public Integer hy2UpMbps;
    public String hy2Password;
    public String hy2ObfsPassword;

    public String mekyaKcpSeed;
    public String mekyaKcpHeaderType;
    public String mekyaUrl;

    public Boolean mux;
    public Integer muxConcurrency;
    public String muxPacketEncoding;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (StrUtil.isBlank(uuid)) uuid = "";

        if (StrUtil.isBlank(type)) type = "tcp";
        else if ("h2".equals(type)) type = "http";

        if (StrUtil.isBlank(host)) host = "";
        if (StrUtil.isBlank(path)) path = "";
        if (StrUtil.isBlank(headerType)) headerType = "none";
        if (StrUtil.isBlank(mKcpSeed)) mKcpSeed = "";
        if (StrUtil.isBlank(quicSecurity)) quicSecurity = "none";
        if (StrUtil.isBlank(quicKey)) quicKey = "";
        if (StrUtil.isBlank(meekUrl)) meekUrl = "";

        if (StrUtil.isBlank(security)) security = "none";
        if (StrUtil.isBlank(sni)) sni = "";
        if (StrUtil.isBlank(alpn)) alpn = "";

        if (StrUtil.isBlank(grpcServiceName)) grpcServiceName = "";
        if (wsMaxEarlyData == null) wsMaxEarlyData = 0;
        if (wsUseBrowserForwarder == null) wsUseBrowserForwarder = false;
        if (shUseBrowserForwarder == null) shUseBrowserForwarder = false;
        if (StrUtil.isBlank(certificates)) certificates = "";
        if (StrUtil.isBlank(pinnedPeerCertificateChainSha256)) pinnedPeerCertificateChainSha256 = "";
        if (StrUtil.isBlank(earlyDataHeaderName)) earlyDataHeaderName = "";
        if (allowInsecure == null) allowInsecure = false;
        if (StrUtil.isBlank(packetEncoding)) packetEncoding = "none";
        if (StrUtil.isBlank(utlsFingerprint)) utlsFingerprint = "";
        if (StrUtil.isBlank(echConfig)) echConfig = "";
        if (StrUtil.isBlank(echDohServer)) echDohServer = "";

        if (StrUtil.isBlank(realityPublicKey)) realityPublicKey = "";
        if (StrUtil.isBlank(realityShortId)) realityShortId = "";
        if (StrUtil.isBlank(realitySpiderX)) realitySpiderX = "";
        if (StrUtil.isBlank(realityFingerprint)) realityFingerprint = "chrome";

        if (hy2DownMbps == null) hy2DownMbps = 0;
        if (hy2UpMbps == null) hy2UpMbps = 0;
        if (StrUtil.isBlank(hy2Password)) hy2Password = "";
        if (StrUtil.isBlank(hy2ObfsPassword)) hy2ObfsPassword = "";

        if (StrUtil.isBlank(mekyaKcpSeed)) mekyaKcpSeed = "";
        if (StrUtil.isBlank(mekyaKcpHeaderType)) mekyaKcpHeaderType = "none";
        if (StrUtil.isBlank(mekyaUrl)) mekyaUrl = "";

        if (mux == null) mux = false;
        if (muxConcurrency == null) muxConcurrency = 8;
        if (StrUtil.isBlank(muxPacketEncoding)) muxPacketEncoding = "none";

    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(22);
        super.serialize(output);

        output.writeString(uuid);
        output.writeString(encryption);
        output.writeString(type);

        switch (type) {
            case "tcp": {
                output.writeString(headerType);
                output.writeString(host);
                output.writeString(path);
                break;
            }
            case "kcp": {
                output.writeString(headerType);
                output.writeString(mKcpSeed);
                break;
            }
            case "ws": {
                output.writeString(host);
                output.writeString(path);
                output.writeInt(wsMaxEarlyData);
                output.writeBoolean(wsUseBrowserForwarder);
                output.writeString(earlyDataHeaderName);
                break;
            }
            case "http", "httpupgrade": {
                output.writeString(host);
                output.writeString(path);
                break;
            }
            case "splithttp": {
                output.writeString(host);
                output.writeString(path);
                output.writeBoolean(shUseBrowserForwarder);
                break;
            }
            case "quic": {
                output.writeString(headerType);
                output.writeString(quicSecurity);
                output.writeString(quicKey);
                break;
            }
            case "grpc": {
                output.writeString(grpcServiceName);
                break;
            }
            case "meek": {
                output.writeString(meekUrl);
                break;
            }
            case "hysteria2": {
                output.writeInt(hy2DownMbps);
                output.writeInt(hy2UpMbps);
                output.writeString(hy2ObfsPassword);
                output.writeString(hy2Password);
                break;
            }
            case "mekya": {
                output.writeString(mekyaKcpHeaderType);
                output.writeString(mekyaKcpSeed);
                output.writeString(mekyaUrl);
                break;
            }
        }

        output.writeString(security);

        switch (security) {
            case "tls": {
                output.writeString(sni);
                output.writeString(alpn);
                output.writeString(certificates);
                output.writeString(pinnedPeerCertificateChainSha256);
                output.writeBoolean(allowInsecure);
                output.writeString(utlsFingerprint);
                output.writeString(echConfig);
                output.writeString(echDohServer);
                break;
            }
            case "reality": {
                output.writeString(sni);
                output.writeString(realityPublicKey);
                output.writeString(realityShortId);
                output.writeString(realitySpiderX);
                output.writeString(realityFingerprint);
                break;
            }
        }

        if (this instanceof VMessBean) {
            output.writeInt(((VMessBean) this).alterId);
            output.writeBoolean(((VMessBean) this).experimentalAuthenticatedLength);
            output.writeBoolean(((VMessBean) this).experimentalNoTerminationSignal);
        }
        if (this instanceof VLESSBean) {
            output.writeString(((VLESSBean) this).flow);
        }

        output.writeString(packetEncoding);

        output.writeBoolean(mux);
        output.writeInt(muxConcurrency);
        output.writeString(muxPacketEncoding);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        encryption = input.readString();
        type = input.readString();

        switch (type) {
            case "tcp": {
                headerType = input.readString();
                host = input.readString();
                path = input.readString();
                break;
            }
            case "kcp": {
                headerType = input.readString();
                mKcpSeed = input.readString();
                break;
            }
            case "ws": {
                host = input.readString();
                path = input.readString();
                wsMaxEarlyData = input.readInt();
                wsUseBrowserForwarder = input.readBoolean();
                if (version >= 2) {
                    earlyDataHeaderName = input.readString();
                }
                break;
            }
            case "http": {
                host = input.readString();
                path = input.readString();
                break;
            }
            case "quic": {
                headerType = input.readString();
                quicSecurity = input.readString();
                quicKey = input.readString();
                if (version >= 16) {
                    break;
                }
            }
            case "grpc": {
                grpcServiceName = input.readString();
                if (version >= 8 && version <= 12) {
                    input.readString(); // grpcMode, removed
                }
                if (version >= 16) {
                    break;
                }
            }
            case "meek": {
                if (version >= 10) {
                    meekUrl = input.readString();
                }
                if (version >= 16) {
                    break;
                }
            }
            case "httpupgrade": {
                if (version >= 12) {
                    host = input.readString();
                    path = input.readString();
                }
                if (version >= 16) {
                    break;
                }
            }
            case "hysteria2": {
                if (version >= 14) {
                    hy2DownMbps = input.readInt();
                    hy2UpMbps = input.readInt();
                    hy2ObfsPassword = input.readString();
                }
                if (version >= 15) {
                    hy2Password = input.readString();
                }
                break;
            }
            case "splithttp": {
                if (version >= 18) {
                    host = input.readString();
                    path = input.readString();
                }
                if (version >= 20) {
                    shUseBrowserForwarder = input.readBoolean();
                }
                break;
            }
            case "mekya": {
                if (version >= 22) {
                    mekyaKcpHeaderType = input.readString();
                    mekyaKcpSeed = input.readString();
                    mekyaUrl = input.readString();
                }
                break;
            }
        }

        security = input.readString();
        switch (security) {
            case "tls": {
                sni = input.readString();
                alpn = input.readString();
                if (version >= 1) {
                    certificates = input.readString();
                    pinnedPeerCertificateChainSha256 = input.readString();
                }
                if (version >= 3) {
                    allowInsecure = input.readBoolean();
                }
                if (version >= 9) {
                    utlsFingerprint = input.readString();
                }
                if (version >= 21) {
                    echConfig = input.readString();
                    echDohServer = input.readString();
                }
                break;
            }
            case "xtls": { // removed, for compatibility
                if (version <= 8) {
                    security = "tls";
                    sni = input.readString();
                    alpn = input.readString();
                    input.readString(); // flow, removed
                }
                if (version >= 16) {
                    break;
                }
            }
            case "reality": {
                if (version >= 11) {
                    sni = input.readString();
                    realityPublicKey = input.readString();
                    realityShortId = input.readString();
                    realitySpiderX = input.readString();
                    realityFingerprint = input.readString();
                }
                break;
            }
        }
        if (this instanceof VMessBean && version != 4 && version < 6) {
            ((VMessBean) this).alterId = input.readInt();
        }
        if (this instanceof VMessBean && version >= 4) {
            if (version >= 17) {
                ((VMessBean) this).alterId = input.readInt();
            }
            ((VMessBean) this).experimentalAuthenticatedLength = input.readBoolean();
            ((VMessBean) this).experimentalNoTerminationSignal = input.readBoolean();
        }
        if (this instanceof VLESSBean && version >= 11) {
            ((VLESSBean) this).flow = input.readString();
        }
        if (version >= 7 && version <= 15) {
            switch (input.readInt()) {
                case 0:
                    packetEncoding = "none";
                    break;
                case 1:
                    packetEncoding = "packet";
                    break;
                case 2:
                    packetEncoding = "xudp";
                    break;
            }
        }
        if (version >= 16) {
            packetEncoding = input.readString();
        }
        if (version >= 19) {
            mux = input.readBoolean();
            muxConcurrency = input.readInt();
            muxPacketEncoding = input.readString();
        }
    }

    @Override
    public boolean canTCPing() {
        List<String> alpns = NetsKt.listByLineOrComma(alpn);
        return !type.equals("kcp") && !type.equals("quic") && !type.equals("hysteria2")
                && !(type.equals("splithttp") && alpns.size() == 1 && Objects.equals(alpns.get(0), "h3"));
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof StandardV2RayBean bean)) return;
        if (allowInsecure) {
            bean.allowInsecure = true;
        }
        bean.wsMaxEarlyData = wsMaxEarlyData;
        bean.earlyDataHeaderName = earlyDataHeaderName;
        bean.wsUseBrowserForwarder = wsUseBrowserForwarder;
        bean.shUseBrowserForwarder = shUseBrowserForwarder;
        bean.certificates = certificates;
        bean.pinnedPeerCertificateChainSha256 = pinnedPeerCertificateChainSha256;
        bean.packetEncoding = packetEncoding;
        bean.utlsFingerprint = utlsFingerprint;
        bean.echConfig = echConfig;
        bean.echDohServer = echDohServer;
        // bean.realityFingerprint = realityFingerprint; // fuck RPRX's disgusting "fp"
        bean.hy2DownMbps = hy2DownMbps;
        bean.hy2UpMbps = hy2UpMbps;
        bean.mux = mux;
        bean.muxConcurrency = muxConcurrency;
        bean.muxPacketEncoding = muxPacketEncoding;
    }

    public String uuidOrGenerate() {
        try {
            return UUID.fromString(uuid).toString(false);
        } catch (Exception ignored) {
            return UUIDsKt.uuid5(uuid);
        }
    }

}