package com.byd.rtpserverdemo;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import com.byd.rtpserverdemo.rtp.RtpSenderWrapper;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private Camera2Helper mCamera2Helper;
    private int mWidth = 1440;
    private int mHeight = 1080;
    private int mFramerate = 30;
    private int mBitrate = 40000;
    private RtpSenderWrapper mRtpSenderWrapper;
    private static final String IP = "192.168.43.30";
    private static final int PORT = 5004;
    private AvcEncoder mAvcEncoder;
    private byte[] mH264Data = new byte[mWidth * mHeight * 3];
    private Button mStart;
    private Button mStop;
    private Button mPause;
    private int mStartState = 0;
    private int mStopState = 1;
    private int mPauseState = 2;
    private int mPlayState = 3;
    private int mCameraState = mStopState;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate");
        mStart = findViewById(R.id.start);
        mStop = findViewById(R.id.stop);
        mPause = findViewById(R.id.pause);
        mStart.setOnClickListener(this);
        mStop.setOnClickListener(this);
        mPause.setOnClickListener(this);

        mCamera2Helper = new Camera2Helper(this);
        mAvcEncoder = new AvcEncoder(mWidth, mHeight, mFramerate, mBitrate);
        mRtpSenderWrapper = new RtpSenderWrapper(IP, PORT, false);
        mCamera2Helper.setImageDataListener(new Camera2Helper.ImageDataListener() {
            @Override
            public void OnImageDataListener(byte[] data) {
                 Log.i(TAG, "OnImageDataListener");
                int ret = mAvcEncoder.offerEncoder(data, mH264Data);
                if (ret > 0) {
                    mRtpSenderWrapper.sendAvcPacket(mH264Data, 0, ret, 0);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        if (mCameraState != mStopState) {
            mCamera2Helper.closeCamera();
            mCameraState = mStopState;
            mPause.setText("暂停");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        if (mAvcEncoder != null) {
            mAvcEncoder.close();
            mAvcEncoder = null;
        }

        if (mRtpSenderWrapper != null) {
            mRtpSenderWrapper.close();
            mRtpSenderWrapper = null;
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.start) {
            if (mCameraState == mStopState) {
                mCamera2Helper.startCamera(mWidth,mHeight);
                mCameraState = mStartState;
            }
        } else if (viewId == R.id.stop) {
            if (mCameraState == mStopState) {
                return;
            }
            mCamera2Helper.closeCamera();
            mCameraState = mStopState;
            mPause.setText("暂停");
        } else if (viewId == R.id.pause) {
            if (mCameraState == mPlayState || mCameraState == mStartState) {
                mCamera2Helper.pauseCamera();
                mCameraState = mPauseState;
                mPause.setText("播放");
            } else if (mCameraState == mPauseState) {
                mCamera2Helper.playCamera();
                mCameraState = mPlayState;
                mPause.setText("暂停");
            }
        }
    }
}
