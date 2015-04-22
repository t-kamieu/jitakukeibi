package jp.mau.jitakukeibi.activity;

import jp.mau.jitakukeibi.BuildConfig;
import jp.mau.jitakukeibi.R;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager.BadTokenException;
import android.widget.Toast;

public class ActivityBase extends FragmentActivity {

	protected String 				TAG = 				getClass().getSimpleName();
	protected static final int 		RESULT_CLOSE = 		-1;
	protected Handler 				_handler;
	protected AlertDialog 			_alertDialog;
	protected ProgressDialog 		_loadingDialog;

	/**
	 * 読み込みダイアログを非表示
	 */
	public void closeLoading () {
		if (!_loadingDialog.isShowing()) return;
		_loadingDialog.dismiss();
	}

	/**
	 * デバッグログの出力
	 * @param str ログ
	 */
	public void debug (Object str) {
		if (BuildConfig.DEBUG) {
			StackTraceElement[] stack = new Throwable().getStackTrace();
			String methodname = stack[1].getMethodName();
			int line = stack[1].getLineNumber();
			StringBuilder builder = new StringBuilder(60);
			builder.append("□■□■ ").append(methodname)
					.append("(").append(line).append(") ").append(str);
			Log.v (TAG, builder.toString());
		}
	}

	/**
	 * アクティビティの修了
	 */
	public void finishActivity () {
		setResult(RESULT_CLOSE);
		this.finish();
	}

	/**
	 * リソースから色を取得する
	 * @param id リソースID
	 * @return 色コード
	 */
	public int getColor (int id) {
		return getResources().getColor(id);
	}

	/**
	 * アプリのバージョンコードを取得する
	 * @return
	 */
	public int getVersionCode () {
		try {
			return getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA).versionCode;
		} catch (Exception ex) {
			ex.printStackTrace();
			return 0;
		}
	}

	/**
	 * アプリのバージョンコードを取得する
	 * @return
	 */
	public String getVersionName () {
		try {
			return getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA).versionName;
		} catch (Exception ex) {
			ex.printStackTrace();
			return "";
		}
	}

	/**
	 * 指定した文字列がnullまたは空であるか,空白文字だけで構成されているかどうかを示す
	 * @param str 指定する文字列
	 * @return nullまたは空であるか,空白文字だけである場合はtrue
	 */
	public boolean isNullOrWhiteSpace (String str) {
		if (str == null) return true;
		str = str.replace (" ", "");
		str = str.replace ("　", "");
		if (str.equalsIgnoreCase("")) {
			return true;
		}
		return false;
	}

	protected void moveActivity (Class<?> cls) {
		showActivity(cls, null);
		finishActivity ();
	}

	protected void moveActivity (Class<?> cls, Bundle extras) {
		showActivity (cls, extras);
		finishActivity ();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_CLOSE) {
			setResult (RESULT_CLOSE);
			finish ();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		_handler = new Handler();
		super.onCreate(savedInstanceState);
	}

	protected int parseIntHex (String str) {
		if (Integer.parseInt(str.substring(0, 1), 16) < 8) {
			return Integer.parseInt(str, 16);
		}
		String strSub = str.substring(1, 8);
		if (str.substring(0, 1).equals("F")) {
			return -2147483648 + Integer.parseInt("7" + strSub,16);
		}
		if (str.substring(0, 1).equals("E")) {
			return -2147483648 + Integer.parseInt("6" + strSub,16);
		}
		if (str.substring(0, 1).equals("D")) {
			return -2147483648 + Integer.parseInt("5" + strSub,16);
		}
		if (str.substring(0, 1).equals("C")) {
			return -2147483648 + Integer.parseInt("4" + strSub,16);
		}
		if (str.substring(0, 1).equals("B")) {
			return -2147483648 + Integer.parseInt("3" + strSub,16);
		}
		if (str.substring(0, 1).equals("A")) {
			return -2147483648 + Integer.parseInt("2" + strSub,16);
		}
		if (str.substring(0, 1).equals("9")) {
			return -2147483648 + Integer.parseInt("1" + strSub,16);
		}
		if (str.substring(0, 1).equals("8")) {
			return -2147483648 + Integer.parseInt("0" + strSub,16);
		}
		return 0;
	}

	protected int px2dip (int px) {
		int dip = 0;
		DisplayMetrics metrics = new DisplayMetrics();
		this.getWindowManager ().getDefaultDisplay().getMetrics(metrics);
		dip = (int)metrics.scaledDensity * px;
		return dip;
	}

	/**
	 * アクティビティのリロード
	 */
	public void reloadActivity () {
		startActivity(getIntent());
		this.finish();
	}

	/**
	 * 読み込みダイアログの共通設定
	 */
	private void setLoadingDialog () {
		_loadingDialog = new ProgressDialog(this);
		_loadingDialog.setTitle(getString(R.string.loading_title));
		_loadingDialog.setMessage(getString(R.string.loading_message));
		_loadingDialog.setProgress(ProgressDialog.STYLE_SPINNER);
		_loadingDialog.setCancelable(true);
	}

	/**
	 * 指定したアクティビティへ移動
	 * @param cls 移動先アクティビティクラス
	 */
	protected void showActivity (Class<?> cls) {
		showActivity(cls, null);
	}
	/**
	 * 指定したアクティビティへ移動
	 * @param cls 移動先アクティビティクラス
	 * @param extras 付加情報
	 */
	protected void showActivity (Class<?> cls, Bundle extras) {
		Intent i = new Intent (this,cls);
		if (extras != null) i.putExtras(extras);
		startActivityForResult(i, 0);
	}

	/**
	 * アラートダイアログの表示
	 * @param message メッセージ
	 */
	protected void showAlert (String message) {
		showAlert(null, message, getString(R.string.button_ok), false);
	}

	/**
	 * アラートダイアログの表示
	 * @param title タイトル
	 * @param message メッセージ
	 * @param button ボタンテキスト
	 * @param needFinish ダイアログ終了時にアクティビティも終了するか
	 */
	protected void showAlert (String title, String message, String button, final boolean needFinish) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (title != null) builder.setTitle(title);
		if (message != null) builder.setMessage(message);
		if (button != null) builder.setNegativeButton(button, null);
		_handler.post(new Runnable() {
			public void run() {
				_alertDialog = builder.create();
				if (needFinish) {
					_alertDialog.setOnDismissListener(new OnDismissListener() {
						public void onDismiss(DialogInterface dialog) {
							finish ();
						}
					});
				}
				try {
					_alertDialog.show();
				} catch (BadTokenException ex) {
					ex.printStackTrace();
				}
			}
		});
	}


	/**
	 * 確認ダイアログの表示
	 * @param message ダイアログメッセージ
	 * @param listener ポジティブボタン押下時の動作
	 */
	protected void showConfirm (String message, OnClickListener listener) {
		showConfirm(null, message, null, null, listener);
	}

	/**
	 * 確認ダイアログの表示
	 * @param title ダイアログタイトル
	 * @param message ダイアログメッセージ
	 * @param pos ポジティブボタン名
	 * @param neg ネガティブボタン名
	 * @param listener ポジティブボタン押下時の動作
	 */
	protected void showConfirm (String title, String message, String pos, String neg, OnClickListener listener) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (title != null) builder.setTitle(title);
		if (message != null) builder.setMessage(message);
		builder.setPositiveButton((pos != null) ? pos : getString(R.string.button_ok), listener);
		builder.setNegativeButton((neg != null) ? neg : getString(R.string.button_cancel), null);
		_handler.post(new Runnable() {
			public void run() {
				_alertDialog = builder.create();

				try {
					_alertDialog.show();
				} catch (BadTokenException ex) {
					ex.printStackTrace();
				}
			}
		});
	}

	/**
	 * 読み込みダイアログの表示
	 */
	public void showLoadingDialog () {
		setLoadingDialog ();
		_loadingDialog.show();
	}

	/**
	 * 中止ボタン付き読み込みダイアログの表示
	 * @param listener 中止ボタンの動作
	 */
	public void showLoadingDialog (OnCancelListener listener) {
		setLoadingDialog();
		_loadingDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		_loadingDialog.setOnCancelListener(listener);
		_loadingDialog.show();
	}

	/**
	 * トーストの表示
	 * @param resId リソースID
	 */
	protected void showToast (int resId) {
		showToast(getString(resId));
	}


	/**
	 * トーストの表示
	 * @param str 表示するテキスト
	 */
	protected void showToast (String str) {
		Toast.makeText(this, str, Toast.LENGTH_LONG).show();
	}
}
