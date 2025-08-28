package de.taz.app.android.ui.pdfViewer.mupdf;

public interface CancellableTaskDefinition <Params, Result>
{
	Result doInBackground(Params... params);
	void doCancel();
	void doCleanup();
}
