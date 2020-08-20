package xyz.oboloi.openvpn;


import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleObserver;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.App;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.LogItem;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.OpenVPNStatusService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;


public class OboloiVPN implements VpnStatus.ByteCountListener, VpnStatus.StateListener,VpnStatus.LogListener {

    //    final OboloiVPN ourInstance = new OboloiVPN();
    private static IOpenVPNServiceInternal mService;
    private ProfileAsync profileAsync;

    private boolean profileStatus;

    private Context context;
    private Activity activity;
    private OnVPNStatusChangeListener listener;

    private boolean value;
    private String expireDate;

    public void setOnVPNStatusChangeListener(OnVPNStatusChangeListener listener)
    {
        this.listener = listener;
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IOpenVPNServiceInternal.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }
    };


    public OboloiVPN(Context context,Activity activity) {
        this.context = context;
        this.activity = activity;
    }


    public void launchVPN(String config,String expireDate) {

        this.expireDate = expireDate;
        if (!App.isStart) {
            DataCleanManager.cleanApplicationData(context);
            setProfileLoadStatus(false);
            profileAsync = new ProfileAsync(context, new ProfileAsync.OnProfileLoadListener() {
                @Override
                public void onProfileLoadSuccess() {
                   setProfileLoadStatus(true);
                }

                @Override
                public void onProfileLoadFailed(String msg) {

                    Toast.makeText(context, context.getString(R.string.init_fail) + msg, Toast.LENGTH_SHORT).show();
                }
            }, config);
            profileAsync.execute();
        }

    }


    public void init() {


        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!App.isStart) {
                    startVPN();
                    App.isStart = true;

                } else {
                    stopVPN();
                    App.isStart = false;

                }
            }
        };
        r.run();
    }

    public void onStop() {
        VpnStatus.removeStateListener(this);
        VpnStatus.removeByteCountListener(this);
        VpnStatus.removeLogListener(this);
    }

    public void onResume() {
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);
        VpnStatus.addLogListener(this);
        //Intent intent = new Intent(activity, OpenVPNService.class);
        //intent.setAction(OpenVPNService.START_SERVICE);
        //activity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void onPause() {
        context.unbindService(mConnection);
    }

    public void cleanup() {
        if (profileAsync != null && !profileAsync.isCancelled()) {
            profileAsync.cancel(true);
        }
    }

    private void startVPN() {
        try {
            onResume();
            ProfileManager pm = ProfileManager.getInstance(context);
            VpnProfile profile = pm.getProfileByName(Build.MODEL);
            startVPNConnection(profile);
        } catch (Exception ex) {
            App.isStart = false;

        }
    }

    private void stopVPN() {
        stopVPNConnection();
        setVPNStatus(false);
        //btnConnect.setText(getString(R.string.connect));

    }

    public boolean getVPNStatus() {
        return value;
    }

    public boolean getProfileStatus(){
        return profileStatus;
    }


    private void setVPNStatus(boolean value)
    {
        this.value = value;


    }

    private void setProfileLoadStatus(boolean profileStatus){
        this.profileStatus = profileStatus;

        if(listener != null){
            listener.onProfileLoaded(profileStatus);
        }
    }


    // ------------- Functions Related to OpenVPN-------------
    private void startVPNConnection(VpnProfile vp) {
        Intent intent = new Intent(context, LaunchVPN.class);
        intent.putExtra(LaunchVPN.EXTRA_KEY, vp.getUUID().toString());
        if(expireDate != null) {
            intent.putExtra(LaunchVPN.EXTRA_EXPRE_DATE, expireDate);
        }
        intent.setAction(Intent.ACTION_MAIN);
        context.startActivity(intent);
    }

    private void stopVPNConnection() {

        ProfileManager.setConnectedVpnProfileDisconnected(context);
        if (mService != null) {
            try {
                Log.d("stopping vpn" , "normal way");
                mService.stopVPN(false);
                onStop();
            } catch (RemoteException e) {
                Log.e("exception lvl 1" , e.toString());
                Log.e("got error" , "attempting the hard way");
                fuckVpn();
                VpnStatus.logException(e);
            }
        }else{
            Log.e("mService is null" , "attempting the hard way");
            fuckVpn();
        }
    }

    private void fuckVpn() {
        OpenVPNService.destroy = true;
        if(listener != null) listener.onVPNStatusChanged("NOPROCESS");
        onStop();
    }

    public String getServiceStatus() {
        return null;
    }


    @Override
    public void updateState(final String state, String logmessage, int localizedResId, ConnectionStatus level) {
        activity.runOnUiThread (new Runnable() {
            @Override
            public void run() {
                if(listener != null)
                {
                    listener.onVPNStatusChanged(state);
                }
                if(state.equals("DISCONNECTED")){
                    OpenVPNService.setDefaultStatus();
                }
                if (state.equals("CONNECTED")) {

                    App.isStart = true;
                    setVPNStatus(true);
                }

                if (state.equals("AUTH_FAILED")) {
                    Toast.makeText(context, "Wrong Username or Password!", Toast.LENGTH_SHORT).show();
                    setVPNStatus(false);
                }


            }
        });
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {

    }


    @Override
    public void newLog(LogItem logItem) {

    }
}



