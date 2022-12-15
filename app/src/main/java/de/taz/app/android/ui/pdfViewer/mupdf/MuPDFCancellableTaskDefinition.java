package de.taz.app.android.ui.pdfViewer.mupdf;

import android.util.Log;

import com.artifex.mupdf.fitz.Cookie;

import io.sentry.Sentry;

public abstract class MuPDFCancellableTaskDefinition<Params, Result> implements CancellableTaskDefinition<Params, Result>
{
	private Cookie cookie;
	private String debugTag = "";

	public MuPDFCancellableTaskDefinition()
	{
		this.cookie = new Cookie();
	}

	public MuPDFCancellableTaskDefinition(String debugTag)
	{
		this();
		this.debugTag = debugTag;
	}


	@Override
	public void doCancel()
	{
		if (cookie == null)
			return;

		Log.d("MuPDF", "Render task canceled [tag: " + debugTag + "]");
		cookie.abort();
	}

	@Override
	public void doCleanup()
	{
		if (cookie == null)
			return;

		Log.d("MuPDF", "Render task cleanup [tag: " + debugTag + "]");
		cookie.destroy();
		cookie = null;
	}

	@Override
	public final Result doInBackground(Params ... params)
	{
		Log.d("MuPDF", "Render task started [tag: " + debugTag + "]");
		try {
			Result result = doInBackground(cookie, params);
			Log.d("MuPDF", "Render task finished [tag: " + debugTag + "]");
			return result;
		} catch (RuntimeException e) {
			String hint = "Render task failed [tag: " + debugTag + "]\n" +
					"Exception will be ignored";
			Log.e("MuPDF", hint , e);
			Sentry.captureException(e, hint);
			return null;
		}
	}

	public abstract Result doInBackground(Cookie cookie, Params ... params);
}
