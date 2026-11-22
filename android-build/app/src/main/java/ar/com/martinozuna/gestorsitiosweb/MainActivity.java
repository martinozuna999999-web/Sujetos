package ar.com.martinozuna.gestorsitiosweb;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.OutputStream;

public final class MainActivity extends Activity {
    private static final String SITE = "https://gestordesitios.netlify.app/";
    private static final String HOST = "gestordesitios.netlify.app";
    private static final int PICK_FILE=411, SAVE_FILE=412;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingBytes;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(20,42,49));
        getWindow().setNavigationBarColor(Color.rgb(20,42,49));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(20,42,49));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        // Android 15/16: mantener toda la interfaz fuera de la barra de estado
        // y de los controles de navegación del sistema.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets safe = insets.getInsets(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                );
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
                if (lp.topMargin != safe.top || lp.bottomMargin != safe.bottom) {
                    lp.topMargin = safe.top;
                    lp.bottomMargin = safe.bottom;
                    web.setLayoutParams(lp);
                }
                return insets;
            });
            root.requestApplyInsets();
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // Tamaño de lectura y viewport adaptados a pantallas móviles pequeñas.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(85);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setInitialScale(90);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.addJavascriptInterface(new Saver(),"GestorAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                Uri address = Uri.parse(url);
                if ("https".equalsIgnoreCase(address.getScheme()) &&
                    HOST.equalsIgnoreCase(address.getHost())) {
                    view.evaluateJavascript("(() => {const id='gestor-android-ajustes';if(document.getElementById(id))return;const style=document.createElement('style');style.id=id;style.textContent=\"#nl-badge-frame,iframe#nl-badge-frame{display:none!important;visibility:hidden!important;pointer-events:none!important;}\\n@media (max-width: 720px) {\\n html,body {max-width:100%!important;overflow-x:hidden!important;}\\n .login-card {max-width:100%!important;padding:24px 22px!important;}\\n .login h1 {font-size:25px!important;line-height:1.15!important;}\\n .login .login-signature {font-size:15px!important;}\\n .login-card .muted {font-size:15px!important;}\\n .login-card form {margin-top:16px!important;}\\n .login-card .field {margin-bottom:12px!important;}\\n .login-card label {font-size:15px!important;}\\n .login-card input {font-size:16px!important;min-height:42px!important;}\\n .login-card .btn {font-size:16px!important;min-height:42px!important;}\\n .topbar {padding:12px 14px!important;gap:8px 12px!important;}\\n .topbar .brand {gap:10px!important;}\\n .topbar .brand-title {font-size:21px!important;line-height:1.14!important;white-space:normal!important;}\\n .topbar .brand-byline {font-size:15px!important;}\\n .topbar .brand-symbol {width:39px!important;height:39px!important;font-size:22px!important;}\\n #greeting {font-size:17px!important;}\\n .topbar .btn,.top-actions .btn {font-size:15px!important;min-height:40px!important;padding:8px 10px!important;}\\n .top-actions {gap:5px!important;}\\n .sidebar button {font-size:15px!important;min-height:40px!important;padding:8px 9px!important;gap:6px!important;}\\n .sidebar {gap:4px!important;}\\n .form-panel {padding:17px 15px!important;}\\n .panel-head h2,.modal h2 {font-size:22px!important;}\\n .panel-box h3,.subheading {font-size:18px!important;}\\n .form-panel label {font-size:15px!important;}\\n .form-panel input,.form-panel select,.form-panel textarea {font-size:16px!important;}\\n .form-panel .btn,.help-trigger {font-size:15px!important;min-height:41px!important;padding:8px 10px!important;}\\n .preview-bar strong {font-size:16px!important;}\\n .device button {font-size:15px!important;min-height:38px!important;}\\n .note,.image-spec,.small,.panel-head p {font-size:14px!important;}\\n}\";(document.head||document.documentElement).appendChild(style);})()",null);
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (allowed(request.getUrl())) return false;
                external(request.getUrl());
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback!=null) fileCallback.onReceiveValue(null);
                fileCallback=callback;
                try { startActivityForResult(params.createIntent(),PICK_FILE); }
                catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null; toast("No se pudo seleccionar el archivo.");}
                return true;
            }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message resultMsg) {
                WebView popup=new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        external(r.getUrl()); v.destroy(); return true;
                    }
                    @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon) {
                        external(Uri.parse(u));v.stopLoading();v.destroy();
                    }
                });
                WebView.WebViewTransport transport=(WebView.WebViewTransport)resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        web.setDownloadListener((url,agent,disposition,mime,length)->{
            String filename=URLUtil.guessFileName(url,disposition,mime);
            if(url.startsWith("blob:")||url.startsWith("data:")){
                String js="(async()=>{try{const r=await fetch("+JSONObject.quote(url)+");const b=await r.blob();"+
                    "const f=new FileReader();f.onload=()=>GestorAndroid.guardar(String(f.result),"+
                    JSONObject.quote(filename)+");f.readAsDataURL(b);}catch(e){alert('No se pudo descargar el archivo.')}})();";
                web.evaluateJavascript(js,null);
            }else if(url.startsWith("https://")){
                try {
                    DownloadManager.Request dr=new DownloadManager.Request(Uri.parse(url));
                    dr.setTitle(filename);
                    dr.setMimeType(mime);
                    dr.addRequestHeader("User-Agent",agent);
                    dr.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    dr.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,filename);
                    ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(dr);
                    toast("Descargando "+filename);
                } catch(Exception e){external(Uri.parse(url));}
            }
        });
        web.loadUrl(SITE);
    }
    private boolean allowed(Uri uri){
        String scheme=uri.getScheme();
        if("about".equals(scheme)||"blob".equals(scheme)||"data".equals(scheme))return true;
        return "https".equalsIgnoreCase(scheme)&&HOST.equalsIgnoreCase(uri.getHost());
    }
    private void external(Uri uri){
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
        catch(ActivityNotFoundException e){toast("No se puede abrir el enlace.");}
    }
    private void toast(String str){runOnUiThread(()->Toast.makeText(this,str,Toast.LENGTH_LONG).show());}
    private class Saver {
        @JavascriptInterface public void guardar(String dataUrl,String filename){
            try {
                int comma=dataUrl.indexOf(',');
                if(comma<0)throw new Exception("Formato incorrecto");
                byte[] bytes=Base64.decode(dataUrl.substring(comma+1),Base64.DEFAULT);
                String mime=dataUrl.substring(5,comma).split(";")[0];
                String name=filename.replaceAll("[^a-zA-Z0-9._-]","_");
                if(name.isEmpty())name="gestor-proyecto.zip";
                if(Build.VERSION.SDK_INT>=29){
                    ContentValues values=new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
                    values.put(MediaStore.MediaColumns.MIME_TYPE,mime.isEmpty()?"application/octet-stream":mime);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);
                    Uri destination=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                    if(destination==null)throw new Exception("No se pudo abrir Descargas");
                    try(OutputStream out=getContentResolver().openOutputStream(destination)){
                        if(out==null)throw new Exception("Sin acceso a Descargas");
                        out.write(bytes);
                    }
                    toast("Archivo guardado en Descargas.");
                }else{
                    pendingBytes=bytes;
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(mime.isEmpty()?"application/octet-stream":mime);
                    intent.putExtra(Intent.EXTRA_TITLE,name);
                    runOnUiThread(()->startActivityForResult(intent,SAVE_FILE));
                }
            }catch(Exception e){toast("No se pudo guardar: "+e.getMessage());}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_FILE && fileCallback!=null){
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
            fileCallback=null;
        }else if(request==SAVE_FILE && pendingBytes!=null){
            if(result==RESULT_OK && data!=null && data.getData()!=null){
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                    if(out!=null){out.write(pendingBytes);toast("Archivo guardado.");}
                }catch(Exception e){toast("Error al guardar el archivo.");}
            }
            pendingBytes=null;
        }
    }
    @Override public void onBackPressed(){
        if(web!=null&&web.canGoBack())web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(web!=null)web.destroy();
        super.onDestroy();
    }
}
