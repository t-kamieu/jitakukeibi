package jp.mau.jitakukeibi.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jp.mau.jitakukeibi.R;
import jp.mau.jitakukeibi.view.SeekBarDialog;
import jp.mau.jitakukeibi.view.TwoLinesCheckView;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images;
import android.support.v4.app.DialogFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;

public class MainActivity extends ActivityBase {
///////////////////////////////////////////////////////////////////////////////////////////////////
// Sub Classes
///////////////////////////////////////////////////////////////////////////////////////////////////
	/** インターバル撮影を行う非同期クラス */
	class IntervalTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
		if (_isRunnningPhoto) {
			this.cancel(true);
		} else {
			showStat (true);
			_isRunnningPhoto = true;
		}
		super.onPreExecute();
		}

		@Override
		protected Void doInBackground(Void... arg0) {
			while (_isRunnningPhoto) {
				debug ("TakePhoto");
				if (_settings.autoFocus) {
					_camera.autoFocus(_autofocusListener);
				} else {
					if (_camera != null)
						_camera.setPreviewCallback(_previewListener);
				}
				try {
					Thread.sleep(_settings.interval);
				} catch (InterruptedException ex) {
					return null;
				} catch (Exception ex) {
					ex.printStackTrace();
					break;
				}
			}
			return null;
		}

		@Override
		protected void onCancelled() {
		showStat (false);
		_isRunnningPhoto = false;
//		_oldInt = null;
		_oldTake									= null;
		super.onCancelled();
		}
	}

	/** 設定クラス */
	class Settings {
		/** 設定 */
		private SharedPreferences		_pref;
		/** インターバル時間(msec) */
		long							interval;
		/** ギャラリーに登録するかどうか */
		boolean							registGallery;
		/** オンタイマーを使用するか */
		boolean							useOnTimer;
		/** 開始する時間 */
		int								onTime;
		/** オフタイマーを使用するか */
		boolean							useOffTimer;
		/** 終了する時間 */
		int								offTime;
		/** オートフォーカスするか */
		boolean							autoFocus;
		/** 動体検知撮影をおこなうか */
		boolean							moveDetect;
		/** 動体検知のスレッショルド */
		int								moveDetectThreshold;
		/** 各カメラに対する解像度 */
		int[]							resolution;
		/** 変更領域を表示するか */
		boolean 						showDifferenceArea;
		/** 変更領域の枠色 */
		int								differenceAreaColor;
		/** 差分用画像フィルタ */
		int								filter;

		/** 各パラメーターを初期化 */
		Settings (Context context) {
			_pref = PreferenceManager.getDefaultSharedPreferences(context);
		}

		/** 設定を読み込む */
		void load () {
			interval 				= _pref.getLong("interval", 5000);
			registGallery 			= _pref.getBoolean("registGallery", true);
			autoFocus 				= _pref.getBoolean("autoFocus", true);

			moveDetect 				= _pref.getBoolean("moveDetect", false);
			moveDetectThreshold 	= _pref.getInt("moveDetectThreshold", 50);
			showDifferenceArea		= _pref.getBoolean("showDifferenceArea", true);
			differenceAreaColor		= _pref.getInt("differenceAreaColor", 0xFF0000);

			useOnTimer 				= _pref.getBoolean("useOnTimer", false);
			useOffTimer 			= _pref.getBoolean("useOffTimer", false);
			onTime 					= _pref.getInt("onTime", 0);
			offTime 				= _pref.getInt("offTime", 0);

			filter					= _pref.getInt(AboutActivity.TESTFUNC_FILTER_KEY, AboutActivity.TESTFUNC_MOVINGAVR);
		}

		/** 設定を保存する */
		void save () {
			Editor edit = _pref.edit();
			edit.putLong("interval", interval);
			edit.putBoolean("registGallery", registGallery);
			edit.putBoolean("useOnTimer", useOnTimer);
			edit.putBoolean("useOffTimer", useOffTimer);
			edit.putInt("onTime", onTime);
			edit.putInt("offTime", offTime);
			edit.putBoolean("autoFocus", autoFocus);
			edit.putBoolean("moveDetect", moveDetect);
			edit.putInt("moveDetectThreshold", moveDetectThreshold);
			edit.putBoolean("showDifferenceArea", showDifferenceArea);
			edit.putInt("differenceAreaColor", differenceAreaColor);
			for (int i = 0; i < _numberOfCameras; i ++) {
				edit.putInt("resolution-" + i, resolution[i]);
			}
			edit.commit();
			setInfoText ();
		}

		void loadResolutions () {
			resolution = new int[_numberOfCameras];
			for (int i = 0; i < _numberOfCameras; i ++) {
				resolution[i] = _pref.getInt("resolution-" + i, 0);
			}
		}
	}

	class TimerView extends LinearLayout {
		/** タイトルと設定値 */
		private LinearLayout textLayer;
		/** タイマーを使用するかしないかのチェックボックス */
		private CheckBox box;
		/** タイマーの説明テキスト */
		private TextView text;
		/** 設定値 */
		private TextView value;

		private int hour;
		private int min;

		public TimerView(final Context context) {
			super (context);
			this.setOrientation(LinearLayout.HORIZONTAL);
			this.setPadding(px2dip(6), px2dip(6), px2dip(6), px2dip(6));
			textLayer = new LinearLayout(CONTEXT);
			box = new CheckBox(CONTEXT);
			text = new TextView(CONTEXT);
			value = new TextView(CONTEXT);

			textLayer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					new TimePickerDialog (context, new TimePickerDialog.OnTimeSetListener() {
						@Override
						public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
							hour = hourOfDay;
							min = minute;
							value.setText(String.format("%1$02d", hour) + ":" + String.format("%1$02d", min));
						}
					}, hour, min, true)
						.show ();
				}
			});

			text.setTextAppearance(context, android.R.style.TextAppearance_Large);
			textLayer.setOrientation(LinearLayout.VERTICAL);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
			textLayer.addView(text, params);
			value.setTextAppearance(context, android.R.style.TextAppearance_Small);
			params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
			textLayer.addView(value);

			params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1);
			this.addView(textLayer, params);

			box.setGravity(Gravity.BOTTOM);
			box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					setChecked (isChecked);
				}
			});
			params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
			this.addView(box, params);
			value.setText(R.string.hello_world);
		}

		/** 説明テキストを設定する */
		public void setText (int id) {
			text.setText(getString(id));
		}

		/** チェックボックスのON/OFF変更 */
		public void setChecked (boolean checked) {
			box.setChecked(checked);
			textLayer.setEnabled(checked);
			value.setTextAppearance(CONTEXT, (checked) ? android.R.style.TextAppearance_Small : android.R.style.TextAppearance_Small_Inverse);
		}

		public void setTime (int i) {
			hour = i / 100;
			min = i % 100;
			value.setText(String.format("%1$02d", hour) + ":" + String.format("%1$02d", min));
		}

		public int getTime () {
			return hour * 100 + min;
		}

		public boolean getUse () {
			return box.isChecked();
		}
	}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Fields
///////////////////////////////////////////////////////////////////////////////////////////////////

	/** このActivity */
	private final  Context			CONTEXT				= this;
	/** ViewGroup.LayoutParams.WRAP_CONTENT */
	private final static int 		WRAP_CONTENT 		= ViewGroup.LayoutParams.WRAP_CONTENT;
	/** ViewGroup.LayoutParams.MATCH_PARENT */
	@SuppressLint("InlinedApi")
	private final static int		MATCH_PARENT		= ViewGroup.LayoutParams.MATCH_PARENT;

	/** 画面のルートとなるビュー */
	private RelativeLayout 			_root;
	/** プレビューを表示させるビュー */
	private SurfaceView 			_cameraView;
	/** 情報 */
	private TextView				_infoText;
	/** メニュー */
	private FrameLayout 			_menuLayer;

	/** シャッターボタン */
	private ImageButton				_shutterButton;
	/** 設定ボタン */
	private ImageButton 			_settingsButton;

	/** 設定 */
	private Settings 				_settings;

	private Handler					_onTimerHandler = new Handler();
	private Handler					_offTimerHandler = new Handler();

	/** カメラオブジェクト */
	private Camera					_camera;
	/** カメラオブジェクトのパラメーター */
	private Camera.Parameters 		_params;
	/** 使用しているカメラの番号(複数ある場合：API Lv.9以降) */
	private int						_cameraId = 0;
	/** カメラの数 */
	private int						_numberOfCameras;

	/** インターバル撮影がスタートしているかどうか */
	private boolean					_isRunnningPhoto = false;
	/** インターバル撮影を行う非同期タスク */
	private IntervalTask			_intervalTask;
	/** 撮影処理を行なっている最中か */
	private boolean					_takingPhoto = false;

	/** 動体検知用の前フレーム */
//	private int[]					_oldInt;
	private int[][]					_oldTake;

///////////////////////////////////////////////////////////////////////////////////////////////////
// Callback
///////////////////////////////////////////////////////////////////////////////////////////////////
	private Camera.AutoFocusCallback _autofocusListener = new Camera.AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			debug ("onAutoFocus");
			if (!_takingPhoto) {
				// この瞬間にcameraをクローズされた場合にerrorが起きないように
				if (camera != null)
					camera.autoFocus(null);
				// オートフォーカスしている間にcameraをクローズされた場合(ry
				if (camera != null)
					camera.setPreviewCallback(_previewListener);
			}
		}
	};

	private Camera.PreviewCallback _previewListener = new Camera.PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			_takingPhoto = true;

			final int width = camera.getParameters().getPreviewSize().width;
			final int height = camera.getParameters().getPreviewSize().height;
			debug ("width:" + width + ", height:" + height);
			int[] rgb = new int[(width * height)];
			final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
			decodeYUV420SP (rgb, data, width, height);

			// 動体検知
			if (_settings.moveDetect) {
				boolean				result;
				switch (_settings.filter) {
					case AboutActivity.TESTFUNC_MEDIAN :
						debug ("take photo with detect median filter");
						result							= median(rgb, width, height);
						break;
					case AboutActivity.TESTFUNC_GAUSSIAN :
						debug ("take photo with detect gaussian filter");
						result							= gaussianFilter(rgb, width, height);
						break;
					case AboutActivity.TESTFUNC_MOVINGAVR :
					default :
						debug ("take photo with detect moving avr filter");
						result							= avrFilter(rgb, width, height);
				}
				if (!result) {
					// 差分がない場合
					debug ("no difference");
					camera.setPreviewCallback(null);
					_takingPhoto = false;
					bitmap.recycle();
					return;
				}
//					int test[] = new int[(width -2) * (height - 2)];
//					for (int i = 0; i < width -2; i ++) {
//						for (int j = 0; j < height -2; j ++) {
//							test[j * (width-2) + i] = _oldTake[i][j];
//						}
//					}
//					bitmap.setPixels(test, 0, width - 2, 0, 0, width - 2, height - 2);
			}
			bitmap.setPixels(rgb, 0, width, 0, 0, width, height);

			camera.setPreviewCallback(null);
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MMdd_HHmmss_SSS", Locale.getDefault());
			Date now = new Date ();

			// ルートディレクトリを作成する(DCIM/[dir])
			final File dcimDir = new File(Environment.getExternalStorageDirectory(), getString(R.string.dcim_dir_name));
			if (!dcimDir.exists()) {
				dcimDir.mkdirs();
			}
			final File rootDir = new File(dcimDir, getString(R.string.directory_name));
			if (!rootDir.exists()) {
				rootDir.mkdirs();
			}
			File targetDir = null;
			// ギャラリーに登録するかどうかでSDカードの保存先を変更する
			if (_settings.registGallery) {
				SimpleDateFormat dayFormat = new SimpleDateFormat ("yyyy_MMdd_HH", Locale.getDefault());
				final File dayDir = new File(rootDir, dayFormat.format(now));
				if (!dayDir.exists()) {
					dayDir.mkdirs();
				}
				targetDir = dayDir;
			} else {
				SimpleDateFormat dayFormat = new SimpleDateFormat ("yyyy_MMdd", Locale.getDefault());
				SimpleDateFormat hourFormat = new SimpleDateFormat ("HH", Locale.getDefault());
				SimpleDateFormat minFormat = new SimpleDateFormat ("mm", Locale.getDefault());
				final File dayDir = new File(rootDir, dayFormat.format(now));
				if (!dayDir.exists()) {
					dayDir.mkdirs();
				}
				final File hourDir = new File(dayDir, hourFormat.format(now));
				if (!hourDir.exists()) {
					hourDir.mkdirs();
				}
				final File minDir = new File(hourDir, minFormat.format(now));
				if (!minDir.exists()) {
					minDir.mkdirs();
				}
				targetDir = minDir;
			}
			final File dstFile = new File (targetDir, dateFormat.format(now) + ".jpg");
			debug ("path:" + dstFile.getAbsolutePath());

			new Thread (new Runnable() {
				@Override
				public void run() {
					FileOutputStream fos = null;
					try {
						fos = new FileOutputStream(dstFile);
						bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
					} catch (Exception ex) {
						ex.printStackTrace();
					} finally {
						if (fos != null) {
							try {
								fos.close();
							} catch (Exception ex) {
								ex.printStackTrace();
							}
						}
						bitmap.recycle();
						registerContent (dstFile);
					}
				}
			}).start ();

			_takingPhoto = false;
		}
	};

//	private Camera.PreviewCallback _previewListenerOld = new Camera.PreviewCallback() {
//		@Override
//		public void onPreviewFrame(byte[] data, Camera camera) {
//			_takingPhoto = true;
//
//			final int width = camera.getParameters().getPreviewSize().width;
//			final int height = camera.getParameters().getPreviewSize().height;
//			debug ("width:" + width + ", height:" + height);
//			int[] rgb = new int[(width * height)];
//			final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//			decodeYUV420SP (rgb, data, width, height);
//
//			// 動体検知
//			if (_settings.moveDetect) {
//				if (_oldInt == null) {
//					_oldInt = new int[rgb.length];
//					for (int i = 0; i < rgb.length; i ++) {
//						_oldInt[i] = rgb[i];
//					}
//				} else {
//					int diffColor = _settings.differenceAreaColor | 0xff000000;
//					boolean hasDifference = false;
//					/** 差分のあった区間 */
//					int left	= Integer.MAX_VALUE;
//					int up 		= Integer.MAX_VALUE;
//					int right	= Integer.MIN_VALUE;
//					int bottom	= Integer.MIN_VALUE;
//					for (int i = 0; i < rgb.length; i ++) {
//						int diff = (_oldInt[i] & 0xff) - (rgb[i] & 0xff);
//						// 比較用フレームにコピー
//						_oldInt[i] = rgb[i];
//						// 差分があれば撮影へ
//						if (diff > _settings.moveDetectThreshold || diff < -1*_settings.moveDetectThreshold) {
//							hasDifference = true;
//							if (_settings.showDifferenceArea) {
//								if (i / width < up) 		up 		= i / width;
//								if (i / width > bottom)		bottom 	= i / width;
//								if (i % width < left)		left 	= i % width;
//								if (i % width > right)		right	= i % width;
//							}
////							rgb[i] = 0xFFFF0000;
//						}
//					}
//					// 差分がなかったら撮影せずに終了
//					if (!hasDifference) {
//						debug ("no difference");
//						camera.setPreviewCallback(null);
//						_takingPhoto = false;
//						bitmap.recycle();
//						return;
//					}
//					// 変更領域
//					if (_settings.showDifferenceArea) {
//						// 横線
//						for (int i = 0; i < right - left; i ++) {
//							rgb [width * up + left + i] = diffColor;
//							rgb [width * (up + 1) + left + i] = diffColor;
//							rgb [width * bottom + left + i] = diffColor;
//							rgb [width * (bottom - 1) + left + i] = diffColor;
//						}
//						// 縦線
//						for (int i = 0; i < bottom - up; i ++) {
//							rgb[width * (up + i) + left] = diffColor;
//							rgb[width * (up + i) + left + 1] = diffColor;
//							rgb[width * (up + i) + right] = diffColor;
//							rgb[width * (up + i) + right - 1] = diffColor;
//						}
//					}
//				}
//			}
//
//			bitmap.setPixels(rgb, 0, width, 0, 0, width, height);
//
//			camera.setPreviewCallback(null);
//			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MMdd_HHmmss_SSS", Locale.getDefault());
//			Date now = new Date ();
//
//			// ルートディレクトリを作成する(DCIM/[dir])
//			final File dcimDir = new File(Environment.getExternalStorageDirectory(), getString(R.string.dcim_dir_name));
//			if (!dcimDir.exists()) {
//				dcimDir.mkdirs();
//			}
//			final File rootDir = new File(dcimDir, getString(R.string.directory_name));
//			if (!rootDir.exists()) {
//				rootDir.mkdirs();
//			}
//			File targetDir = null;
//			// ギャラリーに登録するかどうかでSDカードの保存先を変更する
//			if (_settings.registGallery) {
//				SimpleDateFormat dayFormat = new SimpleDateFormat ("yyyy_MMdd_HH", Locale.getDefault());
//				final File dayDir = new File(rootDir, dayFormat.format(now));
//				if (!dayDir.exists()) {
//					dayDir.mkdirs();
//				}
//				targetDir = dayDir;
//			} else {
//				SimpleDateFormat dayFormat = new SimpleDateFormat ("yyyy_MMdd", Locale.getDefault());
//				SimpleDateFormat hourFormat = new SimpleDateFormat ("HH", Locale.getDefault());
//				SimpleDateFormat minFormat = new SimpleDateFormat ("mm", Locale.getDefault());
//				final File dayDir = new File(rootDir, dayFormat.format(now));
//				if (!dayDir.exists()) {
//					dayDir.mkdirs();
//				}
//				final File hourDir = new File(dayDir, hourFormat.format(now));
//				if (!hourDir.exists()) {
//					hourDir.mkdirs();
//				}
//				final File minDir = new File(hourDir, minFormat.format(now));
//				if (!minDir.exists()) {
//					minDir.mkdirs();
//				}
//				targetDir = minDir;
//			}
//			final File dstFile = new File (targetDir, dateFormat.format(now) + ".jpg");
//			debug ("path:" + dstFile.getAbsolutePath());
//
//			new Thread (new Runnable() {
//				@Override
//				public void run() {
//					FileOutputStream fos = null;
//					try {
//						fos = new FileOutputStream(dstFile);
//						bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
//					} catch (Exception ex) {
//						ex.printStackTrace();
//					} finally {
//						if (fos != null) {
//							try {
//								fos.close();
//							} catch (Exception ex) {
//								ex.printStackTrace();
//							}
//						}
//						bitmap.recycle();
//						registerContent (dstFile);
//					}
//					// シンボリックリンクの作成
//					try {
//						File dcim = new File (Environment.getExternalStorageDirectory(), "DCIM");
//						if (!dcim.exists()) dcim.mkdirs();
//						File andro = new File (dcim, "100ANDRO");
//						if (!andro.exists()) andro.mkdirs();
//						File sLink = new File (andro, dstFile.getName());
//						debug ("ln -s " + sLink.getAbsolutePath() + " " + dstFile.getAbsolutePath());
//						Process process = Runtime.getRuntime().exec("ln -s " + sLink.getAbsolutePath() + " " + dstFile.getAbsolutePath());
//						process.waitFor();
//					} catch (Exception ex) {
//						ex.printStackTrace();
//					}
//				}
//			}).start ();
//
//			_takingPhoto = false;
//		}
//	};

	/**
	 * SurfaceViewの変更を受け取る
	 */
	private SurfaceHolder.Callback _surfaceListener = new SurfaceHolder.Callback() {
		@Override
		public void surfaceDestroyed (SurfaceHolder holder) {
			debug ("surfaceDestroyed");
			if (_camera != null) {
				_camera.stopPreview();
				_camera.release();
				_camera 							= null;
			}
			if (_intervalTask != null) {
				_intervalTask.cancel(true);
				_onTimerHandler.removeCallbacks(_onTimerRunnable);
				_offTimerHandler.removeCallbacks(_offTimerRunnable);
			}
		}
		@SuppressLint("NewApi")
		@Override
		public void surfaceCreated (SurfaceHolder holder) {
			debug ("surfaceCreated");
			if (_camera != null) {
				return;
			}
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					_camera = Camera.open(0);
					_numberOfCameras 				= Camera.getNumberOfCameras();
				} else {
					_camera 						= Camera.open();
					_numberOfCameras 				= 1;
				}
				_camera.setPreviewDisplay(holder);
				_settings.loadResolutions ();
			} catch (Exception ex) {
				if (_camera != null) {
					_camera.release();
					_camera 						= null;
				}
			}
			timer ();
		}
		@Override
		public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {
			debug ("surfaceChanged");
			if (_camera != null && !_isRunnningPhoto) {
				try {
					_camera.stopPreview();
					_params 						= _camera.getParameters();
					List<Camera.Size> sizeList 		= _params.getSupportedPreviewSizes();

					_params.setPreviewSize(sizeList.get(_settings.resolution[_cameraId]).width, sizeList.get(_settings.resolution[_cameraId]).height);
					_camera.setParameters(_params);
					_camera.startPreview();
					setInfoText ();
				} catch (Exception ex) {
					showAlert(getString(R.string.error_cant_open_camera));
				}
			}
		}
	};

	private Runnable 			_onTimerRunnable	 = new Runnable() {
		@Override
		public void run() {
			debug ("onTimer");
			if (!_isRunnningPhoto) {
				_intervalTask 						= new IntervalTask();
				_intervalTask.execute();
			}
			timer ();
		}
	};

	private Runnable 			_offTimerRunnable 	= new Runnable() {
		@Override
		public void run() {
			debug ("offTimer");
			if (_isRunnningPhoto) {
				_intervalTask.cancel(true);
			}
			timer ();
		}
	};

///////////////////////////////////////////////////////////////////////////////////////////////////
// Image Filters
///////////////////////////////////////////////////////////////////////////////////////////////////
	// メディアンフィルタ
	private boolean median (int rgb[], int width, int height) {
		int						array[]				= new int[9];

		if (_oldTake == null) {
			_oldTake								= new int[width - 2][height - 2];
		}
		int						diff				= 0;

		int diffColor = _settings.differenceAreaColor | 0xff000000;
		boolean hasDifference = false;
		/** 差分のあった区間 */
		int 					left				= Integer.MAX_VALUE;
		int 					up 					= Integer.MAX_VALUE;
		int 					right				= Integer.MIN_VALUE;
		int 					bottom				= Integer.MIN_VALUE;

		for (int i = 1; i < width -1; i ++) {
			for (int j = 1; j < height -1; j ++) {
				// 配列追加
				for (int m = 0; m < 3; m ++) {
					for (int n = 0; n < 3; n ++) {
						array[3 * m + n]			= rgb[(i + m - 1) + (j + n - 1) * width];
					}
				}
				Arrays.sort(array);
				int				gray				= grayscale(array[4]);
				diff								= (gray & 0xFF) - (_oldTake[i-1][j-1] & 0xFF);
				if (diff > _settings.moveDetectThreshold || diff < -1 * _settings.moveDetectThreshold) {
					hasDifference = true;
					if (_settings.showDifferenceArea) {
						if (j < up)			up 		= j;
						if (j > bottom)		bottom	= j;
						if (i < left)		left	= i;
						if (i > right)		right	= i;
					}
				}
				_oldTake[i-1][j-1]					= gray;
			}
		}
		// 差分がなかったら撮影せずに終了
		if (!hasDifference) {
			return false;
		}
		// 変更領域
		if (_settings.showDifferenceArea) {
			// 横線
			debug (String.format("U:%d, D:%d, L:%d, R:%d", up, bottom, left, right));
			for (int i = 0; i < right - left; i ++) {
				rgb [width * up + left + i] 		= diffColor;
				rgb [width * (up + 1) + left + i] 	= diffColor;
				rgb [width * bottom + left + i] 	= diffColor;
				rgb [width * (bottom - 1) + left + i] = diffColor;
			}
			// 縦線
			for (int i = 0; i < bottom - up; i ++) {
				rgb[width * (up + i) + left] 		= diffColor;
				rgb[width * (up + i) + left + 1] 	= diffColor;
				rgb[width * (up + i) + right] 		= diffColor;
				rgb[width * (up + i) + right - 1] 	= diffColor;
			}
		}
		return true;
	}

	/** 平滑化 */
	private boolean avrFilter (int rgb[], int width, int height) {
		float					filter[][]			= {{1/9f,1/9f,1/9f},{1/9f,1/9f,1/9f},{1/9f,1/9f,1/9f}};
		return movAvr(filter, rgb, width, height);
	}

	/** ガウシアンフィルタ */
	private boolean gaussianFilter (int rgb[], int width, int height) {
		float					filter[][]			= {{1/16f,2/16f,1/16f},{2/16f,4/16f,2/16f},{1/16f,2/16f,1/16f}};
		return movAvr(filter, rgb, width, height);
	}

	/** フィルタ適用 */
	private boolean movAvr (float[][] filter, int rgb[], int width, int height) {
		int						sum					= 0;

		if (_oldTake == null) {
			_oldTake								= new int[width - 2][height - 2];
		}
		int						diff				= 0;

		int diffColor = _settings.differenceAreaColor | 0xff000000;
		boolean hasDifference = false;
		/** 差分のあった区間 */
		int left	= Integer.MAX_VALUE;
		int up 		= Integer.MAX_VALUE;
		int right	= Integer.MIN_VALUE;
		int bottom	= Integer.MIN_VALUE;

		for (int i = 1; i < width -1; i ++) {
			for (int j = 1; j < height -1; j ++) {
				// 配列追加
				sum									= filtered(filter, rgb, i, j, width);
				diff								= (sum & 0xFF) - (_oldTake[i-1][j-1] & 0xFF);
				if (diff > _settings.moveDetectThreshold || diff < -1 * _settings.moveDetectThreshold) {
					hasDifference = true;
					if (_settings.showDifferenceArea) {
						if (j < up)			up 		= j;
						if (j > bottom)		bottom	= j;
						if (i < left)		left	= i;
						if (i > right)		right	= i;
					}
				}
				_oldTake[i-1][j-1]					= sum;
			}
		}
		// 差分がなかったら撮影せずに終了
		if (!hasDifference) {
			return false;
		}
		// 変更領域
		if (_settings.showDifferenceArea) {
			// 横線
			debug (String.format("U:%d, D:%d, L:%d, R:%d", up, bottom, left, right));
			for (int i = 0; i < right - left; i ++) {
				rgb [width * up + left + i] 		= diffColor;
				rgb [width * (up + 1) + left + i] 	= diffColor;
				rgb [width * bottom + left + i] 	= diffColor;
				rgb [width * (bottom - 1) + left + i] = diffColor;
			}
			// 縦線
			for (int i = 0; i < bottom - up; i ++) {
				rgb[width * (up + i) + left] 		= diffColor;
				rgb[width * (up + i) + left + 1] 	= diffColor;
				rgb[width * (up + i) + right] 		= diffColor;
				rgb[width * (up + i) + right - 1]	 = diffColor;
			}
		}
		return true;
	}

	/** 画素のグレースケール化 */
	private int grayscale (int color) {
		int						gray				= ( ((color & 0xff0000) >> 16) * 2 + ((color & 0xff00) >> 8) * 4 + (color & 0xff) )/ 7;
		return (gray << 16) + (gray << 8) + gray;
	}

	/**
	 * フィルタを適用する
	 * @param filter フィルタ
	 * @param rgb 元画像
	 * @param i 操作画素
	 * @param j 操作画素
	 * @return
	 */
	private int filtered (float filter[][], int rgb[], int i, int j, int width) {
		int 					ret					= 0;
		float					rsum				= 0;
		float					gsum				= 0;
		float					bsum				= 0;
		int						startx				= -1 * filter.length / 2;
		int						starty				= -1 * filter[0].length / 2;
		for (int m = 0; m < filter.length; m ++) {
			for (int n = 0; n < filter[0].length; n ++) {
				rsum								+= (rgb[(i + m + startx) + (j + n + starty) * width] & 0xFF0000) * filter[m][n];
				gsum								+= (rgb[(i + m + startx) + (j + n + starty) * width] & 0xFF00) * filter[m][n];
				bsum								+= (rgb[(i + m + startx) + (j + n + starty) * width] & 0xFF) * filter[m][n];
			}
		}
		ret											= ( (( (int)rsum & 0xff0000) >> 16) * 2 + (( (int)gsum & 0xff00) >> 8) * 4 + ( (int)bsum & 0xff) )/ 7;
		return ret;
	}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Activity Class Implements Methods
///////////////////////////////////////////////////////////////////////////////////////////////////
	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// screen keep on
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		_settings 									= new Settings(this);
		_settings.load();

		_root 										= new RelativeLayout(this);
		RelativeLayout.LayoutParams params 			= new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		_cameraView 								= new SurfaceView(this);
		SurfaceHolder 			holder 				= _cameraView.getHolder();
		holder.addCallback(_surfaceListener);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		_root.addView(_cameraView, params);

		// 情報テキスト
		_infoText									= new TextView (this);
		_infoText.setBackgroundColor(Color.argb(172, 0, 0, 0));
		_infoText.setId(200);
		_root.addView(_infoText, new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		// 自社広告
		{
			LinearLayout		adLayout			= new LinearLayout(this);
			adLayout.setOrientation(LinearLayout.HORIZONTAL);
			adLayout.setBackgroundColor(Color.argb(192, 0, 0, 0));
			adLayout.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Uri uri = Uri.parse("https://play.google.com/store/apps/developer?id=mau");
					Intent intent = new Intent(Intent.ACTION_VIEW, uri);
					startActivity(intent);
				}
			});
			ImageView			myAppAd				= new ImageView(this);
			myAppAd.setImageResource(R.drawable.nicochannel_icon);
			adLayout.addView(myAppAd, 80, 80);
			TextView			adText				= new TextView(this);
			adText.setText(R.string.main_adText);
			adText.setGravity(Gravity.CENTER);
			params									= new RelativeLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
			adLayout.addView(adText, params);
			params									= new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
//			params.addRule(RelativeLayout.RIGHT_OF, 200);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL);
			_root.addView(adLayout, params);
		}

		// メニューボタン
		LinearLayout			buttonLayer 		= new LinearLayout(this);
		buttonLayer.setOrientation(LinearLayout.VERTICAL);

		// 画面を消す
		ImageButton				dimmerButton 		= new ImageButton(this);
		dimmerButton.setImageResource(R.drawable.displayoff);
		dimmerButton.setBackgroundColor(getColor(android.R.color.transparent));
		dimmerButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				final WindowManager.LayoutParams windowParams = getWindow().getAttributes();
				windowParams.screenBrightness 		= .1f;
				/** これが画面全体を覆う真っ黒背景 触られると消える */
				final FrameLayout blank 			= new FrameLayout (CONTEXT);
				blank.setBackgroundColor(Color.BLACK);
				blank.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						_root.removeView(blank);
						windowParams.screenBrightness = -1.f;
						getWindow().setAttributes(windowParams);
					}
				});
				RelativeLayout.LayoutParams params 	= new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				_root.addView(blank, params);
				getWindow().setAttributes(windowParams);
				if (Build.VERSION.SDK_INT >= 14) {
					v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
				}
			}
		});
		LinearLayout.LayoutParams llparams 			= new LinearLayout.LayoutParams(WRAP_CONTENT, 0, 1);
		buttonLayer.addView(dimmerButton, llparams);

		// インターバル撮影開始/終了
		_shutterButton 								= new ImageButton(this);
		_shutterButton.setImageResource(R.drawable.rec);
		_shutterButton.setBackgroundColor(getColor(android.R.color.transparent));
		_shutterButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startPhoto ();
			}
		});
		llparams 									= new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		buttonLayer.addView(_shutterButton, llparams);

		// 設定
		_settingsButton 							= new ImageButton(this);
		_settingsButton.setImageResource(R.drawable.settings);
		_settingsButton.setBackgroundColor(getColor(android.R.color.transparent));
		_settingsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openMenu();
			}
		});
		llparams 									= new LinearLayout.LayoutParams(WRAP_CONTENT, 0, 1);
		buttonLayer.addView(_settingsButton, llparams);

		params 										= new RelativeLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		_root.addView(buttonLayer, params);

		setContentView (_root);

	}

	@Override
	protected void onResume() {
		debug ("onResume");
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		debug ("onDestroy");
		super.onDestroy();
	}

	/**
	 * 画面クリックで暗くして背景も真っ黒にする
	 * ディスプレイが消えているように見せかける
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			debug ("ACTION_DOWN");
			if (_camera != null || !_isRunnningPhoto)
				_camera.autoFocus(null);
		}
		return true;
	}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Methods
///////////////////////////////////////////////////////////////////////////////////////////////////
	/** キャプチャしたrawデータをjpegに変換する */
	public static final void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
		final int frameSize = width * height;
		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0) y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}
				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);
				if (r < 0) r = 0; else if (r > 262143) r = 262143;
				if (g < 0) g = 0; else if (g > 262143) g = 262143;
				if (b < 0) b = 0; else if (b > 262143) b = 262143;
				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	}

	/** メニューレイヤーを作成する */
	@SuppressLint("NewApi")
	private void createMenu () {
		/** 画面全体を覆うビュー */
		_menuLayer = new FrameLayout(this);
		_menuLayer.setBackgroundColor(getColor(android.R.color.transparent));
		_menuLayer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				_root.removeView(_menuLayer);
			}
		});

		/** メニューバー */
		LinearLayout menu = new LinearLayout(this);
		menu.setBackgroundColor(getColor(R.color.main_menu_background));
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM);
		_menuLayer.addView(menu, params);

		/** 解像度設定ボタン */
		ImageButton resolutionButton = new ImageButton (this);
		resolutionButton.setImageResource(R.drawable.resolution);
		resolutionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openResolutionSettings ();
				_root.removeView(_menuLayer);
			}
		});
		LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		menu.addView(resolutionButton, lparams);

		/** インターバル設定ボタン */
		ImageButton intervalButton = new ImageButton (this);
		intervalButton.setImageResource(R.drawable.interval);
		intervalButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openIntervalSettings ();
				_root.removeView(_menuLayer);
			}
		});
		lparams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		menu.addView(intervalButton, lparams);

		/** フォーカス設定ボタン */
		ImageButton focusButton = new ImageButton (this);
		focusButton.setImageResource(R.drawable.focus);
		focusButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openFocusSettings ();
				_root.removeView(_menuLayer);
			}
		});
		lparams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		menu.addView(focusButton, lparams);

		/** 動体検知動体検知設定ボタン */
		ImageButton movedetectButton = new ImageButton (this);
		movedetectButton.setImageResource(R.drawable.moving);
		movedetectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openMoveDetectSettings ();
				_root.removeView(_menuLayer);
			}
		});
		lparams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		menu.addView(movedetectButton, lparams);

		/** ギャラリー登録設定ボタン(UNUESD) */
//		Button galleryButton = new Button (this);
//		galleryButton.setText(getString(R.string.dialog_title_gallerysettings));
//		galleryButton.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				openGallerySettings ();
//				_root.removeView(_menuLayer);
//			}
//		});
//		lparams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
//		menu.addView(galleryButton, lparams);

		/** タイマー設定ボタン */
		ImageButton timerButton = new ImageButton (this);
		timerButton.setImageResource(R.drawable.timer);
		timerButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openTimerSettings();
				_root.removeView(_menuLayer);
			}
		});
		lparams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		menu.addView(timerButton, lparams);

		// カメラ切り替え (複数カメラAPIはSDK Lv9(2.3 GINGERBREAD)以降)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			if (_numberOfCameras > 1) {
				/** カメラ切り替えボタン */
				ImageButton changeCameraButton = new ImageButton (this);
				changeCameraButton.setImageResource(R.drawable.change);
				changeCameraButton.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						// カメラ切り替え
						if (_camera != null) {
							_camera.stopPreview();
							_camera.release();
							_camera = null;
						}
						if (_intervalTask != null) {
							_intervalTask.cancel(true);
						}
						_cameraId = (_cameraId + 1) % _numberOfCameras;
						debug ("camera:" + _cameraId + ", number:" + _numberOfCameras);
						_camera = Camera.open(_cameraId);
						try {
							_camera.setPreviewDisplay(_cameraView.getHolder());
						} catch (Exception ex) {
							ex.printStackTrace();
						}
						_params = _camera.getParameters();
						final List<Camera.Size> sizeList = _params.getSupportedPreviewSizes();
						_params.setPreviewSize(sizeList.get(_settings.resolution[_cameraId]).width, sizeList.get(_settings.resolution[_cameraId]).height);
						_camera.setParameters(_params);
						_camera.startPreview();

						_root.removeView(_menuLayer);
						// カメラが変わると使用可能解像度が変わるので解像度設定をリセット
//						_settings.pixelNum = 0;
						_settings.save();
					}
				});
				lparams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
				menu.addView(changeCameraButton, lparams);
			}
		}

		// about
		/** aboutボタン */
		ImageButton aboutButton = new ImageButton (this);
		aboutButton.setImageResource(R.drawable.about);
		aboutButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showActivity(AboutActivity.class);
				_root.removeView(_menuLayer);
			}
		});
		lparams 									= new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		menu.addView(aboutButton, lparams);
	}

	/**
	 * メニューを開く
	 */
	private void openMenu () {
		// メニューレイヤーが作成されていない場合は作成する
		if (_menuLayer == null) {
			createMenu ();
		}
		RelativeLayout.LayoutParams params 			= new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
		_root.addView(_menuLayer, params);
	}

	/**
	 * 解像度設定を開く
	 */
	private void openResolutionSettings () {
		if (_camera != null) {
			_params = _camera.getParameters();
			final List<Camera.Size> sizeList = _params.getSupportedPreviewSizes();
			String items[] = new String[sizeList.size()];
			for (int i = 0; i < sizeList.size(); i ++) {
				items[i] = (sizeList.get(i).width + "x" + sizeList.get(i).height);
			}

			new AlertDialog.Builder(this)
				.setTitle (getString(R.string.dialog_title_resolutionsettings))
				.setSingleChoiceItems(items, _settings.resolution[_cameraId], new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						_settings.resolution[_cameraId] = which;
						_settings.save ();
						_camera.stopPreview();
						_params.setPreviewSize(sizeList.get(which).width, sizeList.get(which).height);
						_camera.setParameters(_params);
						_camera.startPreview();
						dialog.dismiss();
					}
				})
			.show();
		}
	}

	/** インターバル設定 */
	private void openIntervalSettings () {
		final EditText input = new EditText(this);
		float oldVal = (float)_settings.interval / 1000;
		debug (_settings.interval + " > " + oldVal );
		input.setText(String.valueOf(oldVal));
		input.selectAll();

		new AlertDialog.Builder (this)
			.setTitle(getString(R.string.dialog_title_intervalsettings) + getString(R.string.text_second))
			.setView(input)
			.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			})
			.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						long newInterval = (long)(Float.parseFloat(input.getText().toString()) * 1000);
						debug (newInterval);
						if (newInterval > 0) {
							_settings.interval = newInterval;
							_settings.save();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			})
		.show();
	}

	/**
	 * フォーカス設定を開く
	 */
	private void openFocusSettings () {
		String items[] = {getString(R.string.dialog_selector_focus_auto), getString(R.string.dialog_selector_focus_fixed)};
		new AlertDialog.Builder(this)
			.setTitle (getString(R.string.dialog_title_focussettings))
			.setSingleChoiceItems(items, _settings.autoFocus ? 0 : 1, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					_settings.autoFocus = (which == 0);
					_settings.save ();
					dialog.dismiss();
				}
			})
		.show();
	}

	/**
	 * 動体検知設定を開く
	 */
	private void openMoveDetectSettings () {

		LinearLayout layout = new LinearLayout (this);
		layout.setOrientation(LinearLayout.VERTICAL);
		final TwoLinesCheckView useMoveDetect = new TwoLinesCheckView(this);
		useMoveDetect.setTitle(R.string.dialog_selector_movedetect_use);
		useMoveDetect.setValue(getString(R.string.dialog_title_movedetectThreshold) + ": " + _settings.moveDetectThreshold);
		useMoveDetect.setTag(_settings.moveDetectThreshold);
		useMoveDetect.setOnClickListener(new OnClickListener() {
			// 動体検知を使用するをクリックしたとき
			@Override
			public void onClick(View v) {
				final SeekBarDialog seekbar		= new SeekBarDialog(CONTEXT);
				seekbar.addSlider(255, _settings.moveDetectThreshold, getString(R.string.dialog_title_movedetectThreshold))
				.setPositiveButton(new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						useMoveDetect.setValue(getString(R.string.dialog_title_movedetectThreshold) + ": " + seekbar.getValue(0));
						useMoveDetect.setTag(seekbar.getValue(0));
					}
				})
				.show ();
			}
		});
		layout.addView(useMoveDetect);

		final TwoLinesCheckView showDetectedArea = new TwoLinesCheckView(this);
		showDetectedArea.setTitle(R.string.dialog_selector_movedetect_area);
		showDetectedArea.setValue((_settings.differenceAreaColor >> 16 & 0xFF) + ", " + (_settings.differenceAreaColor >> 8 & 0xFF) + ", " + (_settings.differenceAreaColor & 0xFF));
		showDetectedArea.setChecked(_settings.showDifferenceArea);
		showDetectedArea.setTag(_settings.differenceAreaColor);
		showDetectedArea.setOnClickListener(new OnClickListener() {
			// 動体検知を使用するをクリックしたとき
			@Override
			public void onClick(View v) {
				final SeekBarDialog seekbar		= new SeekBarDialog(CONTEXT);
				seekbar
				.addSlider(255, _settings.differenceAreaColor >> 16	& 0xFF, "R")
				.addSlider(255, _settings.differenceAreaColor >> 8	& 0xFF, "G")
				.addSlider(255, _settings.differenceAreaColor		& 0xFF, "B")
				.setPositiveButton(new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showDetectedArea.setValue(seekbar.getValue(0) + ", " + seekbar.getValue(1) + ", " + seekbar.getValue(2));
						showDetectedArea.setTag((seekbar.getValue(0) << 16) | (seekbar.getValue(1) << 8) | seekbar.getValue(2));
					}
				})
				.show ();
			}
		});
		useMoveDetect.setRelatives(showDetectedArea);
		useMoveDetect.setChecked(_settings.moveDetect);
		layout.addView(showDetectedArea);

		new AlertDialog.Builder(this)
		.setView(layout)
		.setTitle(R.string.dialog_title_movedetectsettings)
		.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				_settings.moveDetectThreshold = (Integer)useMoveDetect.getTag();
				_settings.moveDetect = useMoveDetect.isChecked();
				_settings.differenceAreaColor = (Integer)showDetectedArea.getTag();
				_settings.showDifferenceArea = showDetectedArea.isChecked();
				_settings.save();
			}
		})
		.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		})
		.show();
	}

	/**
	 * ギャラリー登録設定を開く(UNUSED)
	 */
//	private void openGallerySettings () {
//		if (_camera != null) {
//			String[] items = new String[2];
//			items[0] = getString (R.string.dialog_selector_usegallery_yes);
//			items[1] = "";
//
//			new AlertDialog.Builder(this)
//				.setTitle (getString(R.string.dialog_title_resolutionsettings))
//				.setSingleChoiceItems(items, _settings.registGallery ? 0 : 1, new DialogInterface.OnClickListener() {
//					@Override
//					public void onClick(DialogInterface dialog, int which) {
//						_settings.registGallery = which == 0 ? true : false;
//						_settings.save ();
//						dialog.dismiss();
//					}
//				})
//			.show();
//		}
//	}

	/** タイマー設定を開く */
	private void openTimerSettings () {
		LinearLayout view = new LinearLayout(this);
		view.setOrientation(LinearLayout.VERTICAL);
		final TimerView onTimer = new TimerView(this);
		onTimer.setText(R.string.text_ontimer);
		onTimer.setChecked(_settings.useOnTimer);
		onTimer.setTime(_settings.onTime);
		final TimerView offTimer = new TimerView(this);
		offTimer.setText(R.string.text_offtimer);
		offTimer.setChecked(_settings.useOffTimer);
		offTimer.setTime(_settings.offTime);
		view.addView(onTimer);
		view.addView(offTimer);

		new AlertDialog.Builder(this)
			.setTitle(getString(R.string.dialog_title_timersettings))
			.setView(view)
			.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					_settings.onTime = onTimer.getTime();
					_settings.useOnTimer = onTimer.getUse();
					_settings.offTime = offTimer.getTime();
					_settings.useOffTimer = offTimer.getUse();
					_settings.save ();
					dialog.dismiss ();
					timer ();
				}
			})
			.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			})
			.show();
	}

	/**
	 * ギャラリーで表示されるように登録
	 * @param file File
	 */
	private void registerContent(final File file) {
		// ギャラリーに登録する設定がオフの場合は何もしない
		if (!_settings.registGallery) return;
		final ContentValues values = new ContentValues();
		ContentResolver contentResolver = getContentResolver();
		values.put(Images.Media.MIME_TYPE, "image/jpeg");
		values.put(Images.Media.DATE_MODIFIED,file.lastModified()/1000);
		values.put(Images.Media.DATE_TAKEN,file.lastModified());
		values.put(Images.Media.SIZE, file.length());
		values.put(Images.Media.TITLE, file.getName());
		values.put(Images.Media.DATA, file.getPath());
		contentResolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
	}

	/** 画面上部の設定値表示文字列を作成 */
	private void setInfoText () {
		String info = "";
		final List<Camera.Size> sizeList = _params.getSupportedPreviewSizes();
		info += sizeList.get(_settings.resolution[_cameraId]).width + "x" + sizeList.get(_settings.resolution[_cameraId]).height;
		info += "    ";
		info += ((float)_settings.interval / 1000) + " " + getString(R.string.text_second);
		_infoText.setText(info);
	}

	/** インターバル撮影の状態を表示する */
	private void showStat (boolean executed) {
		if (executed) {
			_shutterButton.setImageResource(R.drawable.stop);
		} else {
			_shutterButton.setImageResource(R.drawable.rec);
		}
		_settingsButton.setEnabled(!executed);
	}
	/**
	 * インターバル撮影の開始・終了
	 */
	private void startPhoto () {
		if (_isRunnningPhoto) {
			if (_intervalTask != null) {
				_intervalTask.cancel(true);
				_intervalTask = null;
			}
		} else {
			_intervalTask = new IntervalTask();
			_intervalTask.execute();
		}
	}

	/** オン・オフタイマーを設定する */
	private void timer () {
		_onTimerHandler.removeCallbacks(_onTimerRunnable);
		_offTimerHandler.removeCallbacks(_offTimerRunnable);
		if (_settings.useOnTimer) {
			timerSet (_settings.onTime, _onTimerHandler, _onTimerRunnable);
		}
		if (_settings.useOffTimer) {
			timerSet (_settings.offTime, _offTimerHandler, _offTimerRunnable);
		}
	}

	/** 時間を計算し,タイマーをセット */
	private void timerSet (int time, Handler handler, Runnable runnable) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());
		cal.set(Calendar.HOUR_OF_DAY, time / 100);
		cal.set(Calendar.MINUTE, time % 100);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
//		cal.add(Calendar.SECOND, 15);
		// タイマーの計算
		long difference = cal.getTimeInMillis() - System.currentTimeMillis();
		if (difference < 0) difference += 24 * 60 * 60 * 1000;
		debug ("the timer launches " + difference + "ms later.");
		handler.postDelayed(runnable, difference);
	}

	public static class MyAppDialog extends DialogFragment {
		public static MyAppDialog dialog (String param) {
			MyAppDialog			instance			= new MyAppDialog();
			Bundle				arguments			= new Bundle();
			arguments.putString("parameter", param);
			instance.setArguments(arguments);
			return instance;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			Dialog				dialog				= super.onCreateDialog(savedInstanceState);
			dialog.setTitle("my custom dialog");
			dialog.setCanceledOnTouchOutside(false);
			return dialog;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View				content				= inflater.inflate(R.layout.dialog_myapp_ad, null);
			return content;
		}
	}
}
