package io.nekohasekai.sagernet.fmt.olcrtc;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class OLCRTCBean extends AbstractBean {

    public String authProvider;
    public String transport;
    public String roomId;
    public String encryptionKey;
    public String dnsServer;
    public String socksHost;
    public Integer socksPort;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (authProvider == null) authProvider = "jitsi";
        if (transport == null) transport = "datachannel";
        if (roomId == null) roomId = "";
        if (encryptionKey == null) encryptionKey = "";
        if (dnsServer == null) dnsServer = "8.8.8.8:53";
        if (socksHost == null) socksHost = "127.0.0.1";
        if (socksPort == null) socksPort = 8808;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(authProvider);
        output.writeString(transport);
        output.writeString(roomId);
        output.writeString(encryptionKey);
        output.writeString(dnsServer);
        output.writeString(socksHost);
        output.writeInt(socksPort);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        authProvider = input.readString();
        transport = input.readString();
        roomId = input.readString();
        encryptionKey = input.readString();
        dnsServer = input.readString();
        socksHost = input.readString();
        socksPort = input.readInt();
    }

    @Override
    public String displayName() {
        if (name != null && !name.isEmpty()) return name;
        return "olcrtc/" + authProvider;
    }

    @Override
    public String displayAddress() {
        return authProvider + " | " + transport + " | " + roomId;
    }

    @NotNull
    @Override
    public OLCRTCBean clone() {
        return KryoConverters.deserialize(new OLCRTCBean(), KryoConverters.serialize(this));
    }

    public static final Creator<OLCRTCBean> CREATOR = new CREATOR<>() {
        @NonNull
        @Override
        public OLCRTCBean newInstance() {
            return new OLCRTCBean();
        }

        @Override
        public OLCRTCBean[] newArray(int size) {
            return new OLCRTCBean[size];
        }
    };
}
