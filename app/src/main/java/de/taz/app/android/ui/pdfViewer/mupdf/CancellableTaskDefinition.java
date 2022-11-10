package de.taz.app.android.ui.pdfViewer.mupdf;

public interface CancellableTaskDefinition <Params, Result>
{
	public Result doInBackground(Params ... params);
	public void doCancel();
	public void doCleanup();
}
