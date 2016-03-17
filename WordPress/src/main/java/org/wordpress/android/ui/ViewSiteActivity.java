
package org.wordpress.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.models.Blog;
import org.wordpress.android.stores.store.AccountStore;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.WPWebViewClient;
import org.wordpress.android.util.helpers.WPWebChromeClient;
import org.wordpress.passcodelock.AppLockManager;

import java.lang.reflect.Type;
import java.util.Map;

import javax.inject.Inject;

/**
 * Activity to view the WordPress blog in a WebView
 */
public class ViewSiteActivity extends WebViewActivity {
    @Inject AccountStore mAccountStore;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getApplication()).component().inject(this);
        ActivityId.trackLastActivity(ActivityId.VIEW_SITE);
    }

    @Override
    protected void configureWebView() {
        Blog blog = WordPress.getCurrentBlog();
        if (blog == null) {
            return;
        }

        mWebView.setWebViewClient(new WPWebViewClient(blog));
        mWebView.setWebChromeClient(new WPWebChromeClient(this, (ProgressBar) findViewById(R.id.progress_bar)));
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    @Override
    public void finish() {
        super.finish();
        ActivityLauncher.slideOutToRight(this);
    }

    @Override
    protected void loadContent() {
        Blog blog = WordPress.getCurrentBlog();
        if (blog == null) {
            Toast.makeText(this, getResources().getText(R.string.blog_not_found),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setTitle(StringUtils.unescapeHTML(blog.getBlogName()));

        String siteURL = null;
        Gson gson = new Gson();
        Type type = new TypeToken<Map<?, ?>>() { }.getType();
        Map<?, ?> blogOptions = gson.fromJson(blog.getBlogOptions(), type);
        if (blogOptions != null) {
            Map<?, ?> homeURLMap = (Map<?, ?>) blogOptions.get("home_url");
            if (homeURLMap != null) {
                siteURL = homeURLMap.get("value").toString();
            }
        }
        // Try to guess the URL of the site if blogOptions is null (blog not added to the app)
        if (siteURL == null) {
            siteURL = blog.getUrl().replace("/xmlrpc.php", "");
        }

        // Login to the blog and load the specified URL.
        String authenticationUrl = WPWebViewActivity.getBlogLoginUrl(blog);

        String postData = WPWebViewActivity.getAuthenticationPostData(
                authenticationUrl, siteURL, blog.getUsername(), blog.getPassword(),
                mAccountStore.getAccessToken()
        );

        mWebView.postUrl(authenticationUrl, postData.getBytes());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.webview, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (mWebView == null) {
            return false;
        }

        int i1 = item.getItemId();
        if (i1 == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (i1 == R.id.menu_refresh) {
            mWebView.reload();
            return true;
        } else if (i1 == R.id.menu_share) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, mWebView.getUrl());
            startActivity(Intent.createChooser(share, getResources().getText(R.string.share_link)));
            return true;
        } else if (i1 == R.id.menu_browser) {
            String url = mWebView.getUrl();
            if (url != null) {
                Uri uri = Uri.parse(url);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(uri);
                startActivity(i);
                AppLockManager.getInstance().setExtendedTimeout();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
