package com.miz.mizuu;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.crashlytics.android.Crashlytics;
import com.miz.db.DbAdapter;
import com.miz.db.DbAdapterSources;
import com.miz.db.DbAdapterTvShow;
import com.miz.db.DbAdapterTvShowEpisode;
import com.miz.functions.CifsImageDownloader;
import com.miz.functions.MizLib;
import com.miz.functions.PicassoDownloader;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.PauseOnScrollListener;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.utils.L;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

public class MizuuApplication extends Application implements OnSharedPreferenceChangeListener {

	private static boolean mLoadWhileScrolling;
	private static PauseOnScrollListener mPauseOnScrollListener;
	private static DbAdapterTvShow dbTvShow;
	private static DbAdapterTvShowEpisode dbTvShowEpisode;
	private static DbAdapterSources dbSources;
	private static DbAdapter db;
	private static HashMap<String, String[]> map = new HashMap<String, String[]>();
	private static Picasso mPicasso;
	private static LruCache mLruCache;
	private static File mMovieThumbFolder;

	@Override
	public void onCreate() {
		super.onCreate();

		jcifs.Config.setProperty("jcifs.smb.client.disablePlainTextPasswords", "false");

		if (!(0 != ( getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)))
			Crashlytics.start(this);

		initImageLoader(getApplicationContext());

		// Database setup
		dbTvShow = new DbAdapterTvShow(this);
		dbTvShowEpisode = new DbAdapterTvShowEpisode(this);
		dbSources = new DbAdapterSources(this);
		db = new DbAdapter(this);

		mLruCache = new LruCache(this);

		// Set OnSharedPreferenceChange listener
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

		mLoadWhileScrolling = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefsLoadImagesWhileScrolling", false);
	}

	@Override
	public void onTerminate() {
		super.onTerminate();

		dbTvShow.close();
		dbTvShowEpisode.close();
		dbSources.close();
		db.close();

		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
	}

	public static void initImageLoader(Context context) {

		if (!(0 != (context.getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)))
			L.disableLogging();

		ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(context)
		.threadPoolSize(4)
		.threadPriority(Thread.NORM_PRIORITY)
		.denyCacheImageMultipleSizesInMemory()
		.memoryCacheSizePercentage(20)
		.discCacheSize(1024 * 1024 * 100)
		.imageDownloader(new CifsImageDownloader(context))
		.discCacheFileNameGenerator(new Md5FileNameGenerator())
		.tasksProcessingOrder(QueueProcessingType.FIFO)
		.build();
		// Initialize ImageLoader with configuration.
		ImageLoader.getInstance().init(config);
	}

	public static DisplayImageOptions getDefaultCoverLoadingOptions() {
		DisplayImageOptions options = new DisplayImageOptions.Builder()
		.showImageOnLoading(R.drawable.gray)
		.showImageOnFail(R.drawable.loading_image)
		.cacheInMemory(true)
		.showImageForEmptyUri(R.drawable.loading_image)
		.cacheOnDisc(true)
		.bitmapConfig(Bitmap.Config.RGB_565)
		.build();
		return options;
	}

	public static DisplayImageOptions getDefaultActorLoadingOptions() {
		DisplayImageOptions options = new DisplayImageOptions.Builder()
		.showImageOnLoading(R.drawable.gray)
		.showImageOnFail(R.drawable.noactor)
		.showImageForEmptyUri(R.drawable.noactor)
		.cacheInMemory(true)
		.cacheOnDisc(true)
		.bitmapConfig(Bitmap.Config.RGB_565)
		.build();
		return options;
	}

	public static DisplayImageOptions getDefaultBackdropLoadingOptions() {
		DisplayImageOptions options = new DisplayImageOptions.Builder()
		.showImageOnLoading(R.drawable.gray)
		.showImageOnFail(R.drawable.nobackdrop)
		.showImageForEmptyUri(R.drawable.nobackdrop)
		.cacheInMemory(true)
		.cacheOnDisc(true)
		.bitmapConfig(Bitmap.Config.RGB_565)
		.build();
		return options;
	}

	public static DisplayImageOptions getBackdropLoadingOptions() {
		DisplayImageOptions options = new DisplayImageOptions.Builder()
		.showImageOnFail(R.drawable.bg)
		.cacheInMemory(true)
		.showImageForEmptyUri(R.drawable.bg)
		.cacheOnDisc(true)
		.bitmapConfig(Bitmap.Config.RGB_565)
		.build();
		return options;
	}

	public static DbAdapterTvShow getTvDbAdapter() {
		return dbTvShow;
	}

	public static DbAdapterTvShowEpisode getTvEpisodeDbAdapter() {
		return dbTvShowEpisode;
	}

	public static DbAdapterSources getSourcesAdapter() {
		return dbSources;
	}

	public static DbAdapter getMovieAdapter() {
		return db;
	}

	public static String[] getCifsFilesList(String parentPath) {
		return map.get(parentPath);
	}

	public static void putCifsFilesList(String parentPath, String[] list) {
		if (!map.containsKey(parentPath))
			map.put(parentPath, list);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("prefsLoadImagesWhileScrolling")) {
			mLoadWhileScrolling = sharedPreferences.getBoolean("prefsLoadImagesWhileScrolling", false);
		}
	}

	public static PauseOnScrollListener getPauseOnScrollListener(ImageLoader imageLoader) {
		if (mLoadWhileScrolling)
			mPauseOnScrollListener = new PauseOnScrollListener(imageLoader, false, false);
		else
			mPauseOnScrollListener = new PauseOnScrollListener(imageLoader, true, true);

		return mPauseOnScrollListener;
	}

	public static Picasso getPicassoForCovers(Context context) {
		mPicasso = new Picasso.Builder(context).downloader(new PicassoDownloader(context)).executor(getThreadPoolExecutor()).build();

		return mPicasso;
	}
	
	private static ThreadPoolExecutor getThreadPoolExecutor() {
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
		        new LinkedBlockingQueue<Runnable>(), new PicassoThreadFactory());
	}

	static class PicassoThreadFactory implements ThreadFactory {
		public Thread newThread(Runnable r) {
			return new PicassoThread(r);
		}
	}

	private static class PicassoThread extends Thread {
		public PicassoThread(Runnable r) {
			super(r);
		}

		@Override public void run() {
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
			super.run();
		}
	}

	public static LruCache getLruCache() {
		return mLruCache;
	}

	public static File getMovieThumbFolder(Context context) {
		if (mMovieThumbFolder == null)
			mMovieThumbFolder = MizLib.getMovieThumbFolder(context);
		return mMovieThumbFolder;
	}
}