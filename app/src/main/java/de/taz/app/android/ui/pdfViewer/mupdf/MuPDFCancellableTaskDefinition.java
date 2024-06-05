package de.taz.app.android.ui.pdfViewer.mupdf;

import com.artifex.mupdf.fitz.Cookie;

import de.taz.app.android.sentry.SentryWrapper;
import de.taz.app.android.util.Log;


public abstract class MuPDFCancellableTaskDefinition<Params, Result> implements CancellableTaskDefinition<Params, Result>
{
	private Cookie cookie;

	private final Log log = new Log(MuPDFCancellableTaskDefinition.class.getName());
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

		log.debug("Render task canceled [tag: " + debugTag + "]", null);
		cookie.abort();
	}

	@Override
	public void doCleanup()
	{
		if (cookie == null)
			return;

		log.debug("Render task cleanup [tag: " + debugTag + "]", null);
		cookie.destroy();
		cookie = null;
	}

	@Override
	public final Result doInBackground(Params ... params)
	{
		log.debug("Render task started [tag: " + debugTag + "]", null);
		try {
			Result result = doInBackground(cookie, params);
			log.debug("Render task finished [tag: " + debugTag + "]", null);
			return result;
		} catch (RuntimeException e) {
			String hint = "Render task failed [tag: " + debugTag + "]\n" +
					"Exception will be ignored";
			log.error(hint , e);
			SentryWrapper.INSTANCE.captureException(e);
			return null;
		}
	}

	public abstract Result doInBackground(Cookie cookie, Params ... params);
}
