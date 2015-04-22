package jp.mau.jitakukeibi.activity;

import jp.mau.jitakukeibi.R;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class AboutActivity  extends ActivityBase {
	public final static int		TESTFUNC_MOVINGAVR	= 0;
	public final static int		TESTFUNC_GAUSSIAN	= 1;
	public final static int		TESTFUNC_MEDIAN		= 2;

	public final static String	TESTFUNC_FILTER_KEY	= "FILTER";

	private SharedPreferences	_pref;
	private int					_testfuncSetValue;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		Resources				res					= getResources();
		_pref = PreferenceManager.getDefaultSharedPreferences(this);

		_testfuncSetValue							= _pref.getInt(TESTFUNC_FILTER_KEY, TESTFUNC_MOVINGAVR);
		final CharSequence		testfuncSelector[]	= {
				res.getString(R.string.testfunc_select_moveAvr),
				res.getString(R.string.testfunc_select_gaussian),
				res.getString(R.string.testfunc_select_median),
		};

		TextView text = (TextView)findViewById(R.id.about_text_version);
		text.setText("Ver." + getVersionName());

		TextView				helpText			= (TextView)findViewById(R.id.activity_about_help);
		helpText.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new AlertDialog.Builder(AboutActivity.this)
					.setTitle(R.string.testfunc_dialog_title)
					.setSingleChoiceItems(testfuncSelector, _testfuncSetValue, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							_testfuncSetValue		= which;
							SharedPreferences.Editor edit	= _pref.edit();
							edit.putInt(TESTFUNC_FILTER_KEY, _testfuncSetValue);
							edit.commit();
							dialog.dismiss();
						}
					})
					.show();
			}
		});
	}
}
