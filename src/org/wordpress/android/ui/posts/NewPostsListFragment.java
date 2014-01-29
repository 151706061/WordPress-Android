package org.wordpress.android.ui.posts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.models.Blog;
import org.wordpress.android.models.Post;
import org.wordpress.android.ui.posts.adapters.PostListAdapter;
import org.wordpress.android.util.ListScrollPositionManager;
import org.wordpress.android.util.WPAlertDialogFragment;
import org.xmlrpc.android.XMLRPCClient;
import org.xmlrpc.android.XMLRPCException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class NewPostsListFragment extends ListFragment {
    /** Called when the activity is first created. */
    private long mSelectedID = -1;
    private OnPostSelectedListener mOnPostSelectedListener;
    private OnRefreshListener mOnRefreshListener;
    private OnPostActionListener mOnPostActionListener;
    private PostsActivity mParentActivity;
    private ListScrollPositionManager mListScrollPositionManager;
    private PostListAdapter mPostListAdapter;

    public String errorMsg = "";
    public boolean isPage = false;

    private static final int MENU_GROUP_PAGES = 2, MENU_GROUP_POSTS = 0, MENU_GROUP_DRAFTS = 1;
    private static final int MENU_ITEM_EDIT = 0, MENU_ITEM_DELETE = 1, MENU_ITEM_PREVIEW = 2, MENU_ITEM_SHARE = 3, MENU_ITEM_ADD_COMMENT = 4;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Bundle extras = getActivity().getIntent().getExtras();
        if (extras != null) {
            isPage = extras.getBoolean("viewPages");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.empty_listview, container, false);
        return v;
    }

    private PostListAdapter getPostListAdapter() {
        if (mPostListAdapter == null) {
            mPostListAdapter = new PostListAdapter(getActivity(), WordPress.getCurrentBlog().getLocalTableBlogId(), isPage);
        }

        return mPostListAdapter;
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);
        TextView textview = (TextView) getListView().getEmptyView();
        if (textview != null) {
            if (isPage) {
                textview.setText(getText(R.string.pages_empty_list));
            } else {
                textview.setText(getText(R.string.posts_empty_list));
            }
            textview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mParentActivity.newPost();
                }
            });
        }
        getListView().setAdapter(getPostListAdapter());
        getPostListAdapter().loadPosts();
        mListScrollPositionManager = new ListScrollPositionManager(getListView(), true);
    }

    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            // check that the containing activity implements our callback
            mOnPostSelectedListener = (OnPostSelectedListener) activity;
            mOnRefreshListener = (OnRefreshListener) activity;
            mOnPostActionListener = (OnPostActionListener) activity;
        } catch (ClassCastException e) {
            activity.finish();
            throw new ClassCastException(activity.toString()
                    + " must implement Callback");
        }
    }

    public void onResume() {
        super.onResume();
        mParentActivity = (PostsActivity) getActivity();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Post post = new Post(WordPress.getCurrentBlog().getLocalTableBlogId(), mSelectedID, isPage);

        if (post.getId() < 0) {
            if (!getActivity().isFinishing()) {
                FragmentTransaction ft = getFragmentManager()
                        .beginTransaction();
                WPAlertDialogFragment alert = WPAlertDialogFragment
                        .newInstance(getString(R.string.post_not_found));
                alert.show(ft, "alert");
            }
            return false;
        }

        int itemGroupID = item.getGroupId();
        /* Switch on the ID of the item, to get what the user selected. */
        if (itemGroupID == MENU_GROUP_POSTS || itemGroupID == MENU_GROUP_PAGES || itemGroupID == MENU_GROUP_DRAFTS ) {
            switch (item.getItemId()) {
            case MENU_ITEM_EDIT:
                Intent i2 = new Intent(getActivity().getApplicationContext(),
                        EditPostActivity.class);
                i2.putExtra("postID", mSelectedID);
                i2.putExtra("id", WordPress.getCurrentBlog().getLocalTableBlogId());
                
                if( itemGroupID == MENU_GROUP_PAGES ){ //page synced with the server
                    i2.putExtra("isPage", true);
                } else if ( itemGroupID == MENU_GROUP_DRAFTS ) { //local draft
                    if (isPage)
                        i2.putExtra("isPage", true);
                    i2.putExtra("localDraft", true);
                }
                
                startActivityForResult(i2, 0);
                return true;
            case MENU_ITEM_DELETE:
                mOnPostActionListener.onPostAction(PostsActivity.POST_DELETE, post);
                return true;
            case MENU_ITEM_PREVIEW:
                Intent i = new Intent(getActivity(), PreviewPostActivity.class);
                i.putExtra("isPage", itemGroupID == MENU_GROUP_PAGES ? true : false);
                i.putExtra("postID", mSelectedID);
                i.putExtra("blogID", WordPress.getCurrentBlog().getLocalTableBlogId());
                startActivity(i);
                return true;
            case MENU_ITEM_SHARE:
                mOnPostActionListener.onPostAction(PostsActivity.POST_SHARE, post);
                return true;
            case MENU_ITEM_ADD_COMMENT:
                mOnPostActionListener.onPostAction(PostsActivity.POST_COMMENT, post);
                return true;
            default:
                return false;
            }
        }
        return false;
    }

    public class getRecentPostsTask extends
            AsyncTask<List<?>, Void, Boolean> {

        Context ctx;
        boolean isPage, loadMore;

        protected void onPostExecute(Boolean result) {
            if (isCancelled() || !result) {
                mOnRefreshListener.onRefresh(false);
                if (getActivity() == null)
                    return;
                if (errorMsg != "" && !getActivity().isFinishing()) {
                    FragmentTransaction ft = getFragmentManager()
                            .beginTransaction();
                    WPAlertDialogFragment alert = WPAlertDialogFragment
                            .newInstance(isPage ? getString(R.string.error_refresh_pages) : getString(R.string.error_refresh_posts));
                    try {
                        alert.show(ft, "alert");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    errorMsg = "";
                }
                return;
            }

            mOnRefreshListener.onRefresh(false);
            if (isAdded()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        
                        @Override
                        public void run() {
                            loadPosts(loadMore);
                        }
                    });
                }
            }
        }

        @Override
        protected Boolean doInBackground(List<?>... args) {
            boolean success = false;
            List<?> arguments = args[0];
            WordPress.currentBlog = (Blog) arguments.get(0);
            isPage = (Boolean) arguments.get(1);
            int recordCount = (Integer) arguments.get(2);
            loadMore = (Boolean) arguments.get(3);
            XMLRPCClient client = new XMLRPCClient(WordPress.currentBlog.getUrl(),
                    WordPress.currentBlog.getHttpuser(),
                    WordPress.currentBlog.getHttppassword());

            Object[] result = null;
            Object[] params = { WordPress.getCurrentBlog().getRemoteBlogId(),
                    WordPress.currentBlog.getUsername(),
                    WordPress.currentBlog.getPassword(), recordCount };
            try {
                result = (Object[]) client.call((isPage) ? "wp.getPages"
                        : "metaWeblog.getRecentPosts", params);
                if (result != null) {
                    if (result.length > 0) {
                        success = true;
                        Map<?, ?> contentHash = new HashMap<Object, Object>();
                        List<Map<?, ?>> dbVector = new Vector<Map<?, ?>>();

                        if (!loadMore) {
                            WordPress.wpDB.deleteUploadedPosts(
                                    WordPress.getCurrentBlog().getLocalTableBlogId(), isPage);
                        }

                        for (int ctr = 0; ctr < result.length; ctr++) {
                            Map<String, Object> dbValues = new HashMap<String, Object>();
                            contentHash = (Map<?, ?>) result[ctr];
                            dbValues.put("blogID",
                                    WordPress.getCurrentBlog().getRemoteBlogId());
                            dbVector.add(ctr, contentHash);
                        }

                        WordPress.wpDB.savePosts(dbVector,
                                WordPress.getCurrentBlog().getLocalTableBlogId(), isPage);
                    }
                }
            } catch (XMLRPCException e) {
                errorMsg = e.getMessage();
                if (errorMsg == null)
                    errorMsg = getResources().getString(R.string.error_generic);
            }

            return success;
        }
    }

    private void loadPosts(boolean loadMore) {
    }

    public interface OnPostSelectedListener {
        public void onPostSelected(Post post);
    }

    public interface OnRefreshListener {
        public void onRefresh(boolean start);
    }

    public interface OnPostActionListener {
        public void onPostAction(int action, Post post);
    }
}
