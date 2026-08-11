package com.agh21331.smartmeal;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 500;
    private static final int FILE_CHOOSER = 501;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(248,250,252));
        getWindow().setNavigationBarColor(Color.rgb(248,250,252));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        webView = new WebView(this);
        setContentView(webView);
        setupWebView();
        setupTts();
        requestRuntimePermissions();
        webView.loadUrl("file:///android_asset/web/index.html");
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " SmartMealAndroid/1.0");

        webView.setBackgroundColor(Color.WHITE);
        webView.addJavascriptInterface(new NativeVoiceBridge(), "AndroidVoice");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host == null || host.equals("smart-eating-delta.vercel.app") || host.equals("localhost")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())); } catch (Exception ignored) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                fileCallback = callback;
                try {
                    Intent gallery = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    gallery.addCategory(Intent.CATEGORY_OPENABLE);
                    gallery.setType("image/*");

                    Intent camera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    File photo = createTempImage();
                    cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", photo);
                    camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri);
                    camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    Intent chooser = new Intent(Intent.ACTION_CHOOSER);
                    chooser.putExtra(Intent.EXTRA_INTENT, gallery);
                    chooser.putExtra(Intent.EXTRA_TITLE, "Choose food photo");
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                    startActivityForResult(chooser, FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    if (fileCallback != null) fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    return false;
                }
            }

            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    ArrayList<String> allowed = new ArrayList<>();
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && hasPermission(Manifest.permission.RECORD_AUDIO)) allowed.add(resource);
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) && hasPermission(Manifest.permission.CAMERA)) allowed.add(resource);
                    }
                    if (!allowed.isEmpty()) request.grant(allowed.toArray(new String[0])); else request.deny();
                });
            }

            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                result.cancel();
                return true;
            }
        });
    }

    private File createTempImage() throws IOException {
        File dir = new File(getCacheDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create image cache");
        return File.createTempFile("smartmeal_", ".jpg", dir);
    }

    private void setupTts() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
            }
        });
    }

    private void requestRuntimePermissions() {
        ArrayList<String> needed = new ArrayList<>();
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) needed.add(Manifest.permission.RECORD_AUDIO);
        if (!hasPermission(Manifest.permission.CAMERA)) needed.add(Manifest.permission.CAMERA);
        if (!needed.isEmpty()) ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
    }

    private boolean hasPermission(String p) { return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED; }

    private void startNativeVoice(String languageTag) {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMISSIONS);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            dispatchVoiceError("unsupported");
            return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        final String localeTag = languageTag == null ? "ar-DZ" : languageTag;
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rmsdB) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() {}
            public void onError(int error) { dispatchVoiceError(String.valueOf(error)); }
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = matches != null && !matches.isEmpty() ? matches.get(0) : "";
                dispatchVoiceResult(text);
            }
            public void onPartialResults(Bundle partialResults) {}
            public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        try { speechRecognizer.startListening(intent); }
        catch (Exception e) { dispatchVoiceError("start-failed"); }
    }

    private void dispatchVoiceResult(String text) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.onNativeVoiceResult(" + JSONObject.quote(text) + ");window.onNativeVoiceComplete&&window.onNativeVoiceComplete();", null);
        });
    }

    private void dispatchVoiceError(String code) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.onNativeVoiceError(" + JSONObject.quote(code) + ");window.onNativeVoiceComplete&&window.onNativeVoiceComplete();", null);
        });
    }

    private void speak(String text, String languageTag) {
        if (tts == null || text == null || text.trim().isEmpty()) return;
        Locale locale = Locale.forLanguageTag(languageTag == null ? "ar-SA" : languageTag);
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH);
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smartmeal");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER || fileCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            } else if (cameraUri != null) {
                results = new Uri[]{cameraUri};
            }
        }
        fileCallback.onReceiveValue(results);
        fileCallback = null;
        cameraUri = null;
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    public class NativeVoiceBridge {
        @android.webkit.JavascriptInterface public void startListening(String languageTag) { startNativeVoice(languageTag); }
        @android.webkit.JavascriptInterface public void speak(String text, String languageTag) { speak(text, languageTag); }
        @android.webkit.JavascriptInterface public boolean isNative() { return true; }
    }
}
