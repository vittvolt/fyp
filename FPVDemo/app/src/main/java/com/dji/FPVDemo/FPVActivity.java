package com.dji.FPVDemo;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLSurfaceView;
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
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import dji.sdk.api.GroundStation.DJIGroundStation;
import dji.sdk.api.MainController.DJIMainController;
import android.content.Context;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class FPVActivity extends DemoBaseActivity implements View.OnTouchListener, OnClickListener, TextureView.SurfaceTextureListener{

    //Color blob detection variables
    private boolean              mIsColorSelected = false;
    private Mat mRgba;
    private Scalar mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;

    static{
        System.loadLibrary("opencv_java3");
    }

    private static final String TAG = "FPVActivity";
    private int DroneCode;
    private final int SHOWDIALOG = 1;
    private final int SHOWTOAST = 2;
    private final int STOP_RECORDING = 10;
    private Button captureAction, recordAction, captureMode, TakeOff, Landing, Left, Right, Forward, Backward, openGndStation, Test;
    private TextView viewTimer;
    private int i = 0;
    private int TIME = 1000;
    private TextureView mDjiGLSurfaceView, TextureView_Display;
    private DJIReceivedVideoDataCallBack mReceivedVideoDataCallBack = null;

    // New variables
    int step_count = 0;
    private boolean gndStation = false;
    private boolean left_spin = false;
    private boolean right_spin = false;
    private boolean move_forward = false;
    private boolean move_backward = false;
    private boolean seq_start = false;
    public byte[] iframe;
    public byte[] SEI = new byte[36];
    public byte[] SPS_PPS = new byte[59];
    public byte[] SPS = new byte[51];
    public byte[] PPS = new byte[8];
    byte[] IDR = new byte[680];
    int k = 0;
    //byte[] init_frame = new byte[775];
    boolean sps_ready = false;

    //Decoder Initialization
    private int width = 960;    //800
    private int height = 540;   //600
    private String videoFormat = "video/avc";
    private MediaFormat format = MediaFormat.createVideoFormat(videoFormat, width, height);
    private MediaCodec mCodec;

    //Split the NAL units
    protected ArrayList<byte []> splitNALunits(byte[] vBuffer, int size){
        ArrayList<byte []> NAL_units = new ArrayList<>();
        if (size <= 6)
            return NAL_units;
        int start = 0;

        if (vBuffer[0] == 0x00 && vBuffer[1] == 0x00 && vBuffer[2] == 0x00 && vBuffer[4] == 0x09){
            NAL_units.add(Arrays.copyOfRange(vBuffer,0,size));
            return NAL_units;
        }
        else if (vBuffer[size-1] == 0x10 && vBuffer[size-2] == 0x09 && vBuffer[size-3] == 0x01 && vBuffer[size-4] == 0x00){
            NAL_units.add(Arrays.copyOfRange(vBuffer,0,size-6));
            NAL_units.add(Arrays.copyOfRange(vBuffer,size-6,size));
            return NAL_units;
        }
        else {
            for (int i = 1; i < size; i++) {
                //Todo:  size - 6
                if (i >= size - 6) {
                    NAL_units.add(Arrays.copyOfRange(vBuffer, start, size));
                    return NAL_units;
                }

                if (vBuffer[i] == 0x00 && vBuffer[i+1] == 0x00 && vBuffer[i+2] == 0x00 && vBuffer[i+4] == 0x09){
                    NAL_units.add(Arrays.copyOfRange(vBuffer,0,i));
                    NAL_units.add(Arrays.copyOfRange(vBuffer,i,size));
                    return NAL_units;
                }
            }
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
                                //handler.sendMessage(handler.obtainMessage(SHOWDIALOG, "onGetPermissionResult =" + result + DJIError.getCheckPermissionErrorDescription(result)));
                                //connection = true;
                            } else {
                                // show errors
                                Log.e(TAG, "onGetPermissionResult =" + result);
                                Log.e(TAG,
                                        "onGetPermissionResultDescription=" + DJIError.getCheckPermissionErrorDescription(result));
                                handler.sendMessage(handler.obtainMessage(SHOWDIALOG, getString(R.string.demo_activation_error) + DJIError.getCheckPermissionErrorDescription(result) + "\n" + getString(R.string.demo_activation_error_code) + result));
                            }
                        }
                    });
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }.start();
        //while (!connection){}

        //Read the I-Frame file
        Read_IFrame();

        format.setString("KEY_MIME", videoFormat);
        //format.setByteBuffer("csd-0", ByteBuffer.wrap(SPS));
        //format.setByteBuffer("csd-1", ByteBuffer.wrap(PPS));

        //Configure the surface
        mDjiGLSurfaceView = (TextureView)findViewById(R.id.DjiSurfaceView_);
        TextureView_Display = (TextureView) findViewById(R.id.view02);
        TextureView_Display.setOnTouchListener(this);
        //SDK V2.4 updated
        //mDjiGLSurfaceView.start();

        onInitSDK(DroneCode);
        DJIDrone.connectToDrone();

        DJIDrone.getDjiCamera().setDecodeType(DJICameraDecodeTypeDef.DecoderType.Software);

        Set_Camera_Exposure_Mode();

        mDjiGLSurfaceView.setSurfaceTextureListener(this);

        // Try to initialize the camera to capture mode
        /*DJIDrone.getDjiCamera().setCameraMode(CameraMode.Camera_Capture_Mode, new DJIExecuteResultCallback() {

            @Override
            public void onResult(DJIError mErr) {

                String result = "errorCode =" + mErr.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(mErr.errorCode);
                if (mErr.errorCode != DJIError.RESULT_OK) {
                    handler.sendMessage(handler.obtainMessage(SHOWDIALOG, result));
                    // Show the error when setting fails
                } else {
                    //handler.sendMessage(handler.obtainMessage(SHOWDIALOG, "Camera Init Success!"));
                }

            }

        }); */

        UI_Initialization();

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
                gndStation = false;
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
                    DJIDrone.getDjiGroundStation().setYawControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Palstance);
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
        //mDjiGLSurfaceView.destroy();
        super.onDestroy();
        Process.killProcess(Process.myPid());
    }

    //Implement the SurfaceHolder Callback
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "Surface Created!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        //mDjiGLSurfaceView.start();

        //Detector Init
        mRgba = new Mat();
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);

        try
        {
            mCodec = MediaCodec.createDecoderByType(videoFormat);
            mCodec.configure(format, new Surface(surface), null, 0);
            mCodec.start();

            new Thread() {
                public void run() {
                    //Todo: feed the SEI 0x06 unit
                    /*int Index = mCodec.dequeueInputBuffer(0);
                    while (Index < 0){
                        Index = mCodec.dequeueInputBuffer(0);
                    }

                    ByteBuffer b = mCodec.getInputBuffer(Index);
                    b.put(SEI, 0, SEI.length);
                    mCodec.queueInputBuffer(Index, 0, SEI.length, 0, 0);
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "SEI unit queued!!!"));

                    //Todo: Feed the decoder with IDR frames from the file
                    Index = mCodec.dequeueInputBuffer(0);
                    while(Index < 0){
                        Index = mCodec.dequeueInputBuffer(0);
                    }
                    b = mCodec.getInputBuffer(Index);
                    b.put(IDR, 0, IDR.length);
                    mCodec.queueInputBuffer(Index, 0, IDR.length, 0, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Key frame queued!!!"));  */

                    //Todo: feed the init frame
                    int ind = mCodec.dequeueInputBuffer(0);
                    while ( ind < 0 )
                        ind = mCodec.dequeueInputBuffer(0);
                    ByteBuffer b = mCodec.getInputBuffer(ind);
                    b.put(iframe, 0, iframe.length);
                    mCodec.queueInputBuffer(ind, 0, iframe.length, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "I-frame queued!!!"));

                    sps_ready = true;
                }
            }.start();

            mReceivedVideoDataCallBack = new DJIReceivedVideoDataCallBack(){
                private int packetLength = 0;
                private ByteBuffer accessUnitBuffer = ByteBuffer.allocate(500000);
                int inIndex;

                @Override
                public void onResult(byte[] videoBuffer, int size){
                    //mDjiGLSurfaceView.setDataToDecoder(videoBuffer, size);
                    if (!sps_ready){return;}
                    ArrayList<byte []> NAL_Units = splitNALunits(videoBuffer,size);
                    if (NAL_Units.size() <= 0)
                        return;
                    for (int i = 0; i < NAL_Units.size(); i++) {
                        if (NAL_Units.get(i)[4] == 0x09) {
                            if (!seq_start) {
                                seq_start = true;
                            }
                            else {
                                // Send off the current buffer of data (Access Unit)
                                inIndex = mCodec.dequeueInputBuffer(0);
                                if (inIndex >= 0) {
                                    ByteBuffer inputBuffer = mCodec.getInputBuffer(inIndex);
                                    if (packetLength > 0)
                                        inputBuffer.put(accessUnitBuffer.array(), 0, packetLength);
                                    mCodec.queueInputBuffer(inIndex, 0, packetLength, 0, 0);
                                    //presentationTime += 100;
                                    packetLength = 0;
                                    accessUnitBuffer.clear();
                                    accessUnitBuffer.rewind();
                                }
                            }
                        }
                        if (seq_start) {
                            accessUnitBuffer.put(NAL_Units.get(i));
                            packetLength += NAL_Units.get(i).length;
                        }
                    }
                    MediaCodec.BufferInfo bufferinfo = new MediaCodec.BufferInfo();
                    int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferinfo, 0);
                    if (outputBufferIndex >= 0) {
                        mCodec.releaseOutputBuffer(outputBufferIndex, true);
                        //handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Decoded frame number: " + c + " And queued frame: " + c0));
                    }
                    /*TextView myText = (TextView)findViewById(R.id.timer);
                    myText.setText(size); */
                    //handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Buffer size: " + size));
                }
            };
            DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(mReceivedVideoDataCallBack);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        //SurfaceValid = false;

        if (DJIDrone.getDjiCamera() != null) {
            DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(null);
        }

        return false;
        //mDjiGLSurfaceView.destroy();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (step_count != 5){
                    step_count++;
                    return;
                }
                else{
                    step_count = 0;
                }
                Bitmap frame_bmp = mDjiGLSurfaceView.getBitmap();
                //Mat frame_mat = new Mat();
                Utils.bitmapToMat(frame_bmp, mRgba);  // frame_bmp is in ARGB format, mRgba is in RBGA format

                //Todo: Do image processing stuff here
                mRgba.convertTo(mRgba,-1,2,0);  // Increase intensity by 2

                if (mIsColorSelected) {
                    //Show the error-corrected color
                    mBlobColorHsv = mDetector.get_new_hsvColor();
                    mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

                    //Debug
                    Log.i(TAG, "mDetector rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                            ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");
                    Log.i(TAG, "mDetector hsv color: (" + mBlobColorHsv.val[0] + ", " + mBlobColorHsv.val[1] +
                            ", " + mBlobColorHsv.val[2] + ", " + mBlobColorHsv.val[3] + ")");

                    mDetector.process(mRgba);
                    List<MatOfPoint> contours = mDetector.getContours();
                    Log.e(TAG, "Contours count: " + contours.size());
                    Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR, 2);

                    Mat colorLabel = mRgba.submat(4, 68, 4, 68);
                    colorLabel.setTo(mBlobColorRgba);

                    Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
                    mSpectrum.copyTo(spectrumLabel);
                }


                Utils.matToBitmap(mRgba, frame_bmp);
                Canvas canvas = TextureView_Display.lockCanvas();
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                canvas.drawBitmap(frame_bmp, new Rect(0, 0, frame_bmp.getWidth(), frame_bmp.getHeight()),
                        new Rect((canvas.getWidth() - frame_bmp.getWidth()) / 2,
                                (canvas.getHeight() - frame_bmp.getHeight()) / 2,
                                (canvas.getWidth() - frame_bmp.getWidth()) / 2 + frame_bmp.getWidth(),
                                (canvas.getHeight() - frame_bmp.getHeight()) / 2 + frame_bmp.getHeight()), null);
                TextureView_Display.unlockCanvasAndPost(canvas);
            }
        }).start();
    }

    public void UI_Initialization(){
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
                    //To make sure there will be no conflictions between flight control commands
                    right_spin = false;
                    move_forward = false;
                    move_backward = false;
                    try {
                        Thread.sleep(40);
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }

                    left_spin = true;
                    new Thread() {
                        public void run() {
                            Spinning_CounterCLKWise();

                            //Debug
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    viewTimer.setText(String.valueOf(k));
                                }
                            });
                            k = 1 - k;
                            /*try {
                                   Thread.sleep(25);
                               }
                               catch(Exception e){
                                   e.printStackTrace();
                               }  */
                            if(left_spin && gndStation){
                                handlerTimer.postDelayed(this,25);
                            }
                        }
                    }.start();
                }
                else if (event.getAction() == MotionEvent.ACTION_UP) {
                    left_spin = false;
                }
                return false;
            }
        });

        Right = (Button) findViewById(R.id.right);
        Right.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    left_spin = false;
                    move_forward = false;
                    move_backward = false;
                    try{
                        Thread.sleep(40);
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }

                    right_spin = true;
                    new Thread() {
                        public void run() {
                            Spinning_CLKWise();
                            if (right_spin && gndStation){
                                handlerTimer.postDelayed(this,25);
                            }
                        }
                    }.start();
                }
                else if (event.getAction() == MotionEvent.ACTION_UP){
                    right_spin = false;
                }
                return false;
            }
        });

        Forward = (Button) findViewById(R.id.forward);
        Forward.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    left_spin = false;
                    right_spin = false;
                    move_backward = false;
                    try {
                        Thread.sleep(40);
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }

                    move_forward = true;
                    new Thread() {
                        public void run() {
                            Move_Forward();
                            if (move_forward && gndStation){
                                handlerTimer.postDelayed(this,25);
                            }
                        }
                    }.start();
                }
                else if (event.getAction() == MotionEvent.ACTION_UP){
                    move_forward = false;
                }
                return false;
            }
        });

        Backward = (Button) findViewById(R.id.backward);
        Backward.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    left_spin = false;
                    right_spin = false;
                    move_forward = false;
                    try{
                        Thread.sleep(40);
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }

                    move_backward = true;
                    new Thread() {
                        public void run() {
                            Move_Backward();
                            if (move_backward && gndStation){
                                handlerTimer.postDelayed(this,25);
                            }
                        }
                    }.start();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    move_backward = false;
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
    }

    public void Read_IFrame(){
        InputStream is = getResources().openRawResource(R.raw.iframe_1280_3s);
        iframe = new byte[781];
        BufferedInputStream buf = new BufferedInputStream(is);
        try {
            buf.read(iframe, 0, iframe.length);
            buf.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        //Configure the PPs & SPS sequences, and the format
        /*SPS_PPS = Arrays.copyOfRange(iframe, 0 , 59);
        SPS = Arrays.copyOfRange(iframe, 0, 51);
        PPS = Arrays.copyOfRange(iframe, 51, 59);
        SEI = Arrays.copyOfRange(iframe,59,95);
        IDR = Arrays.copyOfRange(iframe, 95, 775); */

        //init_frame = Arrays.copyOfRange(iframe,0,775);
    }

    public void Set_Camera_Exposure_Mode(){
        // Set the camera exposure compensation value to increase the brightness of the video
        /*DJIDrone.getDjiCamera().setCameraExposureMode(DJICameraSettingsTypeDef.CameraExposureMode.Camera_Exposure_Mode_Shutter, new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                if (djiError.errorCode != DJIError.RESULT_OK) {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Error setting exposure mode!!!" + result));
                    // Show the error when setting fails
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST,"Camera Exposure Mode set!"));
                }
            }
        }); */

        DJIDrone.getDjiCamera().setCameraExposureCompensation(DJICameraSettingsTypeDef.CameraExposureCompensationType.Camera_Exposure_Compensation_P_4_0, new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                if (djiError.errorCode != DJIError.RESULT_OK) {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, "Error setting the compensation !!!" + result));
                    // Show the error when setting fails
                } else {
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST,"Camera Exposure Compensation set!"));
                }
            }
        });
    }

    public void Spinning_CounterCLKWise(){
        DJIDrone.getDjiGroundStation().setHorizontalControlCoordinateSystem(DJIGroundStationTypeDef.DJINavigationFlightControlCoordinateSystem.Navigation_Flight_Control_Coordinate_System_Body);
        DJIDrone.getDjiGroundStation().setHorizontalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlHorizontalControlMode.Navigation_Flight_Control_Horizontal_Control_Angle);
        DJIDrone.getDjiGroundStation().setVerticalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlVerticalControlMode.Navigation_Flight_Control_Vertical_Control_Velocity);
        DJIDrone.getDjiGroundStation().setYawControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Palstance);
        DJIDrone.getDjiGroundStation().sendFlightControlData(-30, 0, 0, 0, new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError.errorCode != DJIError.RESULT_OK && djiError.errorCode != DJIError.RESULT_SUCCEED) {
                    String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                }
            }
        });
    }

    public void Spinning_CLKWise(){
        DJIDrone.getDjiGroundStation().setHorizontalControlCoordinateSystem(DJIGroundStationTypeDef.DJINavigationFlightControlCoordinateSystem.Navigation_Flight_Control_Coordinate_System_Body);
        DJIDrone.getDjiGroundStation().setHorizontalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlHorizontalControlMode.Navigation_Flight_Control_Horizontal_Control_Angle);
        DJIDrone.getDjiGroundStation().setVerticalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlVerticalControlMode.Navigation_Flight_Control_Vertical_Control_Velocity);
        DJIDrone.getDjiGroundStation().setYawControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Palstance);
        DJIDrone.getDjiGroundStation().sendFlightControlData(30, 0, 0, 0, new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError.errorCode != DJIError.RESULT_OK && djiError.errorCode != DJIError.RESULT_SUCCEED) {
                    String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                }
            }
        });
    }

    public void Move_Forward(){
        DJIDrone.getDjiGroundStation().setHorizontalControlCoordinateSystem(DJIGroundStationTypeDef.DJINavigationFlightControlCoordinateSystem.Navigation_Flight_Control_Coordinate_System_Body);
        DJIDrone.getDjiGroundStation().setHorizontalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlHorizontalControlMode.Navigation_Flight_Control_Horizontal_Control_Angle);
        DJIDrone.getDjiGroundStation().setVerticalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlVerticalControlMode.Navigation_Flight_Control_Vertical_Control_Velocity);
        DJIDrone.getDjiGroundStation().setYawControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Palstance);
        DJIDrone.getDjiGroundStation().sendFlightControlData(0, -4, 0, 0, new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError.errorCode != DJIError.RESULT_OK && djiError.errorCode != DJIError.RESULT_SUCCEED) {
                    String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                }
            }
        });
    }

    public void Move_Backward(){
        DJIDrone.getDjiGroundStation().setHorizontalControlCoordinateSystem(DJIGroundStationTypeDef.DJINavigationFlightControlCoordinateSystem.Navigation_Flight_Control_Coordinate_System_Body);
        DJIDrone.getDjiGroundStation().setHorizontalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlHorizontalControlMode.Navigation_Flight_Control_Horizontal_Control_Angle);
        DJIDrone.getDjiGroundStation().setVerticalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlVerticalControlMode.Navigation_Flight_Control_Vertical_Control_Velocity);
        DJIDrone.getDjiGroundStation().setYawControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Palstance);
        DJIDrone.getDjiGroundStation().sendFlightControlData(0, 4, 0, 0, new DJIExecuteResultCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError.errorCode != DJIError.RESULT_OK && djiError.errorCode != DJIError.RESULT_SUCCEED) {
                    String result = "errorCode =" + djiError.errorCode + "\n" + "errorDescription =" + DJIError.getErrorDescriptionByErrcode(djiError.errorCode);
                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, result));
                }
            }
        });
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }public boolean onTouch(View v, MotionEvent event) {
        //Todo: to give a notification for debugging
        viewTimer.setText("Touched!!!");

        //Very important! So that the onFrame function won't change the parameters before this section finishes
        mIsColorSelected = false;

        int rows = mRgba.rows();        int cols = mRgba.cols();


        int xOffset = (TextureView_Display.getWidth() - cols) / 2;
        int yOffset = (TextureView_Display.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        org.opencv.core.Rect touchedRect = new org.opencv.core.Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.resetStart();
        mDetector.setHsvColor(mBlobColorHsv);

        Log.i(TAG, "mDetector hsv color set: (" + mBlobColorHsv.val[0] + ", " + mBlobColorHsv.val[1] +
                ", " + mBlobColorHsv.val[2] + ", " + mBlobColorHsv.val[3] + ")");

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false;
    }


}
