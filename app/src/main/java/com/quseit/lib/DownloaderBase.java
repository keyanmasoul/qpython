package com.quseit.lib;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.hipipal.sl4alib.R;
import com.quseit.base.MyApp;
import com.quseit.config.CONF;
import com.quseit.db.DownloadLog;
import com.quseit.db.UserLog;
import com.quseit.util.FileHelper;
import com.quseit.util.NAction;
import com.quseit.util.NStorage;
import com.quseit.util.NUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;

public abstract class DownloaderBase extends Service {
	protected static final String TAG = "MyDownloader";
	private static int NOTIFICATION_ID = 0x20001;// 通知栏消息id

	protected String DOWNLOADLINK;

	private final int DOWN_LOAD = 4; // 进行中
	private final int DOWN_WAIT = 1; // 排队中
	private final int DOWN_SEECSS = 2; // 下载完毕
	private final int DOWN_PUSE = 3; // 暂停中
	private final int DOWN_ERROR = 5; // 出错的
	private final int DOWN_CONTINUE = 5; // 继续 对 暂停

	protected String mTitle;
	protected String mArtist;
	protected String mAlbum;
	protected String mExt;
	protected long mCompletedSize;

	private long fileLenght;
	
	final private int THREADCOUNT = CONF.THREA_STAT.length;

	private int runingTread = 3;
	private long downlenght = 0;
	private DownloadLog DBDao;
	private long locklenght = 0;
	private long sonThreadSize;

	private int service_stat = 0;
	private String service_json;

	// protected File updateDir = null;
	protected File downloadFile = null;
	protected String rootPath = null;

	protected final static int DOWNLOAD_EXCEPTION = -3;
	protected final static int DOWNLOAD_CANCL = -1;
	protected final static int DOWNLOAD_PAUSE = -2;
	protected final static int DOWNLOAD_COMPLETE = 0;
	protected final static int DOWNLOAD_FAIL = 1;
	private boolean ISERORR;
	private boolean isREAD;

	protected NotificationManager downloadNotificationManager = null;

	protected Intent updateIntent = null;
	protected PendingIntent updatePendingIntent = null;
	protected int NotifyIndex;
	//protected String play;

	protected String referCookie;
	protected String referUrl;
	protected String referUa;

	@Override
	public void onStart(Intent intent, int startId) {
		
		super.onStart(intent, startId);
		//startDownloadTask(intent);
	}

	@TargetApi(5)
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.e(TAG, "onStartCommand<<===");
		String play = null;
		if(intent!=null) {
		  play = intent.getStringExtra(CONF.EXTRA_CONTENT_URL6);

	        if (NAction.isThreadsStop(getApplicationContext()) || (play!=null && play.equals("1"))) {
	        	Log.e(TAG, "onStartCommand===>");
	        	startDownloadTask(intent);
	        }
		}
		return super.onStartCommand(intent, flags, startId);
	}
	
	public void startDownloadTask(Intent intent) {
		NotifyIndex = CONF.DOWNLOAD_NOTIFY_INDEX;

		mCompletedSize=0;
		if (intent == null) {
			Toast.makeText(getApplicationContext(), R.string.exception,
					Toast.LENGTH_SHORT).show();
			
			servicePause();
			
			stopSelf();
		} else {
			rootPath = intent.getStringExtra(CONF.EXTRA_CONTENT_URL0);
			DOWNLOADLINK = intent.getStringExtra(CONF.EXTRA_CONTENT_URL2);

			mTitle = NUtil.sescape(intent
					.getStringExtra(CONF.EXTRA_CONTENT_URL3));
			mArtist = NUtil.sescape(intent
					.getStringExtra(CONF.EXTRA_CONTENT_URL4));
			mAlbum = NUtil.sescape(intent
					.getStringExtra(CONF.EXTRA_CONTENT_URL5));

			mExt = intent.getStringExtra(CONF.EXTRA_CONTENT_URL6);

			referCookie = intent.getStringExtra(CONF.EXTRA_CONTENT_URL7);
			referUrl = intent.getStringExtra(CONF.EXTRA_CONTENT_URL8);
			referUa = intent.getStringExtra(CONF.EXTRA_CONTENT_URL9);

			service_stat = intent.getIntExtra(CONF.EXTRA_CONTENT_URL10, 0);
			service_json = intent.getStringExtra(CONF.EXTRA_CONTENT_URL11);

			if (CONF.DEBUG)
				Log.d(TAG, "onStartCommand[title:" + mTitle + ",artist:"
						+ mArtist + ",album:" + mAlbum + ",completedSize:"
						+ mCompletedSize + "]");

			// 创建文件
			if (NUtil.isExternalStorageExists()) {
				try {
					
						downloadFile = new File(FileHelper.getBasePath(MyApp
								.getInstance().getRoot(), "tmp"), mArtist + "_"
								+ mTitle + mExt);
					
					
					
					
					
					
				} catch (NotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

				this.downloadNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				Notification downloadNotification = new Notification();

				updateIntent = new Intent(this, getMan());
				updatePendingIntent = PendingIntent.getActivity(this,
						NOTIFICATION_ID, updateIntent, 0);

				downloadNotification.tickerText = getString(R.string.up_soft_download);
				downloadNotification.contentIntent = updatePendingIntent;
//				downloadNotification.setLatestEventInfo(this, mTitle + "("
//						+ mArtist + ")","", updatePendingIntent);

				downloadNotificationManager.notify(NotifyIndex,
						downloadNotification);

				DBDao = new DownloadLog(getApplicationContext());
				//DownloadInfo info = DBDao.getInfoByUrl(DOWNLOADLINK);
				try {
					URL url = new URL(DOWNLOADLINK);
					HttpURLConnection conn = (HttpURLConnection) url
							.openConnection();
					conn.setConnectTimeout(5000);
					conn.setRequestMethod("GET");
					int code = conn.getResponseCode();

					if (code == 200) {
						fileLenght = conn.getContentLength();
						ISERORR = false;
						RandomAccessFile raf = new RandomAccessFile(
								downloadFile, "rwd");
						raf.setLength(fileLenght);
						raf.close();

						sonThreadSize = fileLenght / THREADCOUNT;
						isREAD = true;

						DBDao.updatefileleng(fileLenght, DOWNLOADLINK, downloadFile.getName());
						

						for (int threadId = 1; threadId <= THREADCOUNT; threadId++) {
							long start = (threadId - 1) * sonThreadSize;
							long end = threadId * sonThreadSize;
							if (THREADCOUNT != 1) {
								end = end - 1;
							}
							if (threadId == THREADCOUNT) {
								end = fileLenght;
							}

							if(!service_json.equals("")){
								JSONObject jsonData=new JSONObject(service_json);
								NStorage.setLongSP(getContext(), "download"+threadId, jsonData.getLong("download"+threadId));
							}else{
								NStorage.setLongSP(getContext(), "download"+threadId, 0);
							}
							
							new DownloadThread(start, end, threadId,
									DOWNLOADLINK).start();

						}

					
					}

				} catch (Exception e) {
					e.printStackTrace();
					updateHandler.obtainMessage(DOWNLOAD_EXCEPTION)
					.sendToTarget();
				}

			} else {
				Toast.makeText(getApplicationContext(), R.string.not_sd,
						Toast.LENGTH_SHORT).show();
				stopSelf();
			}
		}
	}

	public abstract Class<?> getSelf();

	public abstract Class<?> getMan();

	public abstract Context getContext();

	class DownloadThread extends Thread {
		private long start, end;
		int threadId;
		private String path;

		public DownloadThread(long start, long end, int threadId, String path) {
			this.start = start;
			this.end = end;
			this.threadId = threadId;
			this.path = path;
		}

		@Override
		public void run() {
			int catched = 0;

			try {
				NAction.setThreadStat(getApplicationContext(), threadId, 1);

				Long done=NStorage.getLongSP(getApplicationContext(), "download"+threadId);
				if(done>0){
					synchronized (DownloaderBase.this) {
						long oldAlldownData=done-(sonThreadSize*(threadId-1));
						mCompletedSize+=oldAlldownData;
						start=done;
					}
				}
				

				URL url = new URL(path);
				HttpURLConnection conn = (HttpURLConnection) url
						.openConnection();
				conn.setConnectTimeout(5000);
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
				int code = conn.getResponseCode();
				if (code >= 200 || code < 400) {
					InputStream is = conn.getInputStream();
					RandomAccessFile raf = new RandomAccessFile(downloadFile,
							"rwd");
					raf.seek(start);

					int len = 0;
					long total=0;
					byte[] buffer = new byte[4096];
					while ((len = is.read(buffer)) != -1) {
						raf.write(buffer, 0, len);
						total+=len;
						mCompletedSize+=len;
						synchronized (DownloaderBase.this) {
							
						   NStorage.setLongSP(getApplicationContext(), "download"+threadId, (total+start));
						   NStorage.setLongSP(getApplicationContext(), "downloadProgress", mCompletedSize*100/fileLenght);
						   						   
						   
						   if (mCompletedSize*100/fileLenght%2==0) {
							   Notification downloadNotification = new Notification();
	
							   updatePendingIntent = PendingIntent.getActivity(DownloaderBase.this,
										NOTIFICATION_ID, updateIntent, 0);
	

								downloadNotification.tickerText = getString(R.string.up_soft_download);
								downloadNotification.contentIntent = updatePendingIntent;
//								downloadNotification.setLatestEventInfo(DownloaderBase.this, mTitle + "("
//										+ mArtist + ")",mCompletedSize*100/fileLenght+"%", updatePendingIntent);
	
								downloadNotificationManager.notify(NotifyIndex,
										downloadNotification);
						   }
						}
						
					
						DownloadInfo dInfo = DBDao.getInfoByPath(downloadFile
								.getName());
						
						if (dInfo != null) {
							if (dInfo.getStat() == 2) {
								NStorage.setLongSP(getApplicationContext(), "download"+threadId,(total+start));
								synchronized (DownloaderBase.this) {
									servicePause();
								}
								break;
							}
						}
					}
					is.close();
					raf.close();
				} else {
					updateHandler.obtainMessage(DOWNLOAD_EXCEPTION)
							.sendToTarget();
				}
				catched = 1;
			} catch (Exception e) {
				
				updateHandler.obtainMessage(DOWNLOAD_FAIL).sendToTarget();
				catched = 1;

			} finally {
				Log.d(TAG, "download run finally:"+catched);
				if (catched != 1) {
					updateHandler.obtainMessage(DOWNLOAD_EXCEPTION).sendToTarget();
					
				} else {
				
					synchronized (DownloaderBase.this) {
						long done =NStorage.getLongSP(getApplicationContext(), "download"+threadId);
						end=end-(sonThreadSize*(threadId-1));
						if ((done-(sonThreadSize*(threadId-1)) >= end))
							runingTread--;
						if (runingTread == 0) {
	                        
							for(int i=1;i<=THREADCOUNT;i++){
								NStorage.setLongSP(getApplicationContext(), "download"+i, 0);
							}
							updateHandler.obtainMessage(DOWNLOAD_COMPLETE).sendToTarget();
						} 
						
						 if (NAction.isThreadsStop(getApplicationContext())) {
							 Log.d(TAG, "HERE");
					        	updateHandler.obtainMessage(DOWNLOAD_PAUSE).sendToTarget();
					     }
					}
				}
			}
		}
	}


	protected Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Intent intentS = new Intent(getApplicationContext(), getSelf());
			boolean stopFlag = true;

			switch (msg.what) {

			case DOWNLOAD_COMPLETE:

				Notification downloadNotification = new Notification();
				// downloadNotification.defaults =
				// Notification.DEFAULT_VIBRATE;// 振动提醒
				downloadNotification.contentIntent = updatePendingIntent;
//				downloadNotification.setLatestEventInfo(getContext(), mTitle,
//						getString(R.string.up_soft_done), updatePendingIntent);

				downloadNotificationManager.notify(NotifyIndex,
						downloadNotification);
				
				Log.d(TAG, "DOWNLOAD_COMPLETE:"+downloadFile+"-"+mTitle);

				try {
					String ext = "."
							+ FileHelper.getExt(downloadFile.getName(), "dat");
					
					String root = NAction
							.getDefaultRoot(getApplicationContext());
					
					
					File dstFile;
					if (rootPath != null && !rootPath.equals("")) {
						dstFile = new File(FileHelper.getABSPath(rootPath + "/"
								+ mArtist + "/"), mTitle + ext);

					} else {
						if (!root.equals("")) {
							dstFile = new File(FileHelper.getABSPath(root + "/"
									+ mArtist + "/"), mTitle + ext);

						} else {
							dstFile = new File(FileHelper.getBasePath(MyApp
									.getInstance().getFullRoot() + "/", mArtist
									+ "/"), mTitle + ext);
						}
					}
					downloadFile.renameTo(dstFile);

					Toast.makeText(
							getApplicationContext(),
							MessageFormat.format(
									getString(R.string.download_ok),
									mTitle), Toast.LENGTH_SHORT).show();
	
					
					recordLog(mTitle, mArtist, mAlbum, "9",
							dstFile.getAbsolutePath());

					serviceSuccess();

					reOpenDownLoad();


				} catch (IOException e) { // 改名失败
					downloadNotification.contentIntent = updatePendingIntent;
//					downloadNotification.setLatestEventInfo(getContext(),
//							mTitle + "(" + mArtist + ")",
//							getString(R.string.up_soft_failed),
//							updatePendingIntent);

					downloadNotificationManager.notify(NotifyIndex,
							downloadNotification);

					e.printStackTrace();
				}
				break;

			case DOWNLOAD_FAIL:
				// 下载失败
				downloadNotification = new Notification();
				// downloadNotification.defaults =
				// Notification.DEFAULT_VIBRATE;// 振动提醒
				downloadNotification.contentIntent = updatePendingIntent;
//				downloadNotification
//						.setLatestEventInfo(getContext(), mTitle + "("
//								+ mArtist + ")",
//								getString(R.string.up_soft_failed),
//								updatePendingIntent);

				downloadNotificationManager.notify(NotifyIndex,
						downloadNotification);
				servicePause();
				NAction.clearThreadsStat(getApplicationContext());
				break;

			case DOWNLOAD_CANCL:
				Toast.makeText(
						getApplicationContext(),
						MessageFormat.format(
								getString(R.string.download_cancel), mTitle),
						Toast.LENGTH_SHORT).show();

				downloadNotificationManager.cancel(NotifyIndex);
				break;

			case DOWNLOAD_EXCEPTION:
				Toast.makeText(getApplicationContext(),
						getString(R.string.download_exception),
						Toast.LENGTH_SHORT).show();

				downloadNotification = new Notification();
				downloadNotification.contentIntent = updatePendingIntent;
//				downloadNotification.setLatestEventInfo(getContext(), mTitle
//						+ "(" + mArtist + ")",
//						getString(R.string.download_exception),
//						updatePendingIntent);

				downloadNotificationManager.notify(NotifyIndex,
						downloadNotification);
				servicePause();
				NAction.clearThreadsStat(getApplicationContext());
				break;

			case DOWNLOAD_PAUSE:
				Toast.makeText(
						getApplicationContext(),
						MessageFormat.format(
								getString(R.string.download_pause), mTitle),
						Toast.LENGTH_SHORT).show();

				downloadNotification = new Notification();
				// downloadNotification.defaults =
				// Notification.DEFAULT_VIBRATE;// 振动提醒
				// downloadNotification.flags = Notification.FLAG_NO_CLEAR;
				downloadNotification.contentIntent = updatePendingIntent;
//				downloadNotification.setLatestEventInfo(getContext(), mTitle
//						+ "(" + mArtist + ")", getString(R.string.task_pause),
//						updatePendingIntent);

				downloadNotificationManager.notify(NotifyIndex,
						downloadNotification);
				break;
			case DOWN_LOAD:

				break;
			default:
				stopFlag = false;

				// stopService(updateIntent);
			}
			if (stopFlag) {
				Intent intent = new Intent(".MDownloadManAct");
				sendBroadcast(intent);

				stopService(intentS);
			}
		}
	};

	public void recordLog(String title, String artist, String album,
			String type, String path) {

		UserLog pq = new UserLog(getApplicationContext());
		if (!pq.checkIfLogExists(path)) {
			pq.insertNewLog(title, artist, album, type, path, 1);
		}

	}

	@Override
	public IBinder onBind(Intent intent) {

		return null;
	}

	class sonThreadInfo {
		private int start, end, state, done, threadId;

		public int getStart() {
			return start;
		}

		public void setStart(int start) {
			this.start = start;
		}

		public int getEnd() {
			return end;
		}

		public void setEnd(int end) {
			this.end = end;
		}

		public int getState() {
			return state;
		}

		public void setState(int state) {
			this.state = state;
		}

		public int getDone() {
			return done;
		}

		public void setDone(int done) {
			this.done = done;
		}

		public int getThreadId() {
			return threadId;
		}

		public void setThreadId(int threadId) {
			this.threadId = threadId;
		}
	}

	public void setNStorageThreadInfo(long start, long end, int state,
			long done, long threadId) {
		Log.d(TAG, "setNStorageThreadInfo:" + start + "-" + end + "-" + state
				+ "-" + done);
		try {

			JSONObject objson = new JSONObject();
			objson.put("start", start);
			objson.put("end", end);
			objson.put("state", state);
			objson.put("done", done);
			objson.put("threadId", threadId);

			NStorage.setSP(getApplicationContext(), "downloads" + threadId,
					objson.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public JSONObject getNStorageThreadInfo(long threadId) {
		String json = NStorage.getSP(getApplicationContext(), "download"
				+ threadId);
		if (json != null && !json.equals("")) {
			Log.d(TAG, "getNStorageThreadInfo:" + json);
			try {
				JSONObject objson = new JSONObject(json);
				return objson;

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	private void servicePause() {
		runingTread=THREADCOUNT;
		DBDao = new DownloadLog(getApplicationContext());
		JSONObject json = new JSONObject();
		for (int threadid = 1; threadid <= THREADCOUNT; threadid++) {
			long downloadsJson = NStorage.getLongSP(getApplicationContext(),
					"download" + threadid);
			try {
				json.put("download" + threadid, downloadsJson);
			} catch (JSONException e) {
				
				e.printStackTrace();
			}
			NAction.setThreadStat(getApplicationContext(), threadid, 0);
		}
		if (downloadFile!=null) {
			DBDao.updateInfos(mCompletedSize, mCompletedSize, DOWNLOADLINK,
					downloadFile.getName(), 0);
			DBDao.updateDownLoadState(3, json.toString(), 2, DOWNLOADLINK,
					downloadFile.getName());
		}
		
		Intent reDownloadlist=new Intent(".MDownloadManAct");
		sendBroadcast(reDownloadlist);

	}

	private void serviceSuccess() {
		runingTread=THREADCOUNT;
		NStorage.setLongSP(getApplicationContext(), "downloadProgress", 0);
		DBDao = new DownloadLog(getApplicationContext());
		
		for (int threadid = 1; threadid <= THREADCOUNT; threadid++) {
			NStorage.setLongSP(getApplicationContext(),"download" + threadid,0);
			NAction.setThreadStat(getApplicationContext(), threadid, 0);

		}
	
		service_json = "";
		service_stat = 0;
		DBDao.delete(DOWNLOADLINK, downloadFile.getName());
		downloadFile=null;
		DOWNLOADLINK="";
	}

	private void reOpenDownLoad() {
		DBDao = new DownloadLog(getApplicationContext());

		List<DownloadInfo> listInfos = DBDao.query();
		if (listInfos.size() == 0) {
			Log.d(TAG, "reOpenDownLoad: NOTASK");

		} else {
			DownloadInfo info = listInfos.get(0);
			DBDao.updateDownLoadState(4, 10, info.getUrl(), info.getPath());
			
			mTitle = info.getTitle();
			mArtist = info.getArtist();
			mAlbum = info.getAlbum();
			mExt = FileHelper.getExt(FileHelper.getFileName(info.getPath()), "mp4");

			Log.d(TAG, "reOpenDownLoad: "+mTitle);

			
			DOWNLOADLINK=info.getUrl();
			runingTread=THREADCOUNT;
			mCompletedSize=0;
			
			// 创建文件
			if (NUtil.isExternalStorageExists()) {
				try {
					downloadFile = new File(FileHelper.getBasePath(MyApp
							.getInstance().getRoot(), "tmp"), info.getPath());
				} catch (NotFoundException e) {
					Log.d(TAG, "NotFoundException:" + e.getMessage());
					e.printStackTrace();
				} catch (IOException e) {
					Log.d(TAG, "IOException:" + e.getMessage());
					e.printStackTrace();
				}

				this.downloadNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				Notification downloadNotification = new Notification();

				updateIntent = new Intent(this, getMan());
				updatePendingIntent = PendingIntent.getActivity(this,
						NOTIFICATION_ID, updateIntent, 0);


				downloadNotification.tickerText = getString(R.string.up_soft_download);
				downloadNotification.contentIntent = updatePendingIntent;
//				downloadNotification.setLatestEventInfo(this, mTitle + "("
//						+ mArtist + ")", "", updatePendingIntent);

				downloadNotificationManager.notify(NotifyIndex,
						downloadNotification);

				DBDao = new DownloadLog(getApplicationContext());

				try {
					URL url = new URL(DOWNLOADLINK);
					HttpURLConnection conn = (HttpURLConnection) url
							.openConnection();
					conn.setConnectTimeout(5000);
					conn.setRequestMethod("GET");
					int code = conn.getResponseCode();

					if (code == 200) {
						fileLenght = conn.getContentLength();
						ISERORR = false;
						RandomAccessFile raf = new RandomAccessFile(
								downloadFile, "rwd");
						raf.setLength(fileLenght);
						raf.close();

						sonThreadSize = fileLenght / THREADCOUNT;
						isREAD = true;

						DBDao.updatefileleng(fileLenght, DOWNLOADLINK, downloadFile.getName());
						
						//DownloadInfo downdloadinfo = DBDao.getInfoByPath(downloadFile.getName());

						

						for (int threadId = 1; threadId <= THREADCOUNT; threadId++) {
							long start = (threadId - 1) * sonThreadSize;
							long end = threadId * sonThreadSize;
							if (THREADCOUNT != 1) {
								end = end - 1;
							}
							if (threadId == THREADCOUNT) {
								end = fileLenght;
							}

							
							/*if(!service_json.equals("")){
								JSONObject jsonData=new JSONObject(service_json);
								NStorage.setLongSP(getContext(), "download"+threadId, jsonData.getLong("download"+threadId));
							}else{*/
								NStorage.setLongSP(getContext(), "download"+threadId, 0);
							//}
							
							new DownloadThread(start, end, threadId,
									DOWNLOADLINK).start();

						
							
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}

			} else {
				Toast.makeText(getApplicationContext(), R.string.not_sd,
						Toast.LENGTH_SHORT).show();
				stopSelf();
			}

		}
	}

	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		super.onDestroy();
	}

}
