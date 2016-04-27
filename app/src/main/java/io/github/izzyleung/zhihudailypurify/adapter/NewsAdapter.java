package io.github.izzyleung.zhihudailypurify.adapter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.Stream;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.github.izzyleung.zhihudailypurify.R;
import io.github.izzyleung.zhihudailypurify.ZhihuDailyPurifyApplication;
import io.github.izzyleung.zhihudailypurify.bean.DailyNews;
import io.github.izzyleung.zhihudailypurify.bean.Question;
import io.github.izzyleung.zhihudailypurify.support.Check;
import io.github.izzyleung.zhihudailypurify.support.Constants;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.CardViewHolder> {
    private List<DailyNews> newsList;

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions options = new DisplayImageOptions.Builder()
            .showImageOnLoading(R.drawable.noimage)
            .showImageOnFail(R.drawable.noimage)
            .showImageForEmptyUri(R.drawable.lks_for_blank_url)
            .cacheInMemory(true)
            .cacheOnDisk(true)
            .considerExifParams(true)
            .build();
    private ImageLoadingListener animateFirstListener = new AnimateFirstDisplayListener();

    public NewsAdapter(List<DailyNews> newsList) {
        this.newsList = newsList;

        setHasStableIds(true);
    }

    public void setNewsList(List<DailyNews> newsList) {
        this.newsList.clear();
        this.newsList.addAll(newsList);
    }

    public void updateNewsList(List<DailyNews> newsList) {
        setNewsList(newsList);
        notifyDataSetChanged();
    }

    @Override
    public CardViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final Context context = parent.getContext();

        View itemView = LayoutInflater
                .from(context)
                .inflate(R.layout.news_list_item, parent, false);

        return new CardViewHolder(itemView, new CardViewHolder.ClickResponseListener() {
            @Override
            public void onWholeClick(int position) {
                browseOrShare(context, position, true);
            }

            @Override
            public void onOverflowClick(View v, final int position) {
                PopupMenu popup = new PopupMenu(context, v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.contextual_news_list, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case R.id.action_share_url:
                            browseOrShare(context, position, false);
                            break;
                    }
                    return true;
                });
                popup.show();
            }
        });
    }

    @Override
    public void onBindViewHolder(final CardViewHolder holder, final int position) {
        final DailyNews dailyNews = newsList.get(position);
        imageLoader.displayImage(dailyNews.getThumbnailUrl(), holder.newsImage, options, animateFirstListener);

        if (dailyNews.getQuestions().size() > 1) {
            holder.questionTitle.setText(dailyNews.getDailyTitle());
            String simplifiedMultiQuestion = Constants.Strings.MULTIPLE_DISCUSSION;
            holder.dailyTitle.setText(simplifiedMultiQuestion);
        } else {
            holder.questionTitle.setText(dailyNews.getQuestions().get(0).getTitle());
            holder.dailyTitle.setText(dailyNews.getDailyTitle());
        }
    }

    @Override
    public int getItemCount() {
        return newsList.size();
    }

    @Override
    public long getItemId(int position) {
        return newsList.get(position).hashCode();
    }

    private void browseOrShare(final Context context, int position, final boolean browse) {
        DailyNews dailyNews = newsList.get(position);
        if (dailyNews.getQuestions().size() > 1) {
            String[] questionTitles = Stream.of(dailyNews.getQuestions()).map(Question::getTitle)
                    .toArray(String[]::new);
            new AlertDialog.Builder(context)
                    .setTitle(dailyNews.getDailyTitle())
                    .setItems(questionTitles, makeDialogListener(context, browse, dailyNews))
                    .show();
        } else {
            if (browse) {
                goToZhihu(context, dailyNews.getQuestions().get(0).getUrl());
            } else {
                String questionTitle = dailyNews.getQuestions().get(0).getTitle(),
                        questionUrl = dailyNews.getQuestions().get(0).getUrl();

                share(context, questionTitle, questionUrl);
            }
        }
    }

    private DialogInterface.OnClickListener makeDialogListener(Context context, boolean browse, DailyNews dailyNews) {
        return (dialog, which) -> {
            if (browse) {
                goToZhihu(context, dailyNews.getQuestions().get(which).getUrl());
            } else {
                String questionTitle = dailyNews.getQuestions().get(which).getTitle(),
                        questionUrl = dailyNews.getQuestions().get(which).getUrl();

                share(context, questionTitle, questionUrl);
            }
        };
    }

    private void goToZhihu(Context context, String url) {
        if (!ZhihuDailyPurifyApplication.getSharedPreferences()
                .getBoolean(Constants.SharedPreferencesKeys.KEY_SHOULD_USE_CLIENT, false)) {
            openUsingBrowser(context, url);
        } else if (Check.isZhihuClientInstalled()) {
            //Open using Zhihu's official client
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            browserIntent.setPackage(Constants.Information.ZHIHU_PACKAGE_ID);
            context.startActivity(browserIntent);
        } else {
            openUsingBrowser(context, url);
        }
    }

    private void openUsingBrowser(Context context, String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

        if (Check.isIntentSafe(browserIntent)) {
            context.startActivity(browserIntent);
        } else {
            Toast.makeText(context, context.getString(R.string.no_browser), Toast.LENGTH_SHORT).show();
        }
    }

    private void share(Context context, String questionTitle, String questionUrl) {
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        //noinspection deprecation
        share.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        share.putExtra(Intent.EXTRA_TEXT,
                questionTitle + " " + questionUrl + Constants.Strings.SHARE_FROM_ZHIHU);
        context.startActivity(Intent.createChooser(share, context.getString(R.string.share_to)));
    }

    public static class CardViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public ImageView newsImage;
        public TextView questionTitle;
        public TextView dailyTitle;
        public ImageView overflow;

        private ClickResponseListener mClickResponseListener;

        public CardViewHolder(View v, ClickResponseListener clickResponseListener) {
            super(v);

            this.mClickResponseListener = clickResponseListener;

            newsImage = (ImageView) v.findViewById(R.id.thumbnail_image);
            questionTitle = (TextView) v.findViewById(R.id.question_title);
            dailyTitle = (TextView) v.findViewById(R.id.daily_title);
            overflow = (ImageView) v.findViewById(R.id.card_share_overflow);

            v.setOnClickListener(this);
            overflow.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (v == overflow) {
                mClickResponseListener.onOverflowClick(v, getAdapterPosition());
            } else {
                mClickResponseListener.onWholeClick(getAdapterPosition());
            }
        }

        public interface ClickResponseListener {
            void onWholeClick(int position);

            void onOverflowClick(View v, int position);
        }
    }

    private static class AnimateFirstDisplayListener extends SimpleImageLoadingListener {
        static final List<String> displayedImages = Collections.synchronizedList(new LinkedList<>());

        @Override
        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
            if (loadedImage != null) {
                ImageView imageView = (ImageView) view;
                boolean firstDisplay = !displayedImages.contains(imageUri);
                if (firstDisplay) {
                    FadeInBitmapDisplayer.animate(imageView, 500);
                    displayedImages.add(imageUri);
                }
            }
        }
    }
}
