package com.luobin.dvr;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.UEventObserver;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;

import com.android.internal.app.ShutdownActivity;
import com.example.jrd48.GlobalStatus;
import com.example.jrd48.chat.FileUtils;
import com.example.jrd48.chat.SharedPreferencesUtils;
import com.example.jrd48.chat.ToastR;
import com.example.jrd48.chat.WelcomeActivity;
import com.example.jrd48.chat.crash.MyApplication;
import com.example.jrd48.chat.receiver.ToastReceiver;
import com.example.jrd48.service.MyService;
import com.luobin.dvr.ui.MainActivity;
import com.luobin.log.DBMyLogHelper;
import com.luobin.log.LogCode;
import com.luobin.musbcam.UsbCamera;
import com.luobin.utils.VideoRoadUtils;
import com.video.GlobalVideo;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.lake.librestreaming.client.CameraVideo;
import me.lake.librestreaming.client.RESClient;
public class DvrService extends Service {
    
    public final static String TAG = "DvrService";
    public final static String LOCK = ".lock";
    private ServiceBinder mServiceBinder = new ServiceBinder();
    
    private DvrInterface mImpl = null;
    
    private CircleRecordHelper mCircleRecordHelper = null;
    private UEventObserver vUsbObserverOn;
    private UEventObserver vUsbObserverOff;
    private boolean mClientConnected = false;
    private WakeLock mWakeLock;
    private final int MSG_AFTER_UNBIND = 1;
    public static final String DVR_FULLSCREEN_SHOW = "dvr_fullscreen_show";
    private SmartMirrorsObserver mSmartMirrorsObserver;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (mCircleRecordHelper != null && mCircleRecordHelper.handleMessage(msg)) {
                
            } else if (msg.what == MSG_AFTER_UNBIND) {
            	if(mImpl != null)
            		startThumbnailPreview();
            }
            super.handleMessage(msg);
        }
        
    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG,"DvrService:" + receiver);
            if(GlobalVideo.getInstance().isShow()) {
                GlobalVideo.getInstance().onCallClick();
            } else if(RESClient.getInstance().isShow()){
                RESClient.getInstance().onCallClick();
            } else {
                mImpl.onClick();
            }

        }
    };

    public class ServiceBinder extends IDvrService.Stub {

        @Override
        public boolean show(int x, int y, int w, int h) throws RemoteException {
            Log.d(TAG, "show x=" + x + ", y=" + y + ", w=" + w + ", h=" + h);
//            if(x != 0){
//                RESClient.getInstance().updateSurfaceView(x, y + h, w, h);
//            }
            return mImpl.show(x, y, w, h);
        }

        @Override
        public boolean hide() throws RemoteException {
        	Log.d(TAG, "ServiceBinder hide()");
            return mImpl.hide();
        }

        @Override
        public boolean startPreview() throws RemoteException {
        	Log.d(TAG, "ServiceBinder startPreview()");
            return DvrService.this.startPreview();
        }

        @Override
        public boolean stopPreview() throws RemoteException {
        	Log.d(TAG, "ServiceBinder stopPreview()");
            return DvrService.this.stopPreview();
        }

        @Override
        public boolean startRecord(String file) throws RemoteException {
        	Log.d(TAG, "ServiceBinder startRecord(file)");
            if (mCircleRecordHelper != null) {
                Log.e(TAG, "startRecord failed for circle record running");
                return false;
            }
            return mImpl.startRecord(file, false);
        }

        @Override
        public boolean startRecordWithPreVideo(String file)
                throws RemoteException {
        	Log.d(TAG, "ServiceBinder startRecord(file)");
            if (mCircleRecordHelper != null) {
                Log.e(TAG, "startRecordWithPreVideo failed for circle record running");
                return false;
            }
            return mImpl.startRecord(file, true);
        }

        @Override
        public boolean stopRecord() throws RemoteException {
        	Log.d(TAG, "ServiceBinder stopRecord()");
            return DvrService.this.stopRecord();
        }

        @Override
        public boolean setWaterMark(byte[] png, int gravity)
                throws RemoteException {
        	Log.d(TAG, "ServiceBinder setWaterMark()");
            Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
            return mImpl.setWaterMark(bitmap, gravity);
        }

        @Override
        public boolean takePhoto(String file) throws RemoteException {
        	Log.d(TAG, "ServiceBinder takePhoto()");
        	File foder = new File(file);
			if (!foder.getParentFile().exists()) {
				mkDir(foder.getParentFile());
				Log.d(TAG, "takePhoto mkdir()" + foder.getParentFile());
			}
            return mImpl.takePhoto(file);
        }

        @Override
        public boolean startCircleRecord() throws RemoteException {
            Log.d(TAG, "binder call startCircleRecord");
            return DvrService.this.startCircleRecord();
        }

        @Override
        public boolean setTimeStampWaterMark(boolean enabled)
                throws RemoteException {
        	Log.d(TAG, "startRecordWithPreVideo setTimeStampWaterMark()");
            return false;
        }

        @Override
        public boolean isPreviewing() throws RemoteException {
        	Log.d(TAG, "ServiceBinder isPreviewing()");
            return mImpl.isPreviewing();
        }

        @Override
        public boolean isRecording() throws RemoteException {
        	Log.d(TAG, "ServiceBinder isRecording()");
            return mImpl.isRecording();
        }
        
        @Override
        public void setAutoRunWhenBoot(boolean enabled) throws RemoteException {
            DvrConfig.setAutoRunWhenBoot(enabled);
        }

        @Override
        public boolean getAutoRunWhenBoot() throws RemoteException {
            return DvrConfig.getAutoRunWhenBoot();
        }

        @Override
        public int getVideoDuration() throws RemoteException {
            return DvrConfig.getVideoDuration();
        }

        @Override
        public void setVideoDuration(int duration_ms) throws RemoteException {
            DvrConfig.setVideoDuration(duration_ms);
        }

        @Override
        public void getVideoSize(int[] size) throws RemoteException {
            DvrConfig.getVideoSize(size);
        }

        @Override
        public void setVideoSize(int[] size) throws RemoteException {
            DvrConfig.setVideoSize(size);
        }

        @Override
        public String getStoragePath() throws RemoteException {
            return DvrConfig.getStoragePath();
        }

        @Override
        public void setStoragePath(String path) throws RemoteException {
            DvrConfig.setStoragePath(path);
        }

        @Override
        public boolean getAudioEnabled() throws RemoteException {
            return DvrConfig.getAudioEnabled();
        }

        @Override
        public void setAudioEnabled(boolean enabled) throws RemoteException {
            DvrConfig.setAudioEnabled(enabled);
        }

        @Override
        public int getCollisionSensitivity() throws RemoteException {
            return DvrConfig.getCollisionSensitivity();
        }

        @Override
        public void setCollisionSensitivity(int val) throws RemoteException {
            DvrConfig.setCollisionSensitivity(val);
        }

        @Override
        public void getThumbnailViewRect(int[] size) throws RemoteException {
            Log.d(TAG, "DvrService getThumbnailViewRect()");
            DvrConfig.getThumbnailViewRect(size);
        }

        @Override
        public void setThumbnailViewRect(int[] size) throws RemoteException {
            DvrConfig.setThumbnailViewRect(size);
        }

		@Override
		public int getPreVideoTime() throws RemoteException {
			return DvrConfig.getPreVideoTime();
		}

		@Override
		public void setPreVideoTime(int seconds) throws RemoteException {
			if(seconds > 0)
				DvrConfig.setPreVideoTime(seconds);
		}

		@Override
		public int getVideoBitrate() throws RemoteException {
			return DvrConfig.getVideoBitrate();
		}

		@Override
		public void setVideoBitrate(int bitrate) throws RemoteException {
			if(bitrate > 0)
				DvrConfig.setVideoBitrate(bitrate);
		}

        @Override
        public void startRtmp() throws RemoteException {
            Log.d(TAG, "ServiceBinder startRtmp()");
//            RESClient.getInstance().startRecording(null);
        }

        @Override
        public void stopRtmp() throws RemoteException {
            RESClient.getInstance().stopStreaming();
            RESClient.getInstance().destroy();
        }

        @Override
        public void switchCamera() throws RemoteException {
            RESClient.getInstance().switchCamera();
        }

        @Override
        public int rtmpStatus() throws RemoteException {
            return RESClient.getInstance().getStatus();
        }
    }
    private void mkDir(File file) {
		if (file.getParentFile().exists()) {
			file.mkdir();
		} else {
			mkDir(file.getParentFile());
			file.mkdir();
		}
	}
    private class CircleRecordHelper {
        private String mPath;
        private int mIntervalms;
        private DvrInterface mDvr;
        private String mCurVideoName;
        private final int MAX_INDEX = 9999999;
        private final int MSG_BASE = 100;
        private final int MSG_SWITCH_NEW_VIDEO = MSG_BASE + 1;
        private final int MSG_CHECK_FREE_SPACE = MSG_BASE + 2;
        private final int MSG_UPDATE_WATER_MARK = MSG_BASE + 3;
        
        private final boolean FREE_SPACE_DEBUG = false;
        private final long ENOUGH_FREE_SPACE = FREE_SPACE_DEBUG ? 10L * 1024L * 1024L * 1024L : 500L * 1024L * 1024L;
        private final long ENOUGH_MAX_SPACE = FREE_SPACE_DEBUG ? 2L * 1024L * 1024L * 1024L : 1L * 1024L * 1024L * 1024L;

        public CircleRecordHelper(DvrInterface dvr, String path, int intervalms) {
            Log.d(TAG, "CircleRecordHelper created");
            mDvr = dvr;
            mPath = new String(path);
            mIntervalms = intervalms;
            mHandler.sendEmptyMessage(MSG_SWITCH_NEW_VIDEO);
            Log.d(TAG, "CircleRecordHelper created");
        }
        
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_SWITCH_NEW_VIDEO) {
                switchVideo();
                return true;
            } else if (msg.what == MSG_UPDATE_WATER_MARK) {
                updateTimeStamp();
                return true;
            } else if (msg.what == MSG_CHECK_FREE_SPACE) {
                checkFreeSpace();
                return true;
            }
            return false;
        }
        
        public void switchVideo() {
            File f = new File(mPath);
            boolean startRecord = true;
            if(!f.exists()){
                if(!f.mkdirs()) {
                    startRecord =false;
                    mPath = DvrConfig.getStoragePath();
                    mHandler.removeMessages(MSG_SWITCH_NEW_VIDEO);
                    mHandler.sendEmptyMessageDelayed(MSG_SWITCH_NEW_VIDEO, 5000);
                }
            }
            if(startRecord) {
                mCurVideoName = mPath + "/" + newFileName(true);
                Log.d(TAG, "switchVideo " + mCurVideoName);
                mDvr.startRecord(mCurVideoName, false);
                mHandler.removeMessages(MSG_SWITCH_NEW_VIDEO);
                mHandler.sendEmptyMessageDelayed(MSG_SWITCH_NEW_VIDEO, mIntervalms);
                updateTimeStamp();
                checkFreeSpace();
            }
        }
        
        public void checkFreeSpace() {
            try {
                mHandler.removeMessages(MSG_CHECK_FREE_SPACE);
                StatFs sf = new StatFs(mPath);
                String path = DvrConfig.getStoragePath(MyApplication.getContext(),false);
                Log.d(TAG,"mPath="+mPath+", path= "+path);
                long len = FileUtils.getFileSize(new File(mPath));
                Log.d(TAG,"len="+len+",ENOUGH_MAX_SPACE="+ENOUGH_MAX_SPACE);
                long freespace = sf.getAvailableBytes();
                if (FREE_SPACE_DEBUG) {
//                    Log.d(TAG, "checkFreeSpace " + freespace);
                }
                if((mPath.contains(path) && len > ENOUGH_MAX_SPACE)
                        || (!mPath.contains(path) && freespace < ENOUGH_FREE_SPACE)) {
                    delOldVideo();
                    mHandler.sendEmptyMessageDelayed(MSG_CHECK_FREE_SPACE, 30 * 1000);
                } else {
                    mHandler.sendEmptyMessageDelayed(MSG_CHECK_FREE_SPACE, 10 * 1000);
                }
            } catch (IllegalArgumentException e){
                e.printStackTrace();
            }
        }
        /**
         * Compute the file length.
         *
         * @param file
         * @return
         */
        public  long getFileSize(File file) {
            if (file == null || !file.exists()) {
                return 0;
            }

            if (!file.isDirectory()) {
                return file.length();
            }

            long size = 0;

            Queue<File> queue = new LinkedList<File>();
            queue.offer(file);

            while (null != (file = queue.poll())) {

                for (File fileTemp : file.listFiles()) {
                    if (!fileTemp.isDirectory()) {
                        size += fileTemp.length();
                    } else {
                        queue.offer(fileTemp);
                    }
                }
            }

            return size;
        }
        public void delOldVideo() {
            int lastindex = getLastIndex();
            int earliestindex = getEarliestIndex();
            if (earliestindex == lastindex) {
                Log.w(TAG, "delOldVideo earlistindex=lastindex="+lastindex);
                earliestindex = searchEarliestVideoIndex();
                Log.d(TAG, "searchEarliestVideoIndex return " + earliestindex);
                setEarliestIndex(earliestindex);
                return;
            }
            if (delVideo(earliestindex)) {
                earliestindex++;
            } else {
                // no earliestindex video files found.
                // research for earliest video
                earliestindex = searchEarliestVideoIndex();
            }
            setEarliestIndex(earliestindex);
        }
        
        public void updateTimeStamp() {
            Bitmap newb = Bitmap.createBitmap(300, 40, Config.ARGB_8888);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            Date date = new Date();
            String curDatetimeStr = simpleDateFormat.format(date);
            drawWatermark(newb, curDatetimeStr, 30, 10, 10);
            mDvr.setWaterMark(newb, Gravity.BOTTOM|Gravity.RIGHT);
            mHandler.removeMessages(MSG_UPDATE_WATER_MARK);
            Calendar c = Calendar.getInstance();
            int next = 1000 - c.get(Calendar.MILLISECOND);
//            Log.d(TAG, "update time stamp water mark after " + next + " ms");
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_WATER_MARK, next);
            
        }
        private Point computeStringWidth(Paint p, String strings) {

            float width = p.measureText(strings);
            FontMetrics fr = p.getFontMetrics();
            float height = fr.descent - fr.top;

            return new Point((int) (width + 0.5), (int) (height + 0.5));
        }
        
        private void drawWatermark(Bitmap bmp, String text, int text_size,
                int offset_x, int offset_y) {
            try {
                Canvas canvas = new Canvas(bmp);
                canvas.drawColor(0x00ff00ff);
                int w = canvas.getWidth();
                int h = canvas.getHeight();

                Paint p = new Paint();
                p.setTextSize(text_size);

                Typeface font = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL);
                p.setTypeface(font);

                Point textRect = computeStringWidth(p, text);

                if (offset_x < 0)
                    offset_x = w + offset_x - textRect.x;
                if (offset_y < 0) {
                    offset_y = h + offset_y - textRect.y;
                } else {
                    offset_y += textRect.y/2;
                }

                Paint stkPaint = new Paint();
                stkPaint.setStyle(Style.FILL_AND_STROKE);
                stkPaint.setTextSize(text_size);
                
                stkPaint.setColor(Color.WHITE);
                stkPaint.setAlpha(255);
                canvas.drawText(text, offset_x, offset_y, stkPaint);

                p.setColor(Color.YELLOW);
                p.setAlpha(255);

                canvas.drawText(text, offset_x, offset_y, p);
                canvas.save(Canvas.ALL_SAVE_FLAG);
                canvas.restore();

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("MyBmpUtil", "draw watermark failed: " + e.getMessage());
            }
        }
            
        public int searchEarliestVideoIndex() {
            int lastindex = getLastIndex();
            int earliestindex = getEarliestIndex();
            int maxinter = 0;
            File f = new File(mPath);
            String[] flist = f.list();
            for (int n=0; n<flist.length; n++) {
                String name = flist[n];
                Pattern pat=Pattern.compile("^\\d{7}_[FB]_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_N\\.mp4$");
                Matcher matcher = pat.matcher(name);
                if (matcher.matches()) {
                    String[] strs = name.split("_");
                    int i = Integer.parseInt(strs[0]);
//                    Log.d(TAG, "searchEarliestVideoIndex i="+i);
                    int inter = (i > lastindex) ? (lastindex + (MAX_INDEX - i)) : (lastindex -i);
                    if (inter > maxinter) {
                        maxinter = inter;
                        earliestindex = i;
                    }
                }
            }
            return earliestindex;
        }
        
        /*
         * video file format: XXXXXXX_F/B_YY_MM_DD_HH_MM_SS_L/N.mp4
         */
        public String newFileName(boolean front) {
            int lastindex = getLastIndex();
            int earliestindex = getEarliestIndex();
            lastindex++;
            if (lastindex == earliestindex) {
                delOldVideo();
            }
            SimpleDateFormat timeFormate = new SimpleDateFormat("yy_MM_dd_HH_mm_ss");
            String file = String.format("%07d_%s_%s_N.mp4", lastindex, front ? "F" : "B", timeFormate.format(new Date(System.currentTimeMillis())));
            setLastIndex(lastindex);
            return file;
        }
        
        public int getLastIndex() {
            SharedPreferences sp = DvrService.this.getApplicationContext().getSharedPreferences("Dvr", Context.MODE_PRIVATE);
            return sp.getInt("LastVideoIndex", 0);
        }
        
        public void setLastIndex(int index) {
            SharedPreferences sp = DvrService.this.getApplicationContext().getSharedPreferences("Dvr", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt("LastVideoIndex", index);
            editor.commit();
        }
        
        public int getEarliestIndex() {
            SharedPreferences sp = DvrService.this.getApplicationContext().getSharedPreferences("Dvr", Context.MODE_PRIVATE);
            return sp.getInt("EarliestVideoIndex", 0);
        }
        
        public void setEarliestIndex(int index) {
            SharedPreferences sp = DvrService.this.getApplicationContext().getSharedPreferences("Dvr", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt("EarliestVideoIndex", index);
            editor.commit();
        }
        
        /*
         * delVideo by index, return false if no video be deleted.
         */
        public boolean delVideo(int index) {
            String patstr = String.format("^%07d_[FB]_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_N\\.mp4$", index);
            File f = new File(mPath);
            String[] flist = f.list();
            boolean res = false;
            for (int n=0; n<flist.length; n++) {
                String name = flist[n];
                Pattern pat=Pattern.compile(patstr);
                Matcher matcher = pat.matcher(name);
                if(name.contains(LOCK)){
                    continue;
                }
                if (matcher.matches()) {
                    Log.d(TAG, "delVideo " + name);
                    File ftodel = new File(mPath+"/"+name);
                    ftodel.delete();
                    res = true;
                }
            }
            return res;
        }
        
        public void release() {
            mHandler.removeMessages(MSG_SWITCH_NEW_VIDEO);
            mHandler.removeMessages(MSG_UPDATE_WATER_MARK);
            mHandler.removeMessages(MSG_CHECK_FREE_SPACE);
        }
    };

    public static void start(Context context) {
        Log.d("wsDvr", "start(c)");
        Intent i = new Intent(context, DvrService.class);
        context.startService(i);
    }

    public static void restart() {
        Log.d("wsDvr", "DvrService restart()");
        Intent i = new Intent(MyApplication.getContext(), DvrService.class);
        MyApplication.getContext().stopService(i);

        Intent i2 = new Intent(MyApplication.getContext(), DvrService.class);
        MyApplication.getContext().startService(i2);
    }

    public static void start(Context context,String action,String path) {
        Log.d("wsDvr", "start(c,a,p) : " + action);
        Intent i = new Intent(context, DvrService.class);
        i.setAction(action);
        i.putExtra(RESClient.PATH,path);
        context.startService(i);
    }

    public static void start(Context context,String action,String path,boolean self_video) {
        Log.d("wsDvr", "start(c,a,p) : " + action);
        Intent i = new Intent(context, DvrService.class);
        i.setAction(action);
        i.putExtra(RESClient.PATH,path);
        i.putExtra(RESClient.SELF_VIDEO,self_video);
        context.startService(i);
    }

    public static void updateRtmpView(Context context,int left,int top,int width,int height) {
        Log.d("wsDvr", "updateRtmpView");
        Rect rect = new Rect(left,top,left + width,top+height);
        Intent i = new Intent(context, DvrService.class);
        i.setAction(RESClient.ACTION_UPDATE_RTMP);
        i.putExtra(RESClient.LOCATION,rect);
        context.startService(i);
    }

    public static void updatePlayView(Context context,int left,int top,int width,int height) {
        Log.d("wsDvr", "updatePlayView");
        Rect rect = new Rect(left,top,left + width,top+height);
        Intent i = new Intent(context, DvrService.class);
        i.setAction(RESClient.ACTION_UPDATE_PLAY);
        i.putExtra(RESClient.LOCATION,rect);
        context.startService(i);
    }
    private Thread mCheckThread;
    private ShutDownObserver shutDownObserver;
    private BroadcastReceiver shutDownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v("DvrService", "shutDownReceiver action=" + intent.getAction());
            if(intent.getAction().equalsIgnoreCase(Intent.ACTION_SHUTDOWN)) {
                stopRecord();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if(intent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_ON)) {
                int type = GlobalStatus.getShutDownType(DvrService.this);
                if(type == 0){
                    SharedPreferencesUtils.put(DvrService.this,"isScreenOn",true);
                    checkStatus(true);
                }
            } else if(intent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_OFF)) {
                int type = GlobalStatus.getShutDownType(DvrService.this);
                if(type == 0){
                    SharedPreferencesUtils.put(DvrService.this,"isScreenOn",false);
                    checkStatus(false);
                }
            }
        }
    };
    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate：" + Build.PRODUCT);
        shutDownObserver = new ShutDownObserver(new Handler());
        shutDownObserver.startObserving();
        Settings.System.putInt(MyApplication.getContext().getContentResolver(), MainActivity.DVR_FULLSCREEN_SHOW, 0);
        mSmartMirrorsObserver = new SmartMirrorsObserver(new Handler());
        mSmartMirrorsObserver.startObserving();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(shutDownReceiver,intentFilter);
        DvrConfig.init(getApplicationContext());
        if (mImpl == null) {
            mImpl = DvrImplFactory.createDvrImpl(getApplicationContext());
        }
        RESClient.getInstance().setDvrService(this);
        if(0 == GlobalStatus.getShutDownType(this) && !((boolean)SharedPreferencesUtils.get(DvrService.this,"isScreenOn",false))){
            return;
        }
        setServerForeground();
        if (DvrConfig.getAutoRunWhenBoot()) {
            if(mCheckThread != null && mCheckThread.isAlive()){
                try {
                    mCheckThread.interrupt();
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
            mCheckThread = new Thread() {
                @Override
                public void run() {
                    File file = new File("/dev/video2");
                    while(!mCheckThread.isInterrupted() && !file.exists()){
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        file = new File("/dev/video2");
                        Log.v(TAG,"/dev/video2 is not exist");
                    }
                    Log.v(TAG,"/dev/video2 is exist");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    while(!openCamera()){
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.v(TAG,"/dev/video2 open failed");
                    }
                    try {
                        if (Build.PRODUCT.contains("LB1728")) {
                            vUsbObserverOff = new UEventObserver() {
                                @Override
                                public void onUEvent(UEvent uEvent) {
                                    Log.d(TAG, "vUsbObserverOff");
                                    DvrService dvrService = RESClient.getInstance().getDvrService();
                                    if(dvrService != null) {
                                        dvrService.stopRecord();
                                        try {
                                            Thread.sleep(2000);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (GlobalStatus.getUsbVideo1() != null) {
                                        GlobalStatus.getUsbVideo1().close();
                                        GlobalStatus.setUsbVideo1(null);
                                    }
                                    if (GlobalStatus.getUsbVideo2() != null) {
                                        GlobalStatus.getUsbVideo2().close();
                                        GlobalStatus.setUsbVideo2(null);
                                    }
                                    if(GlobalStatus.getCamera() != null){
                                        GlobalStatus.getCamera().release();
                                        GlobalStatus.setCamera(null);
                                    }
                                    Intent intent = new Intent(ToastReceiver.TOAST_ACTION);
                                    intent.putExtra(ToastReceiver.TOAST_CONTENT, "摄像头断开连接，请检查");

                                    sendBroadcast(intent);
                                    System.exit(0);
                                }
                            };
                            vUsbObserverOff.startObserving("vbusremove");
                            Log.d(TAG, "startObserving:vbusremove");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    startPreview();
                    startCircleRecord();
                    startThumbnailPreview();
                }
            };
            mCheckThread.start();
        }

        try{
            registerReceiver(receiver,new IntentFilter("android.luobin.dvr.ACTION_ONCLICK_COVER"));
        } catch (Exception e){
            e.printStackTrace();
        }
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if(shutDownObserver != null) {
            shutDownObserver.stopObserving();
        }

        if(mSmartMirrorsObserver != null){
            mSmartMirrorsObserver.stopObserving();
        }

        try {
            unregisterReceiver(shutDownReceiver);
        } catch (Exception e){
            e.printStackTrace();
        }
        if(mCheckThread != null && mCheckThread.isAlive()){
            try {
                mCheckThread.interrupt();
            } catch (Exception e){
                e.printStackTrace();
            }
            mCheckThread = null;
        }
        stopRecord();
        if (mImpl != null) {
            mImpl.release();
            mImpl = null;
        }

        try{
            unregisterReceiver(receiver);
        } catch (Exception e){
            e.printStackTrace();
        }
        RESClient.getInstance().setDvrService(null);
        if(vUsbObserverOff != null){
            vUsbObserverOff.stopObserving();
        }
        if(vUsbObserverOn != null){
            vUsbObserverOn.stopObserving();
        }
        RESClient.getInstance().removeSurfaceView();
        super.onDestroy();
    }
    
    public void startThumbnailPreview() {
        int[] rect = new int[4];
        Log.d(TAG, "startThumbnailPreview");
        DvrConfig.getThumbnailViewRect(rect);
        try {
            mImpl.show(rect[0], rect[1], rect[2], rect[3]);
        } catch (Exception e){
            e.printStackTrace();
        }
//        RESClient.getInstance().updateSurfaceView(rect[0], rect[1], rect[2], rect[3]);
    }

    public void show(int x, int y, int w, int h){
        mImpl.show(x,y,w,h);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if(0 == GlobalStatus.getShutDownType(this) && !((boolean)SharedPreferencesUtils.get(DvrService.this,"isScreenOn",false))){
            return START_STICKY;
        }
        String path = null;
//        RESClient.getInstance().createSurfaceView();
        if(intent != null && intent.getAction() != null){
            Log.d(TAG, "onStartCommand action="+intent.getAction());
            switch (intent.getAction()){
                case RESClient.ACTION_START_RTMP:
                    path = intent.getStringExtra(RESClient.PATH);
                    boolean self_video = intent.getBooleanExtra(RESClient.SELF_VIDEO,true);
                    RESClient.getInstance().startRecording(path,self_video);
                    break;
                case RESClient.ACTION_STOP_RTMP:
                    RESClient.getInstance().stopStreaming();
                    RESClient.getInstance().destroy();
                    break;
                case RESClient.ACTION_SWITCH_RTMP:
                    RESClient.getInstance().switchCamera();
                    break;
                case RESClient.ACTION_UPDATE_RTMP:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Rect rect = intent.getParcelableExtra(RESClient.LOCATION);
                            if(!RESClient.getInstance().getSelf_video() && RESClient.getInstance().getStatus() != RESClient.STATUS_NULL_PREPARED) {
                                Log.d(TAG, "mImpl.show");
                                mImpl.show(rect.left, rect.top, rect.width(), rect.height());
                            }
                            RESClient.getInstance().updateSurfaceView(rect.left, rect.top, rect.width(), rect.height());
                        }
                    });
                    break;
                case RESClient.ACTION_START_PLAY:
                    path = intent.getStringExtra(RESClient.PATH);
                    GlobalVideo.getInstance().startVideo(path);
                    break;
                case RESClient.ACTION_STOP_PLAY:
                    GlobalVideo.getInstance().stop();
                    break;
                case RESClient.ACTION_UPDATE_PLAY:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Rect rect = intent.getParcelableExtra(RESClient.LOCATION);
                            GlobalVideo.getInstance().updateParam(rect.left, rect.top, rect.width(), rect.height());
                        }
                    });
                    break;
                case RESClient.ACTION_VOICE_RECORD:
                    RESClient.getInstance().setTransferVoice(true);
                    break;
                case RESClient.ACTION_VOICE_STOP:
                    RESClient.getInstance().setTransferVoice(false);
                    break;
                default:
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        getVersion(); 
        mClientConnected = true;
        mHandler.removeMessages(MSG_AFTER_UNBIND);
        return mServiceBinder;
    }


    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onReBind");
        mClientConnected = true;
        mHandler.removeMessages(MSG_AFTER_UNBIND);
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        mClientConnected = false;
        mHandler.removeMessages(MSG_AFTER_UNBIND);
        mHandler.sendEmptyMessageDelayed(MSG_AFTER_UNBIND, 1500);
        return true;
    }
    
    public boolean startPreview() {
        //zhouyuhuan add: wakelock
        acquireWakeLock();
        int[] size = new int[2];
        DvrConfig.getVideoSize(size);
        return mImpl.startPreview(size[0], size[1], DvrConfig.getAudioEnabled());
    }
    public boolean stopPreview() {
        boolean result = mImpl.stopPreview();
        //zhouyuhuan add: wakelock
        releaseWakeLock();
        return result;
    }

    public boolean startCircleRecord() {
        String path = DvrConfig.getStoragePath();
        Log.d(TAG, "start circle recording path=" + path);
        int intervalms = DvrConfig.getVideoDuration();
        Log.d(TAG, "start circle recording path=" + path + ", intervalms="+intervalms);
        if (mCircleRecordHelper == null) {
            Log.d(TAG, "start circle recording ");
            mCircleRecordHelper = new CircleRecordHelper(mImpl, path, intervalms);
            return true;
        } else {
            Log.e(TAG, "circle recording already running");
            return false;
        }
    }
    
    public boolean stopRecord() {
        if (mCircleRecordHelper != null) {
            mCircleRecordHelper.release();
            mCircleRecordHelper = null;
        }
        mImpl.stopRecord();
        return true;
    }

	/**
	 * get version info
	 */
	public void getVersion() {
		try {
			PackageManager manager = this.getPackageManager();
			PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
			String version = info.versionName;
			Log.d(TAG, "apk version="+version);
		} catch (Exception e) {
			e.printStackTrace();
			Log.d(TAG, "can_not_find_version");
		}
	}

    private void acquireWakeLock() {
        Log.d(TAG,"acquireWakeLock");
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        if (!mWakeLock.isHeld()) {
            Log.d(TAG,"acquireWakeLock mWakeLock.isHeld() == false");
            mWakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        Log.d(TAG,"releaseWakeLock");
		if (mWakeLock == null) {
			return;
		}
        if (!mWakeLock.isHeld()){
            Log.d(TAG,"releaseWakeLock mWakeLock.isHeld() == false");
            return;
        }
        mWakeLock.release();
        mWakeLock = null;
    }


    public void runOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.d(TAG, "Looper.myLooper() == Looper.getMainLooper()");
            r.run();
        } else {
            final Object lock = new Object();
            final Runnable fr = r;
            Runnable rn = new Runnable() {

                @Override
                public void run() {
                    synchronized(lock) {
                        fr.run();
                        lock.notifyAll();
                    }
                }

            };
            synchronized(lock) {
                mHandler.post(rn);
                try {
//                    Log.d(TAG, "lock.wait()");
                    lock.wait();
//                    Log.d(TAG, "lock.wait() end");
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                }
            }
        }
    }


    private class ShutDownObserver extends ContentObserver {
        private final Uri NAVI_START_STOP_URI =
                Settings.System.getUriFor(GlobalStatus.NAVI_START_STOP);
        public ShutDownObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (NAVI_START_STOP_URI.equals(uri)) {
                checkStatus(false);
            }
        }

        public void startObserving() {
            final ContentResolver cr = getContentResolver();
            cr.unregisterContentObserver(this);
            cr.registerContentObserver(
                    NAVI_START_STOP_URI,
                    false, this);
        }

        public void stopObserving() {
            final ContentResolver cr = getContentResolver();
            cr.unregisterContentObserver(this);
        }
    }


    /**
     * ContentObserver to watch dvr fullscreen
     **/
    private class SmartMirrorsObserver extends ContentObserver {

        private final Uri DVR_FULLSCREEN_SHOW_URI =
                Settings.System.getUriFor(DVR_FULLSCREEN_SHOW);
        public SmartMirrorsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (selfChange) return;
            if (DVR_FULLSCREEN_SHOW_URI.equals(uri)) {
                if (1 == Settings.System.getInt(getContentResolver(), DVR_FULLSCREEN_SHOW, 0)) {
                    Log.e("====", "====SmartMirrorsObserver.onChange=DB_NAVI_FORWARD");
                } else if (0 == Settings.System.getInt(getContentResolver(), DVR_FULLSCREEN_SHOW, 0)) {
                    Log.e("====", "====SmartMirrorsObserver.onChange=DB_NAVI_BACK");
//                    finish();
                    if(mImpl != null){
                        mImpl.hide();
                    }
                }
            }
        }

        public void startObserving() {
            final ContentResolver cr = getContentResolver();
            cr.unregisterContentObserver(this);
            cr.registerContentObserver(
                    DVR_FULLSCREEN_SHOW_URI,
                    false, this);
        }

        public void stopObserving() {
            final ContentResolver cr = getContentResolver();
            cr.unregisterContentObserver(this);
        }
    }
    /* zhangzhaolei add for switch dvr fullscreen preview 20170328 end */

    /**
     * 设置为前台服务
     */
    private void setServerForeground() {
        Log.d(TAG, "setServerForeground");
        Intent notificationIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(this, WelcomeActivity.class))
                .setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.btn_call)//必须要先setSmallIcon，否则会显示默认的通知，不显示自定义通知
                .setTicker("视频对讲服务正在运行")
                .setContentTitle("视频对讲服务正在运行")
                .setContentText("")
                .setContentIntent(pendingIntent)
                .build();
        startForeground(2323, notification);
    }

    public boolean openCamera(){
        UsbCamera usbCamera = null;
        CameraVideo mCameraVideo = null;
        int[] size = new int[2];
        DvrConfig.getVideoSize(size);
        if(GlobalStatus.getUsbVideo2() != null){
            return true;
        } else {
            usbCamera = new UsbCamera();
            String dev = getResources().getStringArray(R.array.video_devs)[1];
            String product = Build.PRODUCT;
            if (product != null && product.equals("LB1728V4")) {
                dev = getResources().getStringArray(R.array.video_devs)[1];
            }
            boolean isOpen = usbCamera.open(dev, size);
            if (!isOpen) {
                usbCamera = null;
                GlobalStatus.setUsbVideo2(null);
                return false;
            } else {
                GlobalStatus.setUsbVideo2(usbCamera);
                return true;
            }
        }
    }

    public void checkStatus(boolean screenOn){
        DvrService dvrService = DvrService.this;
        int type = GlobalStatus.getShutDownType(dvrService);
        Log.v(TAG,"checkStatus type:" + type);
        dvrService.stopRecord();
        if(mImpl != null) {
            mImpl.release();
        }

        RESClient.getInstance().removeSurfaceView();

        if (GlobalStatus.getUsbVideo1() != null) {
            GlobalStatus.getUsbVideo1().close();
            GlobalStatus.setUsbVideo1(null);
        }
        if(GlobalStatus.getCamera() != null){
            GlobalStatus.getCamera().stopPreview();
            GlobalStatus.getCamera().release();
            GlobalStatus.setCamera(null);
        }

        if (1 == type || screenOn) {
            System.exit(0);
        } else if (0 == type) {
            //TODO 熄火
            //DvrService.this.stopSelf();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    VideoRoadUtils.stopUSBCamera();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.exit(0);
                }
            }).start();
        }
    }
}
