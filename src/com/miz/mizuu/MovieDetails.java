package com.miz.mizuu;

import java.io.File;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.android.youtube.player.YouTubeApiServiceUtil;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubeStandalonePlayer;
import com.miz.functions.DeleteFile;
import com.miz.functions.MizLib;
import com.miz.functions.Movie;
import com.miz.mizuu.fragments.ActorBrowserFragment;
import com.miz.mizuu.fragments.MovieDetailsFragment;
import com.miz.widgets.MovieBackdropWidgetProvider;
import com.miz.widgets.MovieCoverWidgetProvider;
import com.miz.widgets.MovieStackWidgetProvider;

public class MovieDetails extends FragmentActivity implements ActionBar.TabListener {

	private ViewPager awesomePager;
	private int movieId;
	private Movie thisMovie;
	private DbAdapter db;
	private boolean ignorePrefixes, prefsRemoveMoviesFromWatchlist, ignoreDeletedFiles, ignoreNfo;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!MizLib.runsInPortraitMode(this))
			setTheme(R.style.Theme_Example_NoBackGround);

		if (!MizLib.runsInPortraitMode(this))
			getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

		setContentView(R.layout.viewpager);

		ignorePrefixes = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefsIgnorePrefixesInTitles", false);
		ignoreNfo = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefsIgnoreNfoFiles", true);
		prefsRemoveMoviesFromWatchlist = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefsRemoveMoviesFromWatchlist", true);
		ignoreDeletedFiles = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefsIgnoredFilesEnabled", false);

		// Fetch the database ID of the movie to view
		if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
			movieId = Integer.valueOf(getIntent().getStringExtra(SearchManager.EXTRA_DATA_KEY));
		} else {
			movieId = getIntent().getExtras().getInt("rowId");
		}

		awesomePager = (ViewPager) findViewById(R.id.awesomepager);
		awesomePager.setAdapter(new MovieDetailsAdapter(getSupportFragmentManager()));

		final ActionBar bar = getActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		bar.addTab(bar.newTab()
				.setText(getString(R.string.overview))
				.setTabListener(this));
		bar.addTab(bar.newTab()
				.setText(getString(R.string.detailsActors))
				.setTabListener(this));

		awesomePager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				bar.getTabAt(position).select();
				ViewParent root = findViewById(android.R.id.content).getParent();
				MizLib.findAndUpdateSpinner(root, position);
			}
		});

		if (savedInstanceState != null) {
			bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
		}

		// Set up database and open it
		db = MizuuApplication.getMovieAdapter();


		Cursor cursor = null;
		try {
			if (movieId > 0)
				cursor = db.fetchMovie(movieId);
			else
				cursor = db.fetchMovie(getIntent().getExtras().getString("tmdbId"));
			thisMovie = new Movie(this,
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_ROWID)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_FILEPATH)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TITLE)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_PLOT)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TAGLINE)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TMDBID)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_IMDBID)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RATING)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RELEASEDATE)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_CERTIFICATION)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RUNTIME)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TRAILER)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_GENRES)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_FAVOURITE)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_CAST)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_COLLECTION)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_EXTRA_2)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TO_WATCH)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_HAS_WATCHED)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_COVERPATH)),
					cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_EXTRA_1)),
					ignorePrefixes,
					ignoreNfo
					);
		} catch (Exception e) {
			finish();
		} finally {
			cursor.close();
		}

		if (thisMovie == null)
			finish(); // Finish the activity if the movie doesn't load

		// The the row ID again, if the MovieDetails activity was launched based on a TMDB ID
		movieId = Integer.parseInt(thisMovie.getRowId());

		try {
			setTitle(thisMovie.getTitle());
			getActionBar().setSubtitle(thisMovie.getReleaseYear().replace("(", "").replace(")", ""));
		} catch (Exception e) {
			finish();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
	}

	@SuppressLint("NewApi")
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.details, menu);

		try {
			if (thisMovie.isFavourite()) {
				menu.findItem(R.id.movie_fav).setIcon(R.drawable.fav);
				menu.findItem(R.id.movie_fav).setTitle(R.string.menuFavouriteTitleRemove);
			} else {
				menu.findItem(R.id.movie_fav).setIcon(R.drawable.reviews);
				menu.findItem(R.id.movie_fav).setTitle(R.string.menuFavouriteTitle);
			}

			if (!MizLib.runsOnTablet(this) && MizLib.runsInPortraitMode(this))
				menu.findItem(R.id.watch_list).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

			if (thisMovie.toWatch()) {
				menu.findItem(R.id.watch_list).setIcon(R.drawable.watchlist_remove);
				menu.findItem(R.id.watch_list).setTitle(R.string.removeFromWatchlist);
			} else {
				menu.findItem(R.id.watch_list).setIcon(R.drawable.watchlist_add);
				menu.findItem(R.id.watch_list).setTitle(R.string.watchLater);
			}

			if (thisMovie.hasWatched()) {
				menu.findItem(R.id.watched).setTitle(R.string.stringMarkAsUnwatched);
			} else {
				menu.findItem(R.id.watched).setTitle(R.string.stringMarkAsWatched);
			}

			if (!thisMovie.isPartOfCollection()) {
				menu.findItem(R.id.change_collection_cover).setVisible(false);
			}

			if (!MizLib.isImdbInstalled(this)) {
				menu.findItem(R.id.imdb).setVisible(false);
			}
		} catch (Exception e) {} // This can happen if thisMovie is null for whatever reason

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			if (getIntent().getExtras().getBoolean("isFromWidget")) {
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
				i.setClass(getApplicationContext(), MainMovies.class);
				startActivity(i);

				finish();
			} else			
				onBackPressed();

			return true;
		case R.id.share:
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_TEXT, "http://www.imdb.com/title/" + thisMovie.getImdbId());
			startActivity(intent);
			return true;
		case R.id.imdb:
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse("http://www.imdb.com/title/" + thisMovie.getImdbId()));
			startActivity(i);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		getActionBar().setDisplayHomeAsUpEnabled(true);
		EasyTracker.getInstance().activityStart(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

	public void deleteMovie(MenuItem item) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		View dialogLayout = getLayoutInflater().inflate(R.layout.delete_file_dialog_layout, null);
		final CheckBox cb = (CheckBox) dialogLayout.findViewById(R.id.deleteFile);

		builder.setTitle(getString(R.string.removeMovie))
		.setView(dialogLayout)
		.setCancelable(false)
		.setPositiveButton(getString(android.R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				boolean deleted = false;

				if (ignoreDeletedFiles)
					deleted = db.ignoreMovie(Long.valueOf(thisMovie.getRowId()));
				else
					deleted = db.deleteMovie(Long.valueOf(thisMovie.getRowId()));

				if (deleted) {
					if (cb.isChecked()) {
						Intent deleteIntent = new Intent(getApplicationContext(), DeleteFile.class);
						deleteIntent.putExtra("filepath", thisMovie.getFilepath());
						getApplicationContext().startService(deleteIntent);
					}

					boolean movieExists = db.movieExists(thisMovie.getTmdbId());

					// We only want to delete movie images, if there are no other versions of the same movie
					if (!movieExists) {
						try { // Delete cover art image
							File coverArt = new File(thisMovie.getPoster());
							if (coverArt.exists() && coverArt.getAbsolutePath().contains("com.miz.mizuu")) {
								MizLib.deleteFile(coverArt);
							}
						} catch (NullPointerException e) {} // No file to delete

						try { // Delete thumbnail image
							File thumbnail = new File(thisMovie.getThumbnail());
							if (thumbnail.exists() && thumbnail.getAbsolutePath().contains("com.miz.mizuu")) {
								MizLib.deleteFile(thumbnail);
							}
						} catch (NullPointerException e) {} // No file to delete

						try { // Delete backdrop image
							File backdrop = new File(thisMovie.getBackdrop());
							if (backdrop.exists() && backdrop.getAbsolutePath().contains("com.miz.mizuu")) {
								MizLib.deleteFile(backdrop);
							}
						} catch (NullPointerException e) {} // No file to delete
					}

					notifyDatasetChanges();
					finish();
				} else {
					Toast.makeText(getApplicationContext(), getString(R.string.failedToRemoveMovie), Toast.LENGTH_SHORT).show();
				}
			}
		})
		.setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		})
		.create().show();
	}

	public void identifyMovie(MenuItem item) {
		Intent intent = new Intent();
		intent.putExtra("fileName", thisMovie.getFullFilepath());
		intent.putExtra("rowId", thisMovie.getRowId());
		intent.setClass(this, IdentifyMovie.class);
		startActivityForResult(intent, 0);
	}

	public void shareMovie(MenuItem item) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, "http://www.imdb.com/title/" + thisMovie.getImdbId());
		startActivity(Intent.createChooser(intent, getString(R.string.shareWith)));
	}

	public void favAction(MenuItem item) {
		thisMovie.setFavourite(!thisMovie.isFavourite()); // Reverse the favourite boolean

		if (db.updateMovieSingleItem(Long.valueOf(thisMovie.getRowId()), DbAdapter.KEY_FAVOURITE, thisMovie.getFavourite())) {
			invalidateOptionsMenu();

			if (thisMovie.isFavourite()) {
				Toast.makeText(this, getString(R.string.addedToFavs), Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, getString(R.string.removedFromFavs), Toast.LENGTH_SHORT).show();
				setResult(2); // Favorite removed
			}

			LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("mizuu-library-change"));

		} else Toast.makeText(this, getString(R.string.errorOccured), Toast.LENGTH_SHORT).show();

		new Thread() {
			@Override
			public void run() {
				ArrayList<Movie> movie = new ArrayList<Movie>();
				movie.add(thisMovie);
				MizLib.movieFavorite(movie, getApplicationContext());
			}
		}.start();
	}

	public void watched(MenuItem item) {
		thisMovie.setHasWatched(!thisMovie.hasWatched()); // Reverse the hasWatched boolean

		if (db.updateMovieSingleItem(Long.valueOf(thisMovie.getRowId()), DbAdapter.KEY_HAS_WATCHED, thisMovie.getHasWatched())) {
			invalidateOptionsMenu();

			if (thisMovie.hasWatched()) {
				Toast.makeText(this, getString(R.string.markedAsWatched), Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, getString(R.string.markedAsUnwatched), Toast.LENGTH_SHORT).show();
			}

			LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("mizuu-library-change"));

		} else Toast.makeText(this, getString(R.string.errorOccured), Toast.LENGTH_SHORT).show();

		if (prefsRemoveMoviesFromWatchlist)
			removeFromWatchlist();

		new Thread() {
			@Override
			public void run() {
				ArrayList<Movie> watchedMovies = new ArrayList<Movie>();
				watchedMovies.add(thisMovie);
				MizLib.markMovieAsWatched(watchedMovies, getApplicationContext());
			}
		}.start();
	}

	public void watchList(MenuItem item) {
		thisMovie.setToWatch(!thisMovie.toWatch()); // Reverse the toWatch boolean

		if (db.updateMovieSingleItem(Long.valueOf(thisMovie.getRowId()), DbAdapter.KEY_TO_WATCH, thisMovie.getToWatch())) {
			invalidateOptionsMenu();

			if (thisMovie.toWatch()) {
				Toast.makeText(this, getString(R.string.addedToWatchList), Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, getString(R.string.removedFromWatchList), Toast.LENGTH_SHORT).show();
			}

			LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("mizuu-library-change"));

		} else Toast.makeText(this, getString(R.string.errorOccured), Toast.LENGTH_SHORT).show();

		new Thread() {
			@Override
			public void run() {
				ArrayList<Movie> watchlist = new ArrayList<Movie>();
				watchlist.add(thisMovie);
				MizLib.movieWatchlist(watchlist, getApplicationContext());
			}
		}.start();
	}

	public void removeFromWatchlist() {
		thisMovie.setToWatch(false); // Remove it

		if (db.updateMovieSingleItem(Long.valueOf(thisMovie.getRowId()), DbAdapter.KEY_TO_WATCH, thisMovie.getToWatch())) {
			invalidateOptionsMenu();
			LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("mizuu-library-change"));
		}

		new Thread() {
			@Override
			public void run() {
				ArrayList<Movie> watchlist = new ArrayList<Movie>();
				watchlist.add(thisMovie);
				MizLib.movieWatchlist(watchlist, getApplicationContext());
			}
		}.start();
	}

	public void watchTrailer(MenuItem item) {
		if (!MizLib.isEmpty(thisMovie.getLocalTrailer())) {
			try { // Attempt to launch intent based on the MIME type
				startActivity(MizLib.getVideoIntent(thisMovie.getLocalTrailer(), false, thisMovie.getTitle() + " " + getString(R.string.detailsTrailer)));
			} catch (Exception e) {
				try { // Attempt to launch intent based on wildcard MIME type
					startActivity(MizLib.getVideoIntent(thisMovie.getLocalTrailer(), "video/*", thisMovie.getTitle() + " " + getString(R.string.detailsTrailer)));
				} catch (Exception e2) {
					Toast.makeText(this, getString(R.string.noVideoPlayerFound), Toast.LENGTH_LONG).show();
				}
			}
		} else {
			if (!MizLib.isEmpty(thisMovie.getTrailer())) {
				Intent intent = YouTubeStandalonePlayer.createVideoIntent(this, MizLib.YOUTUBE_API, MizLib.getYouTubeId(thisMovie.getTrailer()), 0, false, true);
				startActivity(intent);
			} else {
				Toast.makeText(this, getString(R.string.searching), Toast.LENGTH_SHORT).show();
				new TmdbTrailerSearch().execute(thisMovie.getTmdbId());
			}
		}
	}

	private class TmdbTrailerSearch extends AsyncTask<String, Integer, String> {
		@Override
		protected String doInBackground(String... params) {
			try {
				JSONObject jObject = MizLib.getJSONObject("https://api.themoviedb.org/3/movie/" + params[0] + "/trailers?api_key=" + MizLib.TMDB_API);
				JSONArray trailers = jObject.getJSONArray("youtube");

				if (trailers.length() > 0)
					return trailers.getJSONObject(0).getString("source");
				else
					return null;
			} catch (Exception e) {
				return null;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				if (YouTubeApiServiceUtil.isYouTubeApiServiceAvailable(getApplicationContext()).equals(YouTubeInitializationResult.SUCCESS)) {
					Intent intent = YouTubeStandalonePlayer.createVideoIntent(MovieDetails.this, MizLib.YOUTUBE_API, MizLib.getYouTubeId(result), 0, false, true);
					startActivity(intent);
				} else {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(result));
				}
			} else {
				new YoutubeTrailerSearch().execute(thisMovie.getTitle());
			}
		}
	}

	private class YoutubeTrailerSearch extends AsyncTask<String, Integer, String> {
		@Override
		protected String doInBackground(String... params) {
			try {
				JSONObject jObject = MizLib.getJSONObject("https://gdata.youtube.com/feeds/api/videos?q=" + params[0] + "&max-results=20&alt=json&v=2");
				JSONObject jdata = jObject.getJSONObject("feed");
				JSONArray aitems = jdata.getJSONArray("entry");

				for (int i = 0; i < aitems.length(); i++) {
					JSONObject item0 = aitems.getJSONObject(i);

					// Check if the video can be embedded or viewed on a mobile device
					boolean embedding = false, mobile = false;
					JSONArray access = item0.getJSONArray("yt$accessControl");

					for (int j = 0; j < access.length(); j++) {
						if (access.getJSONObject(i).getString("action").equals("embed"))
							embedding = access.getJSONObject(i).getString("permission").equals("allowed") ? true : false;

						if (access.getJSONObject(i).getString("action").equals("syndicate"))
							mobile = access.getJSONObject(i).getString("permission").equals("allowed") ? true : false;
					}

					// Add the video ID if it's accessible from a mobile device
					if (embedding || mobile) {
						JSONObject id = item0.getJSONObject("id");
						String fullYTlink = id.getString("$t");
						return fullYTlink.substring(fullYTlink.lastIndexOf("video:") + 6);
					}
				}
			} catch (Exception e) {}

			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				if (YouTubeApiServiceUtil.isYouTubeApiServiceAvailable(getApplicationContext()).equals(YouTubeInitializationResult.SUCCESS)) {
					Intent intent = YouTubeStandalonePlayer.createVideoIntent(MovieDetails.this, MizLib.YOUTUBE_API, MizLib.getYouTubeId(result), 0, false, true);
					startActivity(intent);
				} else {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(result));
				}
			} else {
				Toast.makeText(getApplicationContext(), getString(R.string.errorSomethingWentWrong), Toast.LENGTH_LONG).show();
			}
		}
	}

	public void searchCover(MenuItem mi) {
		searchCover();
	}

	private void searchCover() {
		if (thisMovie.getTmdbId() != null && !thisMovie.getTmdbId().isEmpty() && MizLib.isOnline(getApplicationContext())) { // Make sure that the device is connected to the web and has the TMDb ID
			Intent intent = new Intent();
			intent.putExtra("tmdbId", thisMovie.getTmdbId());
			intent.putExtra("startPosition", 0);
			intent.setClass(this, MovieCoverFanartBrowser.class);
			startActivity(intent); // Start the intent for result
		} else {
			// No movie ID / Internet connection
			Toast.makeText(this, getString(R.string.coverSearchFailed), Toast.LENGTH_LONG).show();
		}
	}

	public void searchCollectionCover(MenuItem mi) {
		searchCollectionCover();
	}

	private void searchCollectionCover() {
		if (thisMovie.getTmdbId() != null && !thisMovie.getTmdbId().isEmpty() && MizLib.isOnline(getApplicationContext())) { // Make sure that the device is connected to the web and has the TMDb ID
			Intent intent = new Intent();
			intent.putExtra("tmdbId", thisMovie.getTmdbId());
			intent.putExtra("collectionId", thisMovie.getCollectionId());
			intent.putExtra("startPosition", 2);
			intent.setClass(this, MovieCoverFanartBrowser.class);
			startActivity(intent); // Start the intent for result
		} else {
			// No movie ID / Internet connection
			Toast.makeText(this, getString(R.string.coverSearchFailed), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == 1)
			updateWidgets();

		if (resultCode == 2 || resultCode == 4) {
			if (resultCode == 4) // The movie data has been edited
				Toast.makeText(this, getString(R.string.updatedMovie), Toast.LENGTH_SHORT).show();

			// Create a new Intent with the Bundle
			Intent intent = new Intent();
			intent.setClass(getApplicationContext(), MovieDetails.class);
			intent.putExtra("rowId", movieId);

			// Start the Intent for result
			startActivity(intent);

			finish();
		}
	}

	private void notifyDatasetChanges() {
		LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("mizuu-library-change"));
	}

	private void updateWidgets() {
		AppWidgetManager awm = AppWidgetManager.getInstance(this);
		awm.notifyAppWidgetViewDataChanged(awm.getAppWidgetIds(new ComponentName(this, MovieStackWidgetProvider.class)), R.id.stack_view); // Update stack view widget
		awm.notifyAppWidgetViewDataChanged(awm.getAppWidgetIds(new ComponentName(this, MovieCoverWidgetProvider.class)), R.id.widget_grid); // Update grid view widget
		awm.notifyAppWidgetViewDataChanged(awm.getAppWidgetIds(new ComponentName(this, MovieBackdropWidgetProvider.class)), R.id.widget_grid); // Update grid view widget
	}

	public void browseFanart(MenuItem mi) {
		browseFanart();
	}

	private void browseFanart() {
		if (MizLib.isOnline(getApplicationContext())) {
			if (thisMovie.getTmdbId() != null && !thisMovie.getTmdbId().isEmpty()) {
				Intent intent = new Intent();
				intent.putExtra("tmdbId", thisMovie.getTmdbId());
				intent.putExtra("startPosition", 1);
				intent.setClass(this, MovieCoverFanartBrowser.class);
				startActivity(intent); // Start the intent for result
			}
		} else {
			Toast.makeText(this, getString(R.string.noInternet), Toast.LENGTH_SHORT).show();
		}
	}

	public void showEditMenu(MenuItem mi) {
		Intent intent = new Intent(this, EditMovie.class);
		intent.putExtra("rowId", Integer.valueOf(thisMovie.getRowId()));
		startActivityForResult(intent, 1);
	}

	private class MovieDetailsAdapter extends FragmentPagerAdapter {

		public MovieDetailsAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override  
		public Fragment getItem(int index) {
			switch (index) {
			case 0:
				return MovieDetailsFragment.newInstance(movieId);
			case 1:
				return ActorBrowserFragment.newInstance(thisMovie.getTmdbId(), true);
			}
			return null;
		}  

		@Override  
		public int getCount() {  
			return 2;
		}
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		awesomePager.setCurrentItem(getActionBar().getSelectedTab().getPosition(), true);
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {}
}