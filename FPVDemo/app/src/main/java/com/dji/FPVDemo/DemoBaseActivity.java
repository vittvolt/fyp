package com.dji.FPVDemo;

import android.app.Activity;
import dji.midware.data.manager.P3.ServiceManager;

public class DemoBaseActivity extends Activity {
    @Override
    protected void onResume(){
        super.onResume();
        ServiceManager.getInstance().pauseService(false); // Resume the service
    }

    @Override
    protected void onPause() {
        super.onPause();
        ServiceManager.getInstance().pauseService(true); // Pause the service
    }
}
