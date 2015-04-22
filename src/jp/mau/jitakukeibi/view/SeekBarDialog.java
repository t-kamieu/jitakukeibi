package jp.mau.jitakukeibi.view;

import java.util.ArrayList;

import jp.mau.jitakukeibi.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class SeekBarDialog {
	private static final int		WRAP_CONTENT	= ViewGroup.LayoutParams.WRAP_CONTENT;

	private Context					_context;
	private AlertDialog.Builder		_builder;
	private ArrayList<Slider>		_sliders;

	public SeekBarDialog (Context context) {
		_context 					= context;
		_sliders					= new ArrayList<Slider>();
		_builder 					= new AlertDialog.Builder (_context);
	}

	public SeekBarDialog addSlider (int max, int progress, String text) {
		_sliders.add(new Slider(_context).init(max, progress, text));
		return this;
	}

	/** ダイアログを表示する */
	public void show () {
		LinearLayout	layout		= new LinearLayout(_context);
		layout.setOrientation(LinearLayout.VERTICAL);
		for (Slider s: _sliders) {
			layout.addView(s);
		}

		_builder
		.setView(layout)
		.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		_builder.create().show();
	}

	/**
	 * 初期値を設定
	 */
	public SeekBarDialog setValue (int index, int value) {
		_sliders.get(index).setValue(value);
		return this;
	}

	/** OKを押された時の挙動を設定する */
	public SeekBarDialog setPositiveButton (DialogInterface.OnClickListener listener) {
		_builder.setPositiveButton(R.string.button_ok, listener);
		return this;
	}

	public int getValue (int index) {
		return _sliders.get(index).getValue();
	}

	class Slider extends LinearLayout {
		private SeekBar					_seekBar;
		private TextView				_textView;
		private TextView				_showValue;

		public Slider (Context context) {
			super (context);
			_seekBar				= new SeekBar(context);
			_textView				= new TextView(context);
			_showValue				= new TextView(context);
			this.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout.LayoutParams params	= new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
			params.gravity			= Gravity.CENTER_VERTICAL;
			params.rightMargin		= 12;
			params.leftMargin		= 12;
			this.addView(_textView, params);
			this.addView(_seekBar, new LinearLayout.LayoutParams(300, WRAP_CONTENT));
			this.addView(_showValue, params);

			_seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {}
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					_showValue.setText(String.format("%1$3d", progress));
				}
			});
		}

		int getValue () {
			return _seekBar.getProgress();
		}

		Slider init (int max, int progress, String text) {
			_seekBar.setMax(max);
			_seekBar.setProgress(progress);
			_textView.setText(text);
			_showValue.setText(String.format("%1$3d", progress));
			return this;
		}

		void setValue (int value) {
			_seekBar.setProgress(value);
		}
	}
}
