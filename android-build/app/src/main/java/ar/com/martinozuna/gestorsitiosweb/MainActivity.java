package ar.com.martinozuna.gestorsitiosweb;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
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
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.addJavascriptInterface(new Saver(),"GestorAndroid");
        web.setWebViewClient(new WebViewClient() {
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
