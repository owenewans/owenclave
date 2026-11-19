package io.nekohasekai.sagernet.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface ISagerNetService extends IInterface {
    int getState() throws RemoteException;
    String getProfileName() throws RemoteException;
    void registerCallback(ISagerNetServiceCallback cb) throws RemoteException;
    void startListeningForBandwidth(ISagerNetServiceCallback cb, long timeout) throws RemoteException;
    void stopListeningForBandwidth(ISagerNetServiceCallback cb) throws RemoteException;
    void startListeningForStats(ISagerNetServiceCallback cb, long timeout) throws RemoteException;
    void stopListeningForStats(ISagerNetServiceCallback cb) throws RemoteException;
    void unregisterCallback(ISagerNetServiceCallback cb) throws RemoteException;
    void protect(int fd) throws RemoteException;
    int urlTest() throws RemoteException;
    void resetTrafficStats() throws RemoteException;
    boolean getTrafficStatsEnabled() throws RemoteException;

    abstract class Stub extends Binder implements ISagerNetService {
        @Override
        public IBinder asBinder() {
            return this;
        }

        public static ISagerNetService asInterface(IBinder obj) {
            if (obj instanceof ISagerNetService) {
                return (ISagerNetService) obj;
            }
            return null;
        }
    }
}

