package io.nekohasekai.sagernet.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface ISagerNetServiceCallback extends IInterface {
    void stateChanged(int state, String profileName, String msg) throws RemoteException;
    void trafficUpdated(long profileId, TrafficStats stats, boolean isCurrent) throws RemoteException;
    void statsUpdated(AppStatsList statsList) throws RemoteException;
    void observatoryResultsUpdated(long groupId) throws RemoteException;
    void profilePersisted(long profileId) throws RemoteException;
    void missingPlugin(String profileName, String pluginName) throws RemoteException;
    void routeAlert(int type, String routeName) throws RemoteException;

    abstract class Stub extends Binder implements ISagerNetServiceCallback {
        @Override
        public IBinder asBinder() {
            return this;
        }

        public static ISagerNetServiceCallback asInterface(IBinder obj) {
            if (obj instanceof ISagerNetServiceCallback) {
                return (ISagerNetServiceCallback) obj;
            }
            return null;
        }
    }
}

