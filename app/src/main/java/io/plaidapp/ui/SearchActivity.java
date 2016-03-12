/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.plaidapp.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.transitionseverywhere.Transition;
import com.transitionseverywhere.TransitionInflater;

import java.util.List;

import butterknife.Bind;
import butterknife.BindDimen;
import butterknife.BindInt;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.plaidapp.R;
import io.plaidapp.data.PlaidItem;
import io.plaidapp.data.SearchDataManager;
import io.plaidapp.data.pocket.PocketUtils;
import io.plaidapp.ui.recyclerview.InfiniteScrollListener;
import io.plaidapp.ui.widget.BaselineGridTextView;
import io.plaidapp.util.AnimUtils;
import io.plaidapp.util.ImeUtils;
import io.plaidapp.util.ViewUtils;
import io.plaidapp.util.compat.ObjectAnimatorCompat;
import io.plaidapp.util.compat.TransitionManagerCompat;
import io.plaidapp.util.compat.ViewAnimationUtilsCompat;

public class SearchActivity extends Activity {

    public static final String EXTRA_MENU_LEFT = "EXTRA_MENU_LEFT";
    public static final String EXTRA_MENU_CENTER_X = "EXTRA_MENU_CENTER_X";
    public static final String EXTRA_QUERY = "EXTRA_QUERY";
    public static final String EXTRA_SAVE_DRIBBBLE = "EXTRA_SAVE_DRIBBBLE";
    public static final String EXTRA_SAVE_DESIGNER_NEWS = "EXTRA_SAVE_DESIGNER_NEWS";
    public static final int RESULT_CODE_SAVE = 7;

    @Bind(R.id.searchback) ImageButton searchBack;
    @Bind(R.id.searchback_container) ViewGroup searchBackContainer;
    @Bind(R.id.search_view) SearchView searchView;
    @Bind(R.id.search_background) View searchBackground;
    @Bind(android.R.id.empty) ProgressBar progress;
    @Bind(R.id.search_results) RecyclerView results;
    @Bind(R.id.container) ViewGroup container;
    @Bind(R.id.search_toolbar) ViewGroup searchToolbar;
    @Bind(R.id.results_container) ViewGroup resultsContainer;
    @Bind(R.id.fab) ImageButton fab;
    @Bind(R.id.confirm_save_container) ViewGroup confirmSaveContainer;
    @Bind(R.id.save_dribbble) CheckBox saveDribbble;
    @Bind(R.id.save_designer_news) CheckBox saveDesignerNews;
    @Bind(R.id.scrim) View scrim;
    @Bind(R.id.results_scrim) View resultsScrim;
    private BaselineGridTextView noResults;
    @BindInt(R.integer.num_columns) int columns;
    @BindDimen(R.dimen.z_app_bar) float appBarElevation;
    private Transition auto;

    private int searchBackDistanceX;
    private int searchIconCenterX;
    private SearchDataManager dataManager;
    private FeedAdapter adapter;

    public static Intent createStartIntent(Context context, int menuIconLeft, int menuIconCenterX) {
        Intent starter = new Intent(context, SearchActivity.class);
        starter.putExtra(EXTRA_MENU_LEFT, menuIconLeft);
        starter.putExtra(EXTRA_MENU_CENTER_X, menuIconCenterX);
        return starter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ButterKnife.bind(this);
        setupSearchView();
        auto = TransitionInflater.from(this).inflateTransition(R.transition.auto);

        dataManager = new SearchDataManager(this) {
            @Override
            public void onDataLoaded(List<? extends PlaidItem> data) {
                if (data != null && data.size() > 0) {
                    if (results.getVisibility() != View.VISIBLE) {
                        TransitionManagerCompat.beginDelayedTransition(container, auto);
                        progress.setVisibility(View.GONE);
                        results.setVisibility(View.VISIBLE);
                        fab.setVisibility(View.VISIBLE);
                        fab.setAlpha(0.6f);
                        fab.setScaleX(0f);
                        fab.setScaleY(0f);
                        fab.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setStartDelay(800L)
                                .setDuration(300L)
                                .setInterpolator(AnimUtils.getLinearOutSlowInInterpolator());
                    }
                    adapter.addAndResort(data);
                } else {
                    TransitionManagerCompat.beginDelayedTransition(container, auto);
                    progress.setVisibility(View.GONE);
                    setNoResultsVisibility(View.VISIBLE);
                }
            }
        };
        adapter = new FeedAdapter(this, dataManager, columns, PocketUtils.isPocketInstalled(this));
        results.setAdapter(adapter);
        GridLayoutManager layoutManager = new GridLayoutManager(this, columns);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemColumnSpan(position);
            }
        });
        results.setLayoutManager(layoutManager);
        results.addOnScrollListener(new InfiniteScrollListener(layoutManager, dataManager) {
            @Override
            public void onLoadMore() {
                dataManager.loadMore();
            }
        });
        results.setHasFixedSize(true);

        // extract the search icon's location passed from the launching activity, minus 4dp to
        // compensate for different paddings in the views
        searchBackDistanceX = getIntent().getIntExtra(EXTRA_MENU_LEFT, 0) - (int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        searchIconCenterX = getIntent().getIntExtra(EXTRA_MENU_CENTER_X, 0);

        // translate icon to match the launching screen then animate back into position
        searchBackContainer.setTranslationX(searchBackDistanceX);
        searchBackContainer.animate()
                .translationX(0f)
                .setDuration(650L)
                .setInterpolator(AnimUtils.getFastOutSlowInInterpolator());
        // transform from search icon to back icon
        AnimatedVectorDrawableCompat searchToBack = AnimatedVectorDrawableCompat.create(this,
                R.drawable.avd_search_to_back);
        searchBack.setImageDrawable(searchToBack);
        searchToBack.start();
        // for some reason the animation doesn't always finish (leaving a part arrow!?) so after
        // the animation set a static drawable. Also animation callbacks weren't added until API23
        // so using post delayed :(
        // TODO fix properly!!
        searchBack.postDelayed(new Runnable() {
            @Override
            public void run() {
                searchBack.setImageDrawable(ContextCompat.getDrawable(SearchActivity.this,
                        R.drawable.ic_arrow_back_padded));
            }
        }, 600L);

        // fade in the other search chrome
        searchBackground.animate()
                .alpha(1f)
                .setDuration(300L)
                .setInterpolator(AnimUtils.getLinearOutSlowInInterpolator());
        searchView.animate()
                .alpha(1f)
                .setStartDelay(400L)
                .setDuration(400L)
                .setInterpolator(AnimUtils.getLinearOutSlowInInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        searchView.requestFocus();
                        ImeUtils.showIme(searchView);
                    }
                });

        // animate in a scrim over the content behind
        scrim.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                scrim.getViewTreeObserver().removeOnPreDrawListener(this);
                AnimatorSet showScrim = new AnimatorSet();
                showScrim.playTogether(
                        ViewAnimationUtilsCompat.createCircularReveal(
                                scrim,
                                searchIconCenterX,
                                searchBackground.getBottom(),
                                0,
                                (float) Math.hypot(searchBackDistanceX, scrim.getHeight()
                                        - searchBackground.getBottom())),
                        ObjectAnimatorCompat.ofArgb(
                                scrim,
                                ViewUtils.BACKGROUND_COLOR,
                                Color.TRANSPARENT,
                                ContextCompat.getColor(SearchActivity.this, R.color.scrim)));
                showScrim.setDuration(400L);
                showScrim.setInterpolator(AnimUtils.getLinearOutSlowInInterpolator());
                showScrim.start();
                return false;
            }
        });
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.hasExtra(SearchManager.QUERY)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(query)) {
                searchView.setQuery(query, false);
                searchFor(query);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (confirmSaveContainer.getVisibility() == View.VISIBLE) {
            hideSaveConfimation();
        } else {
            dismiss();
        }
    }

    @Override
    protected void onPause() {
        // needed to suppress the default window animation when closing the activity
        overridePendingTransition(0, 0);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        dataManager.cancelLoading();
        super.onDestroy();
    }

    @OnClick({ R.id.scrim, R.id.searchback })
    protected void dismiss() {
        // translate the icon to match position in the launching activity
        searchBackContainer.animate()
                .translationX(searchBackDistanceX)
                .setDuration(600L)
                .setInterpolator(AnimUtils.getFastOutSlowInInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ActivityCompat.finishAfterTransition(SearchActivity.this);
                    }
                })
                .start();
        // transform from back icon to search icon
        AnimatedVectorDrawableCompat backToSearch = AnimatedVectorDrawableCompat.create(this,
                R.drawable.avd_back_to_search);
        searchBack.setImageDrawable(backToSearch);
        // clear the background else the touch ripple moves with the translation which looks bad
        ViewUtils.setBackground(searchBack, null);
        backToSearch.start();
        // fade out the other search chrome
        searchView.animate()
                .alpha(0f)
                .setStartDelay(0L)
                .setDuration(120L)
                .setInterpolator(AnimUtils.getFastOutLinearInInterpolator())
                .setListener(null)
                .start();
        searchBackground.animate()
                .alpha(0f)
                .setStartDelay(300L)
                .setDuration(160L)
                .setInterpolator(AnimUtils.getFastOutLinearInInterpolator())
                .setListener(null)
                .start();
        if (ViewCompat.getZ(searchToolbar) != 0f) {
            ViewCompat.animate(searchToolbar)
                    .z(0f)
                    .setDuration(600L)
                    .setInterpolator(AnimUtils.getFastOutLinearInInterpolator())
                    .start();
        }

        // if we're showing search results, circular hide them
        if (resultsContainer.getHeight() > 0) {
            Animator closeResults = ViewAnimationUtilsCompat.createCircularReveal(
                    resultsContainer,
                    searchIconCenterX,
                    0,
                    (float) Math.hypot(searchIconCenterX, resultsContainer.getHeight()),
                    0f);
            closeResults.setDuration(500L);
            closeResults.setInterpolator(AnimUtils.getFastOutSlowInInterpolator());
            closeResults.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    resultsContainer.setVisibility(View.INVISIBLE);
                }
            });
            closeResults.start();
        }

        // fade out the scrim
        scrim.animate()
                .alpha(0f)
                .setDuration(400L)
                .setInterpolator(AnimUtils.getFastOutLinearInInterpolator())
                .setListener(null)
                .start();
    }

    @OnClick(R.id.fab)
    protected void save() {
        // show the save confirmation bubble
        fab.setVisibility(View.INVISIBLE);
        confirmSaveContainer.setVisibility(View.VISIBLE);
        resultsScrim.setVisibility(View.VISIBLE);

        // expand it once it's been measured and show a scrim over the search results
        confirmSaveContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver
                .OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // expand the confirmation
                confirmSaveContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                Animator reveal = ViewAnimationUtilsCompat.createCircularReveal(confirmSaveContainer,
                        confirmSaveContainer.getWidth() / 2,
                        confirmSaveContainer.getHeight() / 2,
                        fab.getWidth() / 2,
                        confirmSaveContainer.getWidth() / 2);
                reveal.setDuration(250L);
                reveal.setInterpolator(AnimUtils.getFastOutSlowInInterpolator());
                reveal.start();

                // show the scrim
                int centerX = (fab.getLeft() + fab.getRight()) / 2;
                int centerY = (fab.getTop() + fab.getBottom()) / 2;
                Animator revealScrim = ViewAnimationUtilsCompat.createCircularReveal(
                        resultsScrim,
                        centerX,
                        centerY,
                        0,
                        (float) Math.hypot(centerX, centerY));
                revealScrim.setDuration(400L);
                revealScrim.setInterpolator(AnimUtils.getLinearOutSlowInInterpolator());
                revealScrim.start();
                ObjectAnimator fadeInScrim = ObjectAnimatorCompat.ofArgb(resultsScrim,
                        ViewUtils.BACKGROUND_COLOR,
                        Color.TRANSPARENT,
                        ContextCompat.getColor(SearchActivity.this, R.color.scrim));
                fadeInScrim.setDuration(800L);
                fadeInScrim.setInterpolator(AnimUtils.getLinearOutSlowInInterpolator());
                fadeInScrim.start();

                // ease in the checkboxes
                saveDribbble.setAlpha(0.6f);
                saveDribbble.setTranslationY(saveDribbble.getHeight() * 0.4f);
                saveDribbble.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200L)
                        .setInterpolator(AnimUtils.getLinearOutSlowInInterpolator());
                saveDesignerNews.setAlpha(0.6f);
                saveDesignerNews.setTranslationY(saveDesignerNews.getHeight() * 0.5f);
                saveDesignerNews.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200L)
                        .setInterpolator(AnimUtils.getLinearOutSlowInInterpolator());
                return false;
            }
        });
    }

    @OnClick(R.id.save_confirmed)
    protected void doSave() {
        Intent saveData = new Intent();
        saveData.putExtra(EXTRA_QUERY, dataManager.getQuery());
        saveData.putExtra(EXTRA_SAVE_DRIBBBLE, saveDribbble.isChecked());
        saveData.putExtra(EXTRA_SAVE_DESIGNER_NEWS, saveDesignerNews.isChecked());
        setResult(RESULT_CODE_SAVE, saveData);
        dismiss();
    }

    @OnClick(R.id.results_scrim)
    protected void hideSaveConfimation() {
        if (confirmSaveContainer.getVisibility() == View.VISIBLE) {
            // contract the bubble & hide the scrim
            AnimatorSet hideConfirmation = new AnimatorSet();
            hideConfirmation.playTogether(
                    ViewAnimationUtilsCompat.createCircularReveal(confirmSaveContainer,
                            confirmSaveContainer.getWidth() / 2,
                            confirmSaveContainer.getHeight() / 2,
                            confirmSaveContainer.getWidth() / 2,
                            fab.getWidth() / 2),
                    ObjectAnimatorCompat.ofArgb(resultsScrim,
                            ViewUtils.BACKGROUND_COLOR,
                            Color.TRANSPARENT));
            hideConfirmation.setDuration(150L);
            hideConfirmation.setInterpolator(AnimUtils.getFastOutSlowInInterpolator());
            hideConfirmation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    confirmSaveContainer.setVisibility(View.GONE);
                    resultsScrim.setVisibility(View.GONE);
                    fab.setVisibility(results.getVisibility());
                }
            });
            hideConfirmation.start();
        }
    }

    private void setupSearchView() {
        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchFor(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                if (TextUtils.isEmpty(query)) {
                    clearResults();
                }
                return true;
            }
        });
        searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && confirmSaveContainer.getVisibility() == View.VISIBLE) {
                    hideSaveConfimation();
                }
            }
        });
    }

    private void clearResults() {
        adapter.clear();
        dataManager.clear();
        TransitionManagerCompat.beginDelayedTransition(container, auto);
        results.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);
        confirmSaveContainer.setVisibility(View.GONE);
        resultsScrim.setVisibility(View.GONE);
        setNoResultsVisibility(View.GONE);
    }

    private void setNoResultsVisibility(int visibility) {
        if (visibility == View.VISIBLE) {
            if (noResults == null) {
                noResults = (BaselineGridTextView) ((ViewStub)
                        findViewById(R.id.stub_no_search_results)).inflate();
                noResults.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        searchView.setQuery("", false);
                        searchView.requestFocus();
                        ImeUtils.showIme(searchView);
                    }
                });
            }
            String message = String.format(getString(R
                    .string.no_search_results), searchView.getQuery().toString());
            SpannableStringBuilder ssb = new SpannableStringBuilder(message);
            ssb.setSpan(new StyleSpan(Typeface.ITALIC),
                    message.indexOf('“') + 1,
                    message.length() - 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            noResults.setText(ssb);
        }
        if (noResults != null) {
            noResults.setVisibility(visibility);
        }
    }

    private void searchFor(String query) {
        clearResults();
        progress.setVisibility(View.VISIBLE);
        ImeUtils.hideIme(searchView);
        searchView.clearFocus();
        dataManager.searchFor(query);
    }
}
