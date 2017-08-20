package com.daka.live.multisurfaceview;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.daka.live.multisurfaceview.gles.Drawable2d;
import com.daka.live.multisurfaceview.gles.EglCore;
import com.daka.live.multisurfaceview.gles.GlUtil;
import com.daka.live.multisurfaceview.gles.Sprite2d;
import com.daka.live.multisurfaceview.gles.Texture2dProgram;
import com.daka.live.multisurfaceview.gles.WindowSurface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity implements
        SurfaceHolder.Callback, MoviePlayer.PlayerFeedback, View.OnTouchListener {

    private static final int DEFAULT_ZOOM_PERCENT = 0;      // 0-100
    private static final int DEFAULT_SIZE_PERCENT = 50;     // 0-100
    private static final int DEFAULT_ROTATE_PERCENT = 0;    // 0-100

    // Requested values; actual may differ.
    private static final int REQ_CAMERA_WIDTH = 1280;
    private static final int REQ_CAMERA_HEIGHT = 720;
    private static final int REQ_CAMERA_FPS = 30;

    private SurfaceView mSurfaceView1;
    private SurfaceView mSurfaceView2;
    private SurfaceView mSurfaceView3;

    private static SurfaceHolder sSurfaceHolder1;
    private static SurfaceHolder sSurfaceHolder2;
    private static SurfaceHolder sSurfaceHolder3;


    private RenderThread mRenderThread;


    // These values are passed to us by the camera/render thread, and displayed in the UI.
    // We could also just peek at the values in the RenderThread object, but we'd need to
    // synchronize access carefully.
    private int mCameraPreviewWidth, mCameraPreviewHeight;
    private float mCameraPreviewFps;
    private int mRectWidth, mRectHeight;
    private int mZoomWidth, mZoomHeight;

    private String[] mMovieFiles;
    private MoviePlayer.PlayTask mPlayTask;

    private int mXDelta;
    private int mYDelta;

    ViewGroup mRoot;

    private int mPemission;

    @RequiresApi(api = Build.VERSION_CODES.M)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Begin onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPemission = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CAMERA);
        if(mPemission != PackageManager.PERMISSION_GRANTED){
            Log.d(TAG, "No Camera Permission : " + mPemission);
            requestPermissions(new String[]{ Manifest.permission.CAMERA,
                                             Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                             Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        mRoot = (ViewGroup)findViewById(R.id.main);

        mSurfaceView1 = (SurfaceView)findViewById(R.id.surfaceview1);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(160, 120); // set size
        params.setMargins(100, 50, 0, 0);  // set position


//        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(100, 100);
        mSurfaceView2 = (SurfaceView)findViewById(R.id.surfaceview2);
        mSurfaceView2.setLayoutParams(params);
        mSurfaceView2.setOnTouchListener(this);

//        mSurfaceView2.setLayoutParams(params);

        mSurfaceView3 = (SurfaceView)findViewById(R.id.surfaceview3);


        SurfaceHolder sh = mSurfaceView1.getHolder();
        sh.addCallback(this);


        sh = mSurfaceView2.getHolder();
        sh.addCallback(this);
        sh.setFormat(PixelFormat.TRANSLUCENT);
        mSurfaceView2.setZOrderMediaOverlay(true);
        mSurfaceView2.setZOrderOnTop(true);

        sh =  mSurfaceView3.getHolder();
        sh.addCallback(this);
        //sh.setFormat(PixelFormat.TRANSLUCENT);
        //mSurfaceView3.setZOrderOnTop(true);

        try {
            int i = 0;
            String[] tmpList = getAssets().list("");
            for (String str: tmpList
                 ) {
                if(str.endsWith(".mp4")){
                    if(mMovieFiles == null) {
                        mMovieFiles = new String[10];

                    }
                    mMovieFiles[i++] = str;

                }
            }

        }catch(Exception e){
            Log.e(TAG, e.toString());

        }

        Log.d(TAG, mMovieFiles[0] + Environment.getExternalStorageDirectory());
        Log.d(TAG, Environment.DIRECTORY_DCIM);
        //mMovieFiles = MiscUtils.getFiles(getFilesDir(), "*.mp4");
        //mMovieFiles[0] = "file:///android_asset/VID_20170816_123031.mp4";

    }

    public boolean onTouch(View view, MotionEvent event) {
        final int X = (int) event.getRawX();
        final int Y = (int) event.getRawY();
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                mXDelta = X - lParams.leftMargin;
                mYDelta = Y - lParams.topMargin;
                break;
            case MotionEvent.ACTION_UP:
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                layoutParams.leftMargin = X - mXDelta;
                layoutParams.topMargin = Y - mYDelta;
                layoutParams.rightMargin = -250;
                layoutParams.bottomMargin = -250;
                view.setLayoutParams(layoutParams);
                break;
        }
        mRoot.invalidate();
        return true;
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume BEGIN");
        super.onResume();

        //mRenderThread = new RenderThread(mHandler);
        mRenderThread = new RenderThread();
        mRenderThread.setName("TexFromCam Render");
        mRenderThread.start();
        mRenderThread.waitUntilReady();

        RenderHandler rh = mRenderThread.getHandler();

        rh.sendZoomValue(0);
        rh.sendSizeValue(100);
        rh.sendRotateValue(0);

        if (sSurfaceHolder1 != null) {
            Log.d(TAG, "Sending previous surface");
            rh.sendSurfaceAvailable(sSurfaceHolder1, false);
        } else {
            Log.d(TAG, "No previous surface");
        }

        SpeedControlCallback callback = new SpeedControlCallback();

        SurfaceHolder sh = mSurfaceView2.getHolder();
        Surface surface = sh.getSurface();


        MoviePlayer player = null;
        try {

            player = new MoviePlayer(
                    new File(Environment.getExternalStorageDirectory()+"/DCIM/Camera", mMovieFiles[0]),
                    surface,
                    callback);

        } catch (IOException ioe) {
            Log.e(TAG, "Unable to play movie", ioe);
            surface.release();
            return;
        }

        int width = player.getVideoWidth();
        int height = player.getVideoHeight();

        //sh.setFixedSize(width/4, height/4);

        mPlayTask = new MoviePlayer.PlayTask(player, this);
        mPlayTask.execute();


        Log.d(TAG, "onResume END");

    }

    @Override
    protected void onPause() {

        Log.d(TAG, "onPause BEGIN");
        super.onPause();


        RenderHandler rh = mRenderThread.getHandler();
        rh.sendShutdown();
        try {
            mRenderThread.join();
        } catch (InterruptedException ie) {
            // not expected
            throw new RuntimeException("join was interrupted", ie);
        }
        mRenderThread = null;

        if (mPlayTask != null) {
            mPlayTask.requestStop();
            mPlayTask.waitForStop();
        }

        Log.d(TAG, "onPause END");
    }

    private int getHolderID(SurfaceHolder holder){
        int id = -1;
        if(holder == mSurfaceView1.getHolder()){
            id = 0;
        }else if(holder == mSurfaceView2.getHolder()){
            id = 1;
        }else if(holder == mSurfaceView3.getHolder()){
            id = 2;
        }

        return id;
    }

    public void surfaceCreated(SurfaceHolder holder){

        int id = getHolderID(holder);
        switch(id){
            case 0:
                Log.d(TAG, "surfaceCreated holder0=" + holder + " (static=" + sSurfaceHolder1 + ")");
                if (sSurfaceHolder1 != null) {
                    throw new RuntimeException("sSurfaceHolder1 is already set");
                }
                sSurfaceHolder1 = holder;

                if (mRenderThread != null) {
                    // Normal case -- render thread is running, tell it about the new surface.
                    RenderHandler rh = mRenderThread.getHandler();
                    rh.sendSurfaceAvailable(holder, true);
                } else {
                    // Sometimes see this on 4.4.x N5: power off, power on, unlock, with device in
                    // landscape and a lock screen that requires portrait.  The surface-created
                    // message is showing up after onPause().
                    //
                    // Chances are good that the surface will be destroyed before the activity is
                    // unpaused, but we track it anyway.  If the activity is un-paused and we start
                    // the RenderThread, the SurfaceHolder will be passed in right after the thread
                    // is created.
                    Log.d(TAG, "render thread not running");
                }
                break;
            case 1:
                Log.d(TAG, "surfaceCreated holder1=" + holder + " (static=" + sSurfaceHolder2 + ")");
                if (sSurfaceHolder2 != null) {
                    throw new RuntimeException("sSurfaceHolder2 is already set");
                }
                sSurfaceHolder2 = holder;
                break;
            case 2:
                Log.d(TAG, "surfaceCreated holder2=" + holder + " (static=" + sSurfaceHolder3 + ")");
                if (sSurfaceHolder3 != null) {
                    throw new RuntimeException("sSurfaceHolder2 is already set");
                }
                sSurfaceHolder3 = holder;
                break;
            default:
                Log.d(TAG, "invalid surfaceCreated holder");
        }

    }


    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height){
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendSurfaceChanged(format, width, height);
        } else {
            Log.d(TAG, "Ignoring surfaceChanged");
            return;
        }

    }


    public void surfaceDestroyed(SurfaceHolder holder){
        // In theory we should tell the RenderThread that the surface has been destroyed.
        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendSurfaceDestroyed();
        }

        int id = getHolderID(holder);
        switch (id) {
            case 0:
                Log.d(TAG, "surfaceDestroyed1 holder=" + holder);
                sSurfaceHolder1 = null;
                break;
            case 1:
                Log.d(TAG, "surfaceDestroyed2 holder=" + holder);
                sSurfaceHolder2 = null;
                break;
            case 2:
                Log.d(TAG, "surfaceDestroyed3 holder=" + holder);
                sSurfaceHolder3 = null;
                break;
            default:

        }
    }

    @Override
    public void playbackStopped() {
        mPlayTask = null;
    }

    /**
     * Thread that handles all rendering and camera operations.
     */
    private static class RenderThread extends Thread implements
            SurfaceTexture.OnFrameAvailableListener {
        // Object must be created on render thread to get correct Looper, but is used from
        // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
        // constructed object.
        private volatile RenderHandler mHandler;

        // Used to wait for the thread to start.
        private Object mStartLock = new Object();
        private boolean mReady = false;

        //private MainHandler mMainHandler;

        private Camera mCamera;
        private int mCameraPreviewWidth, mCameraPreviewHeight;

        private EglCore mEglCore;
        private WindowSurface mWindowSurface;
        private int mWindowSurfaceWidth;
        private int mWindowSurfaceHeight;

        // Receives the output from the camera preview.
        private SurfaceTexture mCameraTexture;

        // Orthographic projection matrix.
        private float[] mDisplayProjectionMatrix = new float[16];

        private Texture2dProgram mTexProgram;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect = new Sprite2d(mRectDrawable);

        private int mZoomPercent = DEFAULT_ZOOM_PERCENT;
        private int mSizePercent = DEFAULT_SIZE_PERCENT;
        private int mRotatePercent = DEFAULT_ROTATE_PERCENT;
        private float mPosX, mPosY;


        /**
         * Constructor.  Pass in the MainHandler, which allows us to send stuff back to the
         * Activity.
         */
        /*
        public RenderThread(MainHandler handler) {
            mMainHandler = handler;
        }
        */

        /**
         * Thread entry point.
         */
        @Override
        public void run() {
            Looper.prepare();

            // We need to create the Handler before reporting ready.
            mHandler = new RenderHandler(this);
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();    // signal waitUntilReady()
            }

            // Prepare EGL and open the camera before we start handling messages.
            mEglCore = new EglCore(null, 0);
            openCamera(REQ_CAMERA_WIDTH, REQ_CAMERA_HEIGHT, REQ_CAMERA_FPS);

            Looper.loop();

            Log.d(TAG, "looper quit");
            releaseCamera();
            releaseGl();
            mEglCore.release();

            synchronized (mStartLock) {
                mReady = false;
            }
        }

        /**
         * Waits until the render thread is ready to receive messages.
         * <p>
         * Call from the UI thread.
         */
        public void waitUntilReady() {
            synchronized (mStartLock) {
                while (!mReady) {
                    try {
                        mStartLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        /**
         * Shuts everything down.
         */
        private void shutdown() {
            Log.d(TAG, "shutdown");
            Looper.myLooper().quit();
        }

        /**
         * Returns the render thread's Handler.  This may be called from any thread.
         */
        public RenderHandler getHandler() {
            return mHandler;
        }

        /**
         * Handles the surface-created callback from SurfaceView.  Prepares GLES and the Surface.
         */
        private void surfaceAvailable(SurfaceHolder holder, boolean newSurface) {
            Surface surface = holder.getSurface();
            mWindowSurface = new WindowSurface(mEglCore, surface, false);
            mWindowSurface.makeCurrent();

            // Create and configure the SurfaceTexture, which will receive frames from the
            // camera.  We set the textured rect's program to render from it.
            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            int textureId = mTexProgram.createTextureObject();
            mCameraTexture = new SurfaceTexture(textureId);
            mRect.setTexture(textureId);

            if (!newSurface) {
                // This Surface was established on a previous run, so no surfaceChanged()
                // message is forthcoming.  Finish the surface setup now.
                //
                // We could also just call this unconditionally, and perhaps do an unnecessary
                // bit of reallocating if a surface-changed message arrives.
                mWindowSurfaceWidth = mWindowSurface.getWidth();
                mWindowSurfaceHeight = mWindowSurface.getHeight();
                finishSurfaceSetup();
            }

            mCameraTexture.setOnFrameAvailableListener(this);
        }

        /**
         * Releases most of the GL resources we currently hold (anything allocated by
         * surfaceAvailable()).
         * <p>
         * Does not release EglCore.
         */
        private void releaseGl() {
            GlUtil.checkGlError("releaseGl start");

            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mTexProgram != null) {
                mTexProgram.release();
                mTexProgram = null;
            }
            GlUtil.checkGlError("releaseGl done");

            mEglCore.makeNothingCurrent();
        }

        /**
         * Handles the surfaceChanged message.
         * <p>
         * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
         * could also be called with a Surface created on a previous run.  So this may not
         * be called.
         */
        private void surfaceChanged(int width, int height) {
            Log.d(TAG, "RenderThread surfaceChanged " + width + "x" + height);

            mWindowSurfaceWidth = width;
            mWindowSurfaceHeight = height;
            finishSurfaceSetup();
        }

        /**
         * Handles the surfaceDestroyed message.
         */
        private void surfaceDestroyed() {
            // In practice this never appears to be called -- the activity is always paused
            // before the surface is destroyed.  In theory it could be called though.
            Log.d(TAG, "RenderThread surfaceDestroyed");
            releaseGl();
        }

        /**
         * Sets up anything that depends on the window size.
         * <p>
         * Open the camera (to set mCameraAspectRatio) before calling here.
         */
        private void finishSurfaceSetup() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;
            Log.d(TAG, "finishSurfaceSetup size=" + width + "x" + height +
                    " camera=" + mCameraPreviewWidth + "x" + mCameraPreviewHeight);

            // Use full window.
            GLES20.glViewport(0, 0, width, height);

            // Simple orthographic projection, with (0,0) in lower-left corner.
            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, width, 0, height, -1, 1);

            // Default position is center of screen.
            mPosX = width / 2.0f;
            mPosY = height / 2.0f;

            updateGeometry();

            // Ready to go, start the camera.
            Log.d(TAG, "starting camera preview");
            try {
                mCamera.setPreviewTexture(mCameraTexture);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mCamera.startPreview();
        }

        /**
         * Updates the geometry of mRect, based on the size of the window and the current
         * values set by the UI.
         */
        private void updateGeometry() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;

            int smallDim = Math.min(width, height);
            // Max scale is a bit larger than the screen, so we can show over-size.
            float scaled = smallDim * (mSizePercent / 100.0f) * 1.25f;
            float cameraAspect = (float) mCameraPreviewWidth / mCameraPreviewHeight;
            int newWidth = Math.round(scaled * cameraAspect);
            int newHeight = Math.round(scaled);

            float zoomFactor = 1.0f - (mZoomPercent / 100.0f);
            int rotAngle = Math.round(360 * (mRotatePercent / 100.0f));

            mRect.setScale(newWidth, newHeight);
            mRect.setPosition(mPosX, mPosY);
            mRect.setRotation(rotAngle);
            mRectDrawable.setScale(zoomFactor);

            /*
            mMainHandler.sendRectSize(newWidth, newHeight);
            mMainHandler.sendZoomArea(Math.round(mCameraPreviewWidth * zoomFactor),
                    Math.round(mCameraPreviewHeight * zoomFactor));
            mMainHandler.sendRotateDeg(rotAngle);
            */
        }

        @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mHandler.sendFrameAvailable();
        }

        /**
         * Handles incoming frame of data from the camera.
         */
        private void frameAvailable() {
            mCameraTexture.updateTexImage();
            draw();
        }

        /**
         * Draws the scene and submits the buffer.
         */
        private void draw() {
            GlUtil.checkGlError("draw start");

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);
            mWindowSurface.swapBuffers();

            GlUtil.checkGlError("draw done");
        }

        private void setZoom(int percent) {
            mZoomPercent = percent;
            updateGeometry();
        }

        private void setSize(int percent) {
            mSizePercent = percent;
            updateGeometry();
        }

        private void setRotate(int percent) {
            mRotatePercent = percent;
            updateGeometry();
        }

        private void setPosition(int x, int y) {
            mPosX = x;
            mPosY = mWindowSurfaceHeight - y;   // GLES is upside-down
            updateGeometry();
        }

        /**
         * Opens a camera, and attempts to establish preview mode at the specified width
         * and height with a fixed frame rate.
         * <p>
         * Sets mCameraPreviewWidth / mCameraPreviewHeight.
         */
        private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
            if (mCamera != null) {
                throw new RuntimeException("camera already initialized");
            }

            Camera.CameraInfo info = new Camera.CameraInfo();

            // Try to find a front-facing camera (e.g. for videoconferencing).
            int numCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCamera = Camera.open(i);
                    break;
                }
            }
            if (mCamera == null) {
                Log.d(TAG, "No front-facing camera found; opening default");
                mCamera = Camera.open();    // opens first back-facing camera
            }
            if (mCamera == null) {
                throw new RuntimeException("Unable to open camera");
            }

            Camera.Parameters parms = mCamera.getParameters();

            CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

            // Try to set the frame rate to a constant value.
            int thousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

            // Give the camera a hint that we're recording video.  This can have a big
            // impact on frame rate.
            parms.setRecordingHint(true);

            mCamera.setParameters(parms);

            int[] fpsRange = new int[2];
            Camera.Size mCameraPreviewSize = parms.getPreviewSize();
            parms.getPreviewFpsRange(fpsRange);
            String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
            if (fpsRange[0] == fpsRange[1]) {
                previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
            } else {
                previewFacts += " @[" + (fpsRange[0] / 1000.0) +
                        " - " + (fpsRange[1] / 1000.0) + "] fps";
            }
            Log.i(TAG, "Camera config: " + previewFacts);

            mCameraPreviewWidth = mCameraPreviewSize.width;
            mCameraPreviewHeight = mCameraPreviewSize.height;
            /*
            mMainHandler.sendCameraParams(mCameraPreviewWidth, mCameraPreviewHeight,
                    thousandFps / 1000.0f);
            */
        }

        /**
         * Stops camera preview, and releases the camera to the system.
         */
        private void releaseCamera() {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
                Log.d(TAG, "releaseCamera -- done");
            }
        }
    }

    /**
     * Thread that handles all rendering and camera operations.
     */
    private static class PlayRenderThread extends Thread implements
            SurfaceTexture.OnFrameAvailableListener {
        // Object must be created on render thread to get correct Looper, but is used from
        // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
        // constructed object.
        private volatile PlayRenderHandler mHandler;

        // Used to wait for the thread to start.
        private Object mStartLock = new Object();
        private boolean mReady = false;

        //private MainHandler mMainHandler;

        //private Camera mCamera;
        private int mVideoWidth, mVideoHeight;

        private EglCore mEglCore;
        private WindowSurface mWindowSurface;
        private int mWindowSurfaceWidth;
        private int mWindowSurfaceHeight;

        // Receives the output from the camera preview.
        private SurfaceTexture mVideoTexture;

        // Orthographic projection matrix.
        private float[] mDisplayProjectionMatrix = new float[16];

        private Texture2dProgram mTexProgram;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect = new Sprite2d(mRectDrawable);

        private int mZoomPercent = DEFAULT_ZOOM_PERCENT;
        private int mSizePercent = 30;//DEFAULT_SIZE_PERCENT;
        private int mRotatePercent = DEFAULT_ROTATE_PERCENT;
        private float mPosX, mPosY;


        /**
         * Constructor.  Pass in the MainHandler, which allows us to send stuff back to the
         * Activity.
         */
        /*
        public RenderThread(MainHandler handler) {
            mMainHandler = handler;
        }
        */

        /**
         * Thread entry point.
         */
        @Override
        public void run() {
            Looper.prepare();

            // We need to create the Handler before reporting ready.
            mHandler = new PlayRenderHandler(this);
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();    // signal waitUntilReady()
            }

            // Prepare EGL and open the camera before we start handling messages.
            mEglCore = new EglCore(null, 0);
//            openCamera(REQ_CAMERA_WIDTH, REQ_CAMERA_HEIGHT, REQ_CAMERA_FPS);

            Looper.loop();

            Log.d(TAG, "looper quit");
//            releaseCamera();
            releaseGl();
            mEglCore.release();

            synchronized (mStartLock) {
                mReady = false;
            }
        }

        /**
         * Waits until the render thread is ready to receive messages.
         * <p>
         * Call from the UI thread.
         */
        public void waitUntilReady() {
            synchronized (mStartLock) {
                while (!mReady) {
                    try {
                        mStartLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        /**
         * Shuts everything down.
         */
        private void shutdown() {
            Log.d(TAG, "shutdown");
            Looper.myLooper().quit();
        }

        /**
         * Returns the render thread's Handler.  This may be called from any thread.
         */
        public PlayRenderHandler getHandler() {
            return mHandler;
        }

        /**
         * Handles the surface-created callback from SurfaceView.  Prepares GLES and the Surface.
         */
        private void surfaceAvailable(SurfaceHolder holder, boolean newSurface) {
            Surface surface = holder.getSurface();
            mWindowSurface = new WindowSurface(mEglCore, surface, false);
            mWindowSurface.makeCurrent();

            // Create and configure the SurfaceTexture, which will receive frames from the
            // camera.  We set the textured rect's program to render from it.
            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            int textureId = mTexProgram.createTextureObject();
            mVideoTexture = new SurfaceTexture(textureId);
            mRect.setTexture(textureId);

            if (!newSurface) {
                // This Surface was established on a previous run, so no surfaceChanged()
                // message is forthcoming.  Finish the surface setup now.
                //
                // We could also just call this unconditionally, and perhaps do an unnecessary
                // bit of reallocating if a surface-changed message arrives.
                mWindowSurfaceWidth = mWindowSurface.getWidth();
                mWindowSurfaceHeight = mWindowSurface.getHeight();
                finishSurfaceSetup();
            }

            mVideoTexture.setOnFrameAvailableListener(this);
        }

        /**
         * Releases most of the GL resources we currently hold (anything allocated by
         * surfaceAvailable()).
         * <p>
         * Does not release EglCore.
         */
        private void releaseGl() {
            GlUtil.checkGlError("releaseGl start");

            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mTexProgram != null) {
                mTexProgram.release();
                mTexProgram = null;
            }
            GlUtil.checkGlError("releaseGl done");

            mEglCore.makeNothingCurrent();
        }

        /**
         * Handles the surfaceChanged message.
         * <p>
         * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
         * could also be called with a Surface created on a previous run.  So this may not
         * be called.
         */
        private void surfaceChanged(int width, int height) {
            Log.d(TAG, "PlayRenderThread surfaceChanged " + width + "x" + height);

            mWindowSurfaceWidth = width;
            mWindowSurfaceHeight = height;
            finishSurfaceSetup();
        }

        /**
         * Handles the surfaceDestroyed message.
         */
        private void surfaceDestroyed() {
            // In practice this never appears to be called -- the activity is always paused
            // before the surface is destroyed.  In theory it could be called though.
            Log.d(TAG, "PlayRenderThread surfaceDestroyed");
            releaseGl();
        }

        /**
         * Sets up anything that depends on the window size.
         * <p>
         * Open the camera (to set mCameraAspectRatio) before calling here.
         */
        private void finishSurfaceSetup() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;
//            Log.d(TAG, "finishSurfaceSetup size=" + width + "x" + height +
//                    " camera=" + mCameraPreviewWidth + "x" + mCameraPreviewHeight);

            // Use full window.
            GLES20.glViewport(0, 0, width, height);

            // Simple orthographic projection, with (0,0) in lower-left corner.
            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, width, 0, height, -1, 1);

            // Default position is center of screen.
            mPosX = width / 2.0f;
            mPosY = height / 2.0f;

            updateGeometry();

            // Ready to go, start the camera.
//            Log.d(TAG, "starting camera preview");
//            try {
//                mCamera.setPreviewTexture(mCameraTexture);
//            } catch (IOException ioe) {
//                throw new RuntimeException(ioe);
//            }
//            mCamera.startPreview();
        }

        /**
         * Updates the geometry of mRect, based on the size of the window and the current
         * values set by the UI.
         */
        private void updateGeometry() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;

            int smallDim = Math.min(width, height);
            // Max scale is a bit larger than the screen, so we can show over-size.
            float scaled = smallDim * (mSizePercent / 100.0f) * 1.25f;
            float videoAspect = (float) mVideoWidth / mVideoHeight;
            int newWidth = Math.round(scaled * videoAspect);
            int newHeight = Math.round(scaled);

            float zoomFactor = 1.0f - (mZoomPercent / 100.0f);
            int rotAngle = Math.round(360 * (mRotatePercent / 100.0f));

            mRect.setScale(newWidth, newHeight);
            mRect.setPosition(mPosX, mPosY);
            mRect.setRotation(rotAngle);
            mRectDrawable.setScale(zoomFactor);

            /*
            mMainHandler.sendRectSize(newWidth, newHeight);
            mMainHandler.sendZoomArea(Math.round(mCameraPreviewWidth * zoomFactor),
                    Math.round(mCameraPreviewHeight * zoomFactor));
            mMainHandler.sendRotateDeg(rotAngle);
            */
        }

        @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mHandler.sendFrameAvailable();
        }

        /**
         * Handles incoming frame of data from the camera.
         */
        private void frameAvailable() {
            mVideoTexture.updateTexImage();
            draw();
        }

        /**
         * Draws the scene and submits the buffer.
         */
        private void draw() {
            GlUtil.checkGlError("draw start");

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);
            mWindowSurface.swapBuffers();

            GlUtil.checkGlError("draw done");
        }

        private void setZoom(int percent) {
            mZoomPercent = percent;
            updateGeometry();
        }

        private void setSize(int percent) {
            mSizePercent = percent;
            updateGeometry();
        }

        private void setRotate(int percent) {
            mRotatePercent = percent;
            updateGeometry();
        }

        private void setPosition(int x, int y) {
            mPosX = x;
            mPosY = mWindowSurfaceHeight - y;   // GLES is upside-down
            updateGeometry();
        }

        /**
         * Opens a camera, and attempts to establish preview mode at the specified width
         * and height with a fixed frame rate.
         * <p>
         * Sets mCameraPreviewWidth / mCameraPreviewHeight.
         */
//        private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
//            if (mCamera != null) {
//                throw new RuntimeException("camera already initialized");
//            }
//
//            Camera.CameraInfo info = new Camera.CameraInfo();
//
//            // Try to find a front-facing camera (e.g. for videoconferencing).
//            int numCameras = Camera.getNumberOfCameras();
//            for (int i = 0; i < numCameras; i++) {
//                Camera.getCameraInfo(i, info);
//                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                    mCamera = Camera.open(i);
//                    break;
//                }
//            }
//            if (mCamera == null) {
//                Log.d(TAG, "No front-facing camera found; opening default");
//                mCamera = Camera.open();    // opens first back-facing camera
//            }
//            if (mCamera == null) {
//                throw new RuntimeException("Unable to open camera");
//            }
//
//            Camera.Parameters parms = mCamera.getParameters();
//
//            CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
//
//            // Try to set the frame rate to a constant value.
//            int thousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);
//
//            // Give the camera a hint that we're recording video.  This can have a big
//            // impact on frame rate.
//            parms.setRecordingHint(true);
//
//            mCamera.setParameters(parms);
//
//            int[] fpsRange = new int[2];
//            Camera.Size mCameraPreviewSize = parms.getPreviewSize();
//            parms.getPreviewFpsRange(fpsRange);
//            String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
//            if (fpsRange[0] == fpsRange[1]) {
//                previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
//            } else {
//                previewFacts += " @[" + (fpsRange[0] / 1000.0) +
//                        " - " + (fpsRange[1] / 1000.0) + "] fps";
//            }
//            Log.i(TAG, "Camera config: " + previewFacts);
//
//            mCameraPreviewWidth = mCameraPreviewSize.width;
//            mCameraPreviewHeight = mCameraPreviewSize.height;
//            /*
//            mMainHandler.sendCameraParams(mCameraPreviewWidth, mCameraPreviewHeight,
//                    thousandFps / 1000.0f);
//            */
//        }

        /**
         * Stops camera preview, and releases the camera to the system.
         */
//        private void releaseCamera() {
//            if (mCamera != null) {
//                mCamera.stopPreview();
//                mCamera.release();
//                mCamera = null;
//                Log.d(TAG, "releaseCamera -- done");
//            }
//        }
    }

    /**
     * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
     * <p>
     * The object is created on the render thread, and the various "send" methods are called
     * from the UI thread.
     */
    private static class RenderHandler extends Handler {
        private static final int MSG_SURFACE_AVAILABLE = 0;
        private static final int MSG_SURFACE_CHANGED = 1;
        private static final int MSG_SURFACE_DESTROYED = 2;
        private static final int MSG_SHUTDOWN = 3;
        private static final int MSG_FRAME_AVAILABLE = 4;
        private static final int MSG_ZOOM_VALUE = 5;
        private static final int MSG_SIZE_VALUE = 6;
        private static final int MSG_ROTATE_VALUE = 7;
        private static final int MSG_POSITION = 8;
        private static final int MSG_REDRAW = 9;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<RenderThread> mWeakRenderThread;

        /**
         * Call from render thread.
         */
        public RenderHandler(RenderThread rt) {
            mWeakRenderThread = new WeakReference<RenderThread>(rt);
        }

        /**
         * Sends the "surface available" message.  If the surface was newly created (i.e.
         * this is called from surfaceCreated()), set newSurface to true.  If this is
         * being called during Activity startup for a previously-existing surface, set
         * newSurface to false.
         * <p>
         * The flag tells the caller whether or not it can expect a surfaceChanged() to
         * arrive very soon.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceAvailable(SurfaceHolder holder, boolean newSurface) {
            sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,
                    newSurface ? 1 : 0, 0, holder));
        }

        /**
         * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceChanged(@SuppressWarnings("unused") int format, int width,
                                       int height) {
            // ignore format
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceDestroyed() {
            sendMessage(obtainMessage(MSG_SURFACE_DESTROYED));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN));
        }

        /**
         * Sends the "frame available" message.
         * <p>
         * Call from UI thread.
         */
        public void sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
        }

        /**
         * Sends the "zoom value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendZoomValue(int progress) {
            sendMessage(obtainMessage(MSG_ZOOM_VALUE, progress, 0));
        }

        /**
         * Sends the "size value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendSizeValue(int progress) {
            sendMessage(obtainMessage(MSG_SIZE_VALUE, progress, 0));
        }

        /**
         * Sends the "rotate value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendRotateValue(int progress) {
            sendMessage(obtainMessage(MSG_ROTATE_VALUE, progress, 0));
        }

        /**
         * Sends the "position" message.  Sets the position of the rect.
         * <p>
         * Call from UI thread.
         */
        public void sendPosition(int x, int y) {
            sendMessage(obtainMessage(MSG_POSITION, x, y));
        }

        /**
         * Sends the "redraw" message.  Forces an immediate redraw.
         * <p>
         * Call from UI thread.
         */
        public void sendRedraw() {
            sendMessage(obtainMessage(MSG_REDRAW));
        }

        @Override  // runs on RenderThread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

            RenderThread renderThread = mWeakRenderThread.get();
            if (renderThread == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_AVAILABLE:
                    renderThread.surfaceAvailable((SurfaceHolder) msg.obj, msg.arg1 != 0);
                    break;
                case MSG_SURFACE_CHANGED:
                    renderThread.surfaceChanged(msg.arg1, msg.arg2);
                    break;
                case MSG_SURFACE_DESTROYED:
                    renderThread.surfaceDestroyed();
                    break;
                case MSG_SHUTDOWN:
                    renderThread.shutdown();
                    break;
                case MSG_FRAME_AVAILABLE:
                    renderThread.frameAvailable();
                    break;
                case MSG_ZOOM_VALUE:
                    renderThread.setZoom(msg.arg1);
                    break;
                case MSG_SIZE_VALUE:
                    renderThread.setSize(msg.arg1);
                    break;
                case MSG_ROTATE_VALUE:
                    renderThread.setRotate(msg.arg1);
                    break;
                case MSG_POSITION:
                    renderThread.setPosition(msg.arg1, msg.arg2);
                    break;
                case MSG_REDRAW:
                    renderThread.draw();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }

    /**
     * Handler for PlayRenderThread.  Used for messages sent from the UI thread to the render thread.
     * <p>
     * The object is created on the render thread, and the various "send" methods are called
     * from the UI thread.
     */
    private static class PlayRenderHandler extends Handler {
        private static final int MSG_SURFACE_AVAILABLE = 0;
        private static final int MSG_SURFACE_CHANGED = 1;
        private static final int MSG_SURFACE_DESTROYED = 2;
        private static final int MSG_SHUTDOWN = 3;
        private static final int MSG_FRAME_AVAILABLE = 4;
        private static final int MSG_ZOOM_VALUE = 5;
        private static final int MSG_SIZE_VALUE = 6;
        private static final int MSG_ROTATE_VALUE = 7;
        private static final int MSG_POSITION = 8;
        private static final int MSG_REDRAW = 9;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<PlayRenderThread> mWeakPlayRenderThread;

        /**
         * Call from render thread.
         */
        public PlayRenderHandler(PlayRenderThread rt) {
            mWeakPlayRenderThread = new WeakReference<PlayRenderThread>(rt);
        }

        /**
         * Sends the "surface available" message.  If the surface was newly created (i.e.
         * this is called from surfaceCreated()), set newSurface to true.  If this is
         * being called during Activity startup for a previously-existing surface, set
         * newSurface to false.
         * <p>
         * The flag tells the caller whether or not it can expect a surfaceChanged() to
         * arrive very soon.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceAvailable(SurfaceHolder holder, boolean newSurface) {
            sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,
                    newSurface ? 1 : 0, 0, holder));
        }

        /**
         * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceChanged(@SuppressWarnings("unused") int format, int width,
                                       int height) {
            // ignore format
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceDestroyed() {
            sendMessage(obtainMessage(MSG_SURFACE_DESTROYED));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN));
        }

        /**
         * Sends the "frame available" message.
         * <p>
         * Call from UI thread.
         */
        public void sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
        }

        /**
         * Sends the "zoom value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendZoomValue(int progress) {
            sendMessage(obtainMessage(MSG_ZOOM_VALUE, progress, 0));
        }

        /**
         * Sends the "size value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendSizeValue(int progress) {
            sendMessage(obtainMessage(MSG_SIZE_VALUE, progress, 0));
        }

        /**
         * Sends the "rotate value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendRotateValue(int progress) {
            sendMessage(obtainMessage(MSG_ROTATE_VALUE, progress, 0));
        }

        /**
         * Sends the "position" message.  Sets the position of the rect.
         * <p>
         * Call from UI thread.
         */
        public void sendPosition(int x, int y) {
            sendMessage(obtainMessage(MSG_POSITION, x, y));
        }

        /**
         * Sends the "redraw" message.  Forces an immediate redraw.
         * <p>
         * Call from UI thread.
         */
        public void sendRedraw() {
            sendMessage(obtainMessage(MSG_REDRAW));
        }

        @Override  // runs on PlayRenderThread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

            PlayRenderThread playRenderThread = mWeakPlayRenderThread.get();
            if (playRenderThread == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_AVAILABLE:
                    playRenderThread.surfaceAvailable((SurfaceHolder) msg.obj, msg.arg1 != 0);
                    break;
                case MSG_SURFACE_CHANGED:
                    playRenderThread.surfaceChanged(msg.arg1, msg.arg2);
                    break;
                case MSG_SURFACE_DESTROYED:
                    playRenderThread.surfaceDestroyed();
                    break;
                case MSG_SHUTDOWN:
                    playRenderThread.shutdown();
                    break;
                case MSG_FRAME_AVAILABLE:
                    playRenderThread.frameAvailable();
                    break;
                case MSG_ZOOM_VALUE:
                    playRenderThread.setZoom(msg.arg1);
                    break;
                case MSG_SIZE_VALUE:
                    playRenderThread.setSize(msg.arg1);
                    break;
                case MSG_ROTATE_VALUE:
                    playRenderThread.setRotate(msg.arg1);
                    break;
                case MSG_POSITION:
                    playRenderThread.setPosition(msg.arg1, msg.arg2);
                    break;
                case MSG_REDRAW:
                    playRenderThread.draw();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }
}
