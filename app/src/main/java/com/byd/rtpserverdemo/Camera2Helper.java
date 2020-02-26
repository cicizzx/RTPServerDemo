package com.byd.rtpserverdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2Helper {
    private static final String TAG = "Camera2Helper";
    private Context mContext;
    private ImageReader mImageReader;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private ImageDataListener mImageDataListener;
    private String mCameraId = "0";
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback(){
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.i(TAG, "onOpened");
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i(TAG, "onDisconnected");
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError openDevice error:" + error);
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }
    };

    public Camera2Helper(Context context) {
        this.mContext = context;
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startCamera(int width, int height) {
        Log.i(TAG, "start Camera.");
        startBackgroundThread();
        setUpCameraOutputs(width, height);
        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "startCamera error: " + e.getMessage());
        }
    }

    public void playCamera() {
        Log.i(TAG, "pauseCamera");
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void pauseCamera() {
        Log.i(TAG, "pauseCamera");
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        Log.i(TAG, "closeCamera");
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }

            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            Surface imageSurface = mImageReader.getSurface();
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(imageSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(imageSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "onConfigured");
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCaptureSession = session;
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                            }
                        }, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "onConfigureFailed");
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        Log.i(TAG, "setUpCameraOutputs start");
        mImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, /*maxImages*/2);
        mImageReader.setOnImageAvailableListener(new RTPOnImageAvailableListener(), mBackgroundHandler);
        return;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private class RTPOnImageAvailableListener implements ImageReader.OnImageAvailableListener{

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.i(TAG, "onImageAvailable");
            Image readImage = reader.acquireNextImage();
            byte[] data = ImageUtil.getBytesFromImageAsType(readImage, 1);
            //byte[] data = ImageUtil.getBytesFromImageAsType(readImage);
            //byte[] rotateData = ImageUtil.rotateYUVDegree90(data, readImage.getWidth(), readImage.getHeight());
            readImage.close();
            /**if (rotateData == null) {
                Log.e(TAG, "The rotated data is null.");
            }*/
            mImageDataListener.OnImageDataListener(data);
        }
    }

    public void setImageDataListener(ImageDataListener listener) {
        this.mImageDataListener = listener;
    }

    public static interface ImageDataListener{
        public void OnImageDataListener(byte[] reader);
    }
}
