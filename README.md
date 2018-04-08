# 傻瓜式网络框架(Retrofit+RxJava)

*前言：项目需要重构网络框架，跟着公司的一位同事一起弄的，有些地方还是理解的不太透彻，所以有此博文记录下以后便于学习。*

######框架要灵活的话，使用什么样的设计模式会比较好呢？
需求是需要传入参数并且没有顺序的要求，传入什么用什么的的这种模式的话，那建造者模式是再好不过了。

- 首先Retrofit的使用必须要有一个接口就是一个Service，这个Service里面定义了一系列的方法，用来具体的请求
```
//
public interface RestService {
    @GET
    Call<String> get(@Url String url, @QueryMap WeakHashMap<String, Object> params);

    @FormUrlEncoded
    @POST
    Call<String> post(@Url String url, @FieldMap WeakHashMap<String, Object> params);

    //下载需要加上Streaming注解：边下载，边写入。避免内存溢出。后面写的时候会以异步的线程去处理。
    @Streaming
    @GET
    Call<ResponseBody> download(@Url String url, @QueryMap WeakHashMap<String, Object> params);

    //@PUT、@DELETE。。。等等方法
}
```
- RestCreator 单例模式 采用静态内部类Holder的方式
```
///
public final class RestCreator {

    /**
     * 参数容器
     */
    private static final class ParamsHolder {
        private static final WeakHashMap<String, Object> PARAMS = new WeakHashMap<>();
    }

    public static WeakHashMap<String, Object> getParams() {
        return ParamsHolder.PARAMS;
    }

    /**
     * 构建全局Retrofit客户端
     */
    private static final class RetrofitHolder {
        private static final String BASE_URL = Latte.getConfiguration(ConfigKeys.API_HOST);
        private static final Retrofit RETROFIT_CLIENT = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(OKHttpHolder.OK_HTTP_CLIENT)//okhttp惰性初始化
                .addConverterFactory(ScalarsConverterFactory.create())//转换器，可以返回String类型
                .build();
    }
}
```
######RestClient使用建造者模式
- RestClientBuilder 把建造者和它的宿主分隔开来，就不用静态内部类了
```
//
private static final WeakHashMap<String, Object> PARAMS = RestCreator.getParams();
    private String mUrl = null;
    private IRequest mIRequest = null;
    private ISuccess mISuccess = null;
    private IFailure mIFailure = null;

    RestClientBuilder() {
    }
    
    public final RestClientBuilder success(ISuccess iSuccess) {
        this.mISuccess = iSuccess;
        return this;
    }

    public final RestClientBuilder failure(IFailure iFailure) {
        this.mIFailure = iFailure;
        return this;
    }
}
```

- 这个RestClient在每次build的时候，都会生成全新的实例，而这里面的参数是一次构建完毕，绝不允许更改的。所以声明参数的时候尽量加上关键字final，这样能保证它每次传值的原子性，这样在多线程里面是比较安全的做法。
```
//进行请求的具体实现类
public class RestClient {
    private static final WeakHashMap<String, Object> PARAMS = RestCreator.getParams();
    private final String URL;
    private final IRequest REQUEST;

    public static RestClientBuilder builder() {
        return new RestClientBuilder();
    }
    
    public final void get() {
        request(HttpMethod.GET);
    }

    public final void post() {
        if (BODY == null) {
            request(HttpMethod.POST);
        } else {
            if (!PARAMS.isEmpty()) {
                throw new RuntimeException("params must be null!");
            }
            request(HttpMethod.POST_RAW);
        }
    }
}
```
Ps：建造者模式写起来真的好烦好烦好烦啊啊啊！！！有没有啥工具能一次生成它- -、

######创建Package callback 里面主要处理一些回调(请求成功、失败、异常)
![callback目录结构](https://upload-images.jianshu.io/upload_images/11184437-79588776dd6272e7.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

- RequestCallbacks实现retrofit2的Callback接口
```
public final class RequestCallbacks implements Callback<String> {
    @Override
    public void onResponse(Call<String> call, Response<String> response) {
        if (response.isSuccessful()) {
            if (call.isExecuted()) {
                if (SUCCESS != null) {
                    SUCCESS.onSuccess(response.body());
                }
            }
        } else {
            if (ERROR != null) {
                ERROR.onError(response.code(), response.message());
            }
        }

        onRequestFinish();
    }
}
```

###### DownLoad

因为前面提到了为了避免内存溢出所以加上了`@Streaming`注解，所以是边下载，边写入。所以下载步骤要放在异步的线程去处理
- SaveFileTask
```
public class SaveFileTask extends AsyncTask<Object, Void, File> {
    @Override
    protected File doInBackground(Object... params) {
        String downloadDir = (String) params[0];
        String extension = (String) params[1];
        final ResponseBody body = (ResponseBody) params[2];
        final String name = (String) params[3];
        final InputStream is = body.byteStream();
        if (downloadDir == null || downloadDir.equals("")) {
            downloadDir = "down_loads";
        }
        if (extension == null || extension.equals("")) {
            extension = "";
        }
        if (name == null) {
            return FileUtil.writeToDisk(is, downloadDir, extension.toUpperCase(), extension);
        } else {
            return FileUtil.writeToDisk(is, downloadDir, name);
        }
    }
}
```

- DownloadHandler
```
public class DownloadHandler {
    public final void handleDownload() {
        if (REQUEST != null) {
            REQUEST.onRequestStart();
        }

        RestCreator
                .getRestService()
                .download(URL, PARAMS)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            final ResponseBody responseBody = response.body();
                            final SaveFileTask task = new SaveFileTask(REQUEST, SUCCESS);
                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                                    DOWNLOAD_DIR, EXTENSION, responseBody, NAME);

                            //这里一定要注意判断，否则文件下载不全
                            if (task.isCancelled()) {
                                if (REQUEST != null) {
                                    REQUEST.onRequestEnd();
                                }
                            }
                        } else {
                            if (ERROR != null) {
                                ERROR.onError(response.code(), response.message());
                            }
                        }
                        RestCreator.getParams().clear();
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        if (FAILURE != null) {
                            FAILURE.onFailure();
                            RestCreator.getParams().clear();
                        }
                    }
                });
    }
}
```
######RxJava简单整合
改造RestService
Call改成Observable
Observable：可观察对象(基于Observable来进行相应的链式、响应式、观察式的操作)
```
//
public interface RxRestService {
    @GET
    Observable<String> get(@Url String url, @QueryMap WeakHashMap<String, Object> params);

    @FormUrlEncoded
    @POST
    Observable<String> post(@Url String url, @FieldMap WeakHashMap<String, Object> params);

    //下载需要加上Streaming注解：边下载，边写入。避免内存溢出。后面写的时候会以异步的线程去处理。
    @Streaming
    @GET
    Observable<ResponseBody> download(@Url String url, @QueryMap WeakHashMap<String, Object> params);

    //@PUT、@DELETE。。。等等方法
}
```
同样RestClient改造：Call改成Observable
`private final ISuccess SUCCESS;`
`private final IFailure FAILURE;`
`private final IError ERROR;`
像这些就不需要了，因为RxJava把请求的应用生命周期已经涵盖进去了。
```
public class RxRestClient {
    public final Observable<String> get() {
        return request(HttpMethod.GET);
    }

    public final Observable<String> post() {
        if (BODY == null) {
            return request(HttpMethod.POST);
        } else {
            if (!PARAMS.isEmpty()) {
                throw new RuntimeException("params must be null!");
            }
            return request(HttpMethod.POST_RAW);
        }
    }
}
```

RestCreator 添加俩个方法，用来实现RxJava接口
```
/**
     * RxService接口
     */
    private static final class RxRestServiceHolder {
        private static final RxRestService REST_SERVICE =
                RetrofitHolder.RETROFIT_CLIENT.create(RxRestService.class);
    }

    public static RxRestService getRxRestService() {
        return RxRestServiceHolder.REST_SERVICE;
    }
```

- 调用RxJava请求网络的俩种方式

```
void onCallRxGet(){
        final String url = "http://127.0.0.1/index.jsp";
        final WeakHashMap<String,Object> params = new WeakHashMap<>();
        final Observable<String> observable = RestCreator.getRxRestService().get(url,params);
        observable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(String s) {
                          Toast.makeText(getContext,s,Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }
```
其中`.subscribenOn()`代表处理后台网络逻辑所处的线程
有俩种选择`.subscribenOn(Schedulers.io())`或`.subscribenOn(Schedulers.newThread())`
RxAndroid `.observeOn(AndroidSchedulers.mainThread())` 意思是处理结果观察到了，在Android的主线程里进行处理。

```
void onCallRxRestClient(){
        final String url = "";
        RxRestClient.builder()
                .url(url)
                .build()
                .get()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(String s) {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });

    }
```
