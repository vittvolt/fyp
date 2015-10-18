package com.dji.FPVDemo;

import java.util.Timer;
import java.util.TimerTask;
import dji.sdk.api.DJIDrone;
import dji.sdk.api.DJIError;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef.CameraCaptureMode;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef.CameraMode;
import dji.sdk.api.DJIDroneTypeDef.DJIDroneType;
import dji.sdk.interfaces.DJIExecuteResultCallback;
import dji.sdk.interfaces.DJIGeneralListener;
import dji.sdk.interfaces.DJIReceivedVideoDataCallBack;
import dji.sdk.widget.DjiGLSurfaceView;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class FPVActivity extends DemoBaseActivity implements OnClickListener{

    private static final String TAG = "FPVActivity";
    private int DroneCode;
    private final int SHOWDIALOG = 1;
    private final int SHOWTOAST = 2;
    private final int STOP_RECORDING = 10;
    private Button captureAction, recordAction, captureMode;
    private TextView viewTimer;
    private int i = 0;
    private int TIME = 1000;
    private DjiGLSurfaceView mDjiGLSurfaceView;
    private DJIReceivedVideoDataCallBack mReceivedVideoDataCallBack = null;

    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case SHOWDIALOG:
                    showMessage(getString(R.string.demo_activation_message_title),(String)msg.obj);
                    break;
                case SHOWTOAST:
                    Toast.makeText(FPVActivity.this, (String)msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
            return false;
        }
    });

    private Handler handlerTimer = new Handler();
    Runnable runnable = new Runnable(){
        @Override
        public void run() {
            // handler自带方法实现定时器
            try {

                handlerTimer.postDelayed(this, TIME);
                viewTimer.setText(Integer.toString(i++));

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fpv);

        DroneCode = 2;

        new Thread(){
            public void run(){
                try{
                    DJIDrone.checkPermission(getApplicationContext(), new DJIGeneralListener() {
                        @Override
                        public void onGetPermissionResult(int result) {
                            if (result == 0) {
                                // show success
                                Log.e(TAG, "onGetPermissionResult =" + result);
                                Log.e(TAG,
                                        "onGetPermissionResultDescription=" + DJIError.getCheckPermissionErrorDescription(result));
                            } else {
                                // show errors
                                Log.e(TAG, "onGetPermissionResult =" + result);
                                Log.e(TAG,
                                        "onGetPermissionResultDescription=" + DJIError.getCheckPermissionErrorDescription(result));
                            }
                        }
                    });
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }.start();

        onInitSDK(DroneCode);
        DJIDrone.connectToDrone();

        mDjiGLSurfaceView = (DjiGLSurfaceView)findViewById(R.id.DjiSurfaceView_02);
        mDjiGLSurfaceView.start();

        mReceivedVideoDataCallBack = new DJIReceivedVideoDataCallBack(){
            @Override
            public void onResult(byte[] videoBuffer, int size){
                mDjiGLSurfaceView.setDataToDecoder(videoBuffer, size);
            }
        };
        DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(mReceivedVideoDataCallBack);

        viewTimer = (TextView) findViewById(R.id.timer);
        captureAction = (Button) findViewById(R.id.button1);
        recordAction = (Button) findViewById(R.id.button2);
        captureMode = (Button) findViewById(R.id.button3);

        captureAction.setOnClickListener(this);
        recordAction.setOnClickListener(this);
        captureMode.setOnClickListener(this);
    }

    private void onInitSDK(int type){
        switch(type){
            case 0: {
                DJIDrone.initWithType(this.getApplicationContext(), DJIDroneType.DJIDrone_Vision);
                // The SDK initiation for Phantom 2 Vision or Vision Plus
                break;
            }
            case 1: {
                DJIDrone.initWithType(this.getApplicationContext(), DJIDroneType.DJIDrone_Inspire1);
                // The SDK initiation for Inspire 1 or Phantom 3 Professional.
                break;
            }
            case 2: {
                DJIDrone.initWithType(this.getApplicationContext(), DJIDroneType.DJIDrone_Phantom3_Advanced);
                // The SDK initiation for Phantom 3 Advanced
                break;
            }
            case 3: {
                DJIDrone.initWithType(this.getApplicationContext(), DJIDroneType.DJIDrone_M100);
                // The SDK initiation for Matrice 100.
                break;
            }
            default:{
                break;
            }
        }
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch (v.getId()) {
            case R.id.button1:{
                captureAction();
                break;
            }
            case R.id.button2:{
                recordAction();
                break;
            }
            case R.id.button3:{
                stopRecord();
                break;
            }
            default:
                break;
        }
    }


    private void captureAction(){

        CameraMode cameraMode = CameraMode.Camera_Capture_Mode;
        // Set the cameraMode as Camera_Capture_Mode. All the available modes can be seen in
        // DJICameraSettingsTypeDef.java
        DJIDrone.getDjiCamera().setCameraMode(cameraMode, new DJIExecuteResultCallback(){

            @Override
            public void onResult(DJIError mErr)
            {

                String result = "errorCode =" + mErr.errorCode + "\n"+"errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                if (mErr.errorCode == DJIError.RESULT_OK) {
                    CameraCaptureMode photoMode = CameraCaptureMode.Camera_Single_Capture;
                    // Set the camera capture mode as Camera_Single_Capture. All the available modes
                    // can be seen in DJICameraSettingsTypeDef.java

                    DJIDrone.getDjiCamera().startTakePhoto(photoMode, new DJIExecuteResultCallback(){

                        @Override
                        public void onResult(DJIError mErr)
                        {

                            String result = "errorCode =" + mErr.errorCode + "\n"+"errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                            handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));  // display the returned message in the callback
                        }

                    }); // Execute the startTakePhoto API if successfully setting the camera mode as
                    // Camera_Capture_Mode
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                    // Show the error when setting fails
                }

            }

        });

    }

    private void recordAction(){
        // Set the cameraMode as Camera_Record_Mode.
        CameraMode cameraMode = CameraMode.Camera_Record_Mode;
        DJIDrone.getDjiCamera().setCameraMode(cameraMode, new DJIExecuteResultCallback(){

            @Override
            public void onResult(DJIError mErr)
            {

                String result = "errorCode =" + mErr.errorCode + "\n"+"errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                if (mErr.errorCode == DJIError.RESULT_OK) {

                    //Call the startRecord API
                    DJIDrone.getDjiCamera().startRecord(new DJIExecuteResultCallback(){

                        @Override
                        public void onResult(DJIError mErr)
                        {

                            String result = "errorCode =" + mErr.errorCode + "\n"+"errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                            handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));  // display the returned message in the callback

                        }

                    }); // Execute the startTakePhoto API
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                }

            }

        });

    }

    private void stopRecord(){
        // Call the API
        DJIDrone.getDjiCamera().stopRecord(new DJIExecuteResultCallback() {

            @Override
            public void onResult(DJIError mErr) {

                String result = "errorCode =" + mErr.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));

            }

        });
    }

    public void showMessage(String title, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub

        super.onResume();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub

        super.onPause();
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        if (DJIDrone.getDjiCamera() != null) {
            DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(null);
        }
        mDjiGLSurfaceView.destroy();
        super.onDestroy();
        Process.killProcess(Process.myPid());
    }
}
