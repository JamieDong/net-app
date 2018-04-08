package com.warrior.rx;

import android.content.Context;

import com.warrior.net.RestClient;
import com.warrior.net.RestCreator;
import com.warrior.net.callback.IError;
import com.warrior.net.callback.IFailure;
import com.warrior.net.callback.IRequest;
import com.warrior.net.callback.ISuccess;
import com.warrior.ui.LoaderStyle;

import java.io.File;
import java.util.WeakHashMap;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * Created by Jamie
 */

public final class RxRestClientBuilder {
    private static final WeakHashMap<String, Object> PARAMS = RestCreator.getParams();
    private String mUrl = null;
    private IRequest mIRequest = null;
    private ISuccess mISuccess = null;
    private IFailure mIFailure = null;
    private IError mIError = null;
    private RequestBody mBody = null;
    private Context mContext = null;
    private LoaderStyle mLoaderStyle = null;
    private File mFile = null;
    private String mDownloadDir = null;
    private String mExtension = null;
    private String mName = null;

    RxRestClientBuilder() {
    }

    public final RxRestClientBuilder url(String url) {
        this.mUrl = url;
        return this;
    }

    public final RxRestClientBuilder params(WeakHashMap<String, Object> params) {
        PARAMS.putAll(params);
        return this;
    }

    public final RxRestClientBuilder params(String key, Object value) {
        PARAMS.put(key, value);
        return this;
    }

    public final RxRestClientBuilder file(File file) {
        this.mFile = file;
        return this;
    }

    public final RxRestClientBuilder file(String file) {
        this.mFile = new File(file);
        return this;
    }

    public final RxRestClientBuilder name(String name) {
        this.mName = name;
        return this;
    }

    public final RxRestClientBuilder dir(String dir) {
        this.mDownloadDir = dir;
        return this;
    }

    public final RxRestClientBuilder extension(String extension) {
        this.mExtension = extension;
        return this;
    }

    public final RxRestClientBuilder raw(String raw) {
        this.mBody = RequestBody.create(MediaType.parse("application/json;charset=UTF-8"), raw);
        return this;
    }

    public final RxRestClientBuilder onRequest(IRequest iRequest) {
        this.mIRequest = iRequest;
        return this;
    }

    public final RxRestClientBuilder success(ISuccess iSuccess) {
        this.mISuccess = iSuccess;
        return this;
    }

    public final RxRestClientBuilder failure(IFailure iFailure) {
        this.mIFailure = iFailure;
        return this;
    }

    public final RxRestClientBuilder error(IError iError) {
        this.mIError = iError;
        return this;
    }

    public final RxRestClientBuilder loader(Context context, LoaderStyle style) {
        this.mContext = context;
        this.mLoaderStyle = style;
        return this;
    }

    public final RxRestClientBuilder loader(Context context) {
        this.mContext = context;
        this.mLoaderStyle = LoaderStyle.BallClipRotatePulseIndicator;
        return this;
    }

    public final RxRestClient build() {
        return new RxRestClient(mUrl, PARAMS,
                mDownloadDir, mExtension, mName,
                mIRequest, mISuccess, mIFailure,
                mIError, mBody, mFile, mContext,
                mLoaderStyle);
    }
}
