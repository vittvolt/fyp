package com.dji.FPVDemo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import dji.sdk.api.Camera.DJICameraDecodeTypeDef;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef;
import dji.sdk.api.DJIDrone;
import dji.sdk.api.DJIError;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef.CameraCaptureMode;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef.CameraMode;
import dji.sdk.api.DJIDroneTypeDef.DJIDroneType;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef;
import dji.sdk.interfaces.DJIExecuteResultCallback;
import dji.sdk.interfaces.DJIGeneralListener;
import dji.sdk.interfaces.DJIGroundStationExecuteCallBack;
import dji.sdk.interfaces.DJIReceivedVideoDataCallBack;
import dji.sdk.widget.DjiGLSurfaceView;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import dji.sdk.api.GroundStation.DJIGroundStation;
import dji.sdk.api.MainController.DJIMainController;
import android.content.Context;

public class FPVActivity extends DemoBaseActivity implements OnClickListener, SurfaceHolder.Callback{

    private static final String TAG = "FPVActivity";
    private int DroneCode;
    private final int SHOWDIALOG = 1;
    private final int SHOWTOAST = 2;
    private final int STOP_RECORDING = 10;
    private Button captureAction, recordAction, captureMode, TakeOff, Landing, Left, openGndStation, Test;
    private TextView viewTimer;
    private int i = 0;
    private int TIME = 1000;
    private DjiGLSurfaceView mDjiGLSurfaceView;
    private DJIReceivedVideoDataCallBack mReceivedVideoDataCallBack = null;

    boolean status = false;
    private int tt = 1;

    // New variables
    private boolean gndStation = false;
    private boolean left_spin = false;
    private int test = 0;
    private int test01 = 0;
    private String tests = "";
    private Surface mSurface;
    private SurfaceHolder mSurfaceHolder;
    private boolean SurfaceValid = false;
    //Decoder Initialization
    private int width = 640;
    private int height = 480;
    private String videoFormat = "video/avc";
    private MediaFormat format = MediaFormat.createVideoFormat(videoFormat, width, height);
    private MediaCodec mCodec;

    //Split the NAL units
    protected ArrayList<byte []> splitNALunits(byte[] vBuffer){
        ArrayList<byte []> NAL_units = new ArrayList<>();
        int start=0;
        for (int i=0;i<vBuffer.length;i++){
            if (vBuffer[i] == 0x00 && vBuffer[i+1] == 0x00 && vBuffer[i+2] == 0x01 && vBuffer[i+3] == 0x09){
                if (start != 0)
                    NAL_units.add(Arrays.copyOfRange(vBuffer, start, i - 1));
                start=i+4;
            }
            if (i == vBuffer.length-1)
                NAL_units.add(Arrays.copyOfRange(vBuffer, start, i));
        }
        return NAL_units;
    }

    //Convert the bytes to a string
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    // Write to file
    public void generateNoteOnSD(String sFileName, String sBody){
        try
        {
            File root = new File(Environment.getExternalStorageDirectory(), "Notes");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, sFileName);
            FileWriter writer = new FileWriter(gpxfile, true);
            writer.append(sBody);
            writer.flush();
            writer.close();
            handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Saved!"));
        } catch(IOException e)
        {
            e.printStackTrace();
        }
    }

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
    /*Runnable runnable = new Runnable(){
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
    }; */

    // Create menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

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
                                handler.sendMessage(handler.obtainMessage(SHOWTOAST, DJIError.getCheckPermissionErrorDescription(result)));
                                status = true;
                            } else {
                                // show errors
                                Log.e(TAG, "onGetPermissionResult =" + result);
                                Log.e(TAG,
                                        "onGetPermissionResultDescription=" + DJIError.getCheckPermissionErrorDescription(result));
                                handler.sendMessage(handler.obtainMessage(SHOWTOAST, getString(R.string.demo_activation_error) + DJIError.getCheckPermissionErrorDescription(result) + "\n" + getString(R.string.demo_activation_error_code) + result));
                            }
                        }
                    });
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }.start();
        while (status == false){
            tt=1-tt;
        }
        onInitSDK(DroneCode);
        DJIDrone.connectToDrone();
        //handler.sendMessage(handler.obtainMessage(SHOWDIALOG, "Haha!"));
        // Try to initialize the camera to capture mode
        DJIDrone.getDjiCamera().setCameraMode(CameraMode.Camera_Record_Mode, new DJIExecuteResultCallback() {

            @Override
            public void onResult(DJIError mErr) {

                String result = "errorCode =" + mErr.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                if (mErr.errorCode != DJIError.RESULT_OK) {
                    handler.sendMessage(handler.obtainMessage(SHOWDIALOG, result));
                    // Show the error when setting fails
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWDIALOG, "Camera Init Success!"));
                }

            }

        });

        mDjiGLSurfaceView = (DjiGLSurfaceView)findViewById(R.id.DjiSurfaceView_02);
        //SDK V2.4 updated
        DJIDrone.getDjiCamera().setDecodeType(DJICameraDecodeTypeDef.DecoderType.Software);
        //mDjiGLSurfaceView.start();

        mDjiGLSurfaceView.getHolder().addCallback(this);

        viewTimer = (TextView) findViewById(R.id.timer);
        captureAction = (Button) findViewById(R.id.button1);
        recordAction = (Button) findViewById(R.id.button2);
        captureMode = (Button) findViewById(R.id.button3);

        //Set for the new buttons
        TakeOff = (Button) findViewById(R.id.take_off);
        Landing = (Button) findViewById(R.id.landing);
        openGndStation = (Button) findViewById(R.id.openGndStation);
        Test = (Button) findViewById(R.id.test);

        Left = (Button) findViewById(R.id.left);
        Left.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    //handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Down!"));
                    left_spin = true;
                    new Thread() {
                        public void run() {
                            DJIDrone.getDjiGroundStation().sendFlightControlData(20, 0, 0, 0, new DJIExecuteResultCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError.errorCode != DJIError.RESULT_OK && djiError.errorCode != DJIError.RESULT_SUCCEED) {
                                        String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                                        handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                                    }
                                }
                            });
                            if (left_spin && gndStation) {
                                handlerTimer.postDelayed(this, 1000);
                            }
                        }
                    }.start();
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    //handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Up!"));
                    left_spin = false;
                }
                return false;
            }
        });

        captureAction.setOnClickListener(this);
        recordAction.setOnClickListener(this);
        captureMode.setOnClickListener(this);

        TakeOff.setOnClickListener(this);
        Landing.setOnClickListener(this);
        openGndStation.setOnClickListener(this);
        Test.setOnClickListener(this);

        /*tests = Environment.getExternalStorageState();
        handler.sendMessage(handler.obtainMessage(SHOWTOAST, tests)); */
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
            case R.id.take_off:{
                TakeOff();
                break;
            }
            case R.id.landing:{
                Landing();
                break;
            }
            case R.id.openGndStation:{
                OpenGndStation();
                break;
            }
            case R.id.test:{
                left_spin = false;
                break;
            }
            default:
                break;
        }
    }

    // Open GroundStation
    private void OpenGndStation(){
        DJIDrone.getDjiGroundStation().openGroundStation(new DJIGroundStationExecuteCallBack() {
            @Override
            public void onResult(DJIGroundStationTypeDef.GroundStationResult result) {
                // TODO Auto-generated method stub
                String ResultsString = "return code =" + result.toString();
                handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));

                if (result == DJIGroundStationTypeDef.GroundStationResult.GS_Result_Success) {
                    gndStation = true;
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "GndStation Init Successful!"));
                    //Setup the mode
                    DJIDrone.getDjiGroundStation().setHorizontalControlCoordinateSystem(DJIGroundStationTypeDef.DJINavigationFlightControlCoordinateSystem.Navigation_Flight_Control_Coordinate_System_Body);
                    DJIDrone.getDjiGroundStation().setHorizontalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlHorizontalControlMode.Navigation_Flight_Control_Horizontal_Control_Angle);
                    DJIDrone.getDjiGroundStation().setVerticalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlVerticalControlMode.Navigation_Flight_Control_Vertical_Control_Velocity);
                    DJIDrone.getDjiGroundStation().setYawControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Angle);
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "GndStation Init Fail!"));
                }
            }
        });
    }

    //Take off function

    private void TakeOff(){
        DJIDrone.getDjiMainController().startTakeoff(new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                if (djiError.errorCode == DJIError.RESULT_OK) {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Take off successful!"));
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                }
            }
        });
    }

    //Landing function
    private void Landing(){
        DJIDrone.getDjiMainController().startLanding(new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                if (djiError.errorCode == DJIError.RESULT_OK) {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Landing successful!"));
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                }
            }
        });
    }


    private void captureAction(){

        CameraMode cameraMode = CameraMode.Camera_Capture_Mode;
        // Set the cameraMode as Camera_Capture_Mode. All the available modes can be seen in
        // DJICameraSettingsTypeDef.java
        DJIDrone.getDjiCamera().setCameraMode(cameraMode, new DJIExecuteResultCallback() {

            @Override
            public void onResult(DJIError mErr) {

                String result = "errorCode =" + mErr.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                if (mErr.errorCode == DJIError.RESULT_OK) {
                    CameraCaptureMode photoMode = CameraCaptureMode.Camera_Single_Capture;
                    // Set the camera capture mode as Camera_Single_Capture. All the available modes
                    // can be seen in DJICameraSettingsTypeDef.java

                    DJIDrone.getDjiCamera().startTakePhoto(photoMode, new DJIExecuteResultCallback() {

                        @Override
                        public void onResult(DJIError mErr) {

                            String result = "errorCode =" + mErr.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
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
        DJIDrone.getDjiCamera().setCameraMode(cameraMode, new DJIExecuteResultCallback() {

            @Override
            public void onResult(DJIError mErr) {

                String result = "errorCode =" + mErr.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                if (mErr.errorCode == DJIError.RESULT_OK) {

                    //Call the startRecord API
                    DJIDrone.getDjiCamera().startRecord(new DJIExecuteResultCallback() {

                        @Override
                        public void onResult(DJIError mErr) {

                            String result = "errorCode =" + mErr.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
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

    //Implement the SurfaceHolder Callback
    public void surfaceCreated(SurfaceHolder holder){
        Log.e(TAG, "Surface Created!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        SurfaceValid = true;
        format.setString("KEY_MIME", videoFormat);

        try
        {
            mCodec = MediaCodec.createDecoderByType(videoFormat);
            mCodec.configure(format, mDjiGLSurfaceView.getHolder().getSurface(), null, 0 );

            mCodec.start();
            mReceivedVideoDataCallBack = new DJIReceivedVideoDataCallBack(){
                private int packetLength = 0;
                private ByteBuffer accessUnitBuffer = ByteBuffer.allocate(50000);
                int inIndex;
                int presentationTime = 0;

                @Override
                public void onResult(byte[] videoBuffer, int size){
                    if (SurfaceValid){
                        ArrayList<byte []> NAL_Units = splitNALunits(videoBuffer );
                        for( int i=0; i< NAL_Units.size(); i++ ) {
                            // Send off the current buffer of data (Access Unit)
                            inIndex = mCodec.dequeueInputBuffer(0);
                            if (inIndex >= 0) {
                                ByteBuffer inputBuffer = mCodec.getInputBuffer(inIndex);
                                inputBuffer.put(accessUnitBuffer.array(), 0, packetLength);
                                mCodec.queueInputBuffer(inIndex, 0, packetLength, presentationTime, 0);
                                presentationTime += 100;
                                packetLength = 0;
                                accessUnitBuffer.clear();
                                accessUnitBuffer.rewind();
                            }
                            accessUnitBuffer.put(NAL_Units.get(i));
                            packetLength += NAL_Units.get(i).length;
                        }
                    }
                /*mDjiGLSurfaceView.setDataToDecoder(videoBuffer, size);
                if (test01 < 5) {
                    if (test == 500) {
                        test01++;
                        test = 0;
                        tests = bytesToHex(videoBuffer);
                        tests = "Bytes: \n" + tests + "\n";
                        handler.sendMessage(handler.obtainMessage(SHOWTOAST, tests));
                        generateNoteOnSD("NALs.txt", tests);
                    } else {
                        test++;
                    }
                } */
                }
            };
            DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(mReceivedVideoDataCallBack);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h){

    }

    public void surfaceDestroyed(SurfaceHolder holder){
        SurfaceValid = false;

        if (DJIDrone.getDjiCamera() != null) {
            DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(null);
        }
        mDjiGLSurfaceView.destroy();
    }
}
