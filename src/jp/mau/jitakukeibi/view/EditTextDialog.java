package jp.mau.jitakukeibi.view;

import jp.mau.jitakukeibi.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

public class EditTextDialog {

	private Context					_context;
	private EditText 				_input;
	AlertDialog.Builder				_builder;

	public EditTextDialog(Context context) {
		_context = context;
		_input = new EditText(_context);

		_builder = new AlertDialog.Builder (_context)
			.setView(_input)
			.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
	}

	/** ダイアログを表示する */
	public void show () {
		_builder.create().show();
	}

	/**
	 * 初期値を設定
	 * @param str
	 * @return
	 */
	public EditTextDialog setText (String str) {
		_input.setText(str);
		return this;
	}

	/** OKを押された時の挙動を設定する */
	public EditTextDialog setPositiveButton (DialogInterface.OnClickListener listener) {
		_builder.setPositiveButton(R.string.button_ok, listener);
		return this;
	}

	public String getText () {
		return _input.getText().toString();
	}
}
