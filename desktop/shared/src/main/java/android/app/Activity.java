package android.app;

import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class Activity extends ContextWrapper {
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = -1;

    private Intent intent = new Intent();
    private int resultCode = RESULT_CANCELED;
    private Intent resultData = null;
    private volatile Function0<Unit> onFinishCallback = null;

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent newIntent) {
        this.intent = newIntent;
    }

    public int getResultCode() {
        return resultCode;
    }

    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }

    public Intent getResultData() {
        return resultData;
    }

    public void setResultData(Intent resultData) {
        this.resultData = resultData;
    }

    public Function0<Unit> getOnFinishCallback() {
        return onFinishCallback;
    }

    public void setOnFinishCallback(Function0<Unit> callback) {
        this.onFinishCallback = callback;
    }

    public void onCreate(Bundle savedInstanceState) {}
    public void onStart() {}
    public void onResume() {}
    public void onPause() {}
    public void onStop() {}
    public void onDestroy() {}
    public void onNewIntent(Intent intent) {}

    public final void setResult(int resultCode) {
        this.resultCode = resultCode;
        this.resultData = null;
    }

    public final void setResult(int resultCode, Intent data) {
        this.resultCode = resultCode;
        this.resultData = data;
    }

    public void finish() {
        Function0<Unit> cb = onFinishCallback;
        if (cb != null) {
            cb.invoke();
        }
    }
}

