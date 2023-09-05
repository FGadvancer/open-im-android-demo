package io.openim.android.ouicore.base;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.ViewDataBinding;

import io.openim.android.ouicore.utils.ActivityManager;

public class BasicActivity<T extends ViewDataBinding> extends AppCompatActivity {
    boolean isRecycle;
    protected T view;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ActivityManager.push(this);
        requestedOrientation();
        super.onCreate(savedInstanceState);
        setLightStatus();
    }

    public void viewBinding(T viewDataBinding) {
        view = viewDataBinding;
        setContentView(view.getRoot());
        view.setLifecycleOwner(this);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    protected void requestedOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    protected void onPause() {
        fasterDestroy();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        fasterDestroy();
        super.onDestroy();
    }

    void fasterDestroy() {
        if (isFinishing() && !isRecycle) {
            isRecycle = true;
            ActivityManager.remove(this);
            view.unbind();
            recycle();
        }
    }

    protected void recycle() {

    }

    protected void setLightStatus() {
        Window window = getWindow();
        //After LOLLIPOP not translucent status bar
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        //Then call setStatusBarColor.
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }
}
