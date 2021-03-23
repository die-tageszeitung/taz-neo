package com.artifex.mupdfdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;


import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.viewer.PageAdapter;
import com.artifex.mupdf.viewer.PageView;
import com.artifex.mupdf.viewer.SearchTaskResult;
import com.artifex.mupdf.viewer.Stepper;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Stack;

import de.taz.app.android.R;

public class ReaderView extends AdapterView<Adapter> implements GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener, Runnable {
	private Context mContext;
	private boolean mLinksEnabled = false;
	private boolean tapDisabled = false;
	private int tapPageMargin;
	private static final int MOVING_DIAGONALLY = 0;
	private static final int MOVING_LEFT = 1;
	private static final int MOVING_RIGHT = 2;
	private static final int MOVING_UP = 3;
	private static final int MOVING_DOWN = 4;
	private static final int FLING_MARGIN = 100;
	private static final int GAP = 20;
	private static final float MIN_SCALE = 1.0F;
	private static final float MAX_SCALE = 64.0F;
	private static final boolean HORIZONTAL_SCROLLING = true;
	private PageAdapter mAdapter;
	protected int mCurrent;
	private boolean mResetLayout;
	public final SparseArray<View> mChildViews = new SparseArray(3);
	private final LinkedList<View> mViewCache = new LinkedList();
	private boolean mUserInteracting;
	private boolean mScaling;
	public float mScale = 1.0F;
	public int mXScroll;
	public int mYScroll;
	private GestureDetector mGestureDetector;
	private ScaleGestureDetector mScaleGestureDetector;
	private Scroller mScroller;
	private com.artifex.mupdf.viewer.Stepper mStepper;
	private int mScrollerLastX;
	private int mScrollerLastY;
	private float mLastScaleFocusX;
	private float mLastScaleFocusY;
	protected Stack<Integer> mHistory;

	public ReaderView(Context context) {
		super(context);
		this.setup(context);
	}

	public ReaderView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.setup(context);
	}

	public ReaderView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.setup(context);
	}

	private void setup(Context context) {
		this.mContext = context;
		this.mGestureDetector = new GestureDetector(context, this);
		this.mScaleGestureDetector = new ScaleGestureDetector(context, this);
		this.mScroller = new Scroller(context);
		this.mStepper = new Stepper(this, this);
		this.mHistory = new Stack();
		DisplayMetrics dm = new DisplayMetrics();
		@SuppressLint("WrongConstant") WindowManager wm = (WindowManager)this.mContext.getSystemService("window");
		wm.getDefaultDisplay().getMetrics(dm);
		this.tapPageMargin = (int)dm.xdpi;
		if (this.tapPageMargin < 100) {
			this.tapPageMargin = 100;
		}

		if (this.tapPageMargin > dm.widthPixels / 5) {
			this.tapPageMargin = dm.widthPixels / 5;
		}

	}

	public boolean popHistory() {
		if (this.mHistory.empty()) {
			return false;
		} else {
			this.setDisplayedViewIndex((Integer)this.mHistory.pop());
			return true;
		}
	}

	public void pushHistory() {
		this.mHistory.push(this.mCurrent);
	}

	public int getDisplayedViewIndex() {
		return this.mCurrent;
	}

	public void setDisplayedViewIndex(int i) {
		if (0 <= i && i < this.mAdapter.getCount()) {
			this.onMoveOffChild(this.mCurrent);
			this.mCurrent = i;
			this.onMoveToChild(i);
			this.mResetLayout = true;
			this.requestLayout();
		}

	}

	public void moveToNext() {
		View v = (View)this.mChildViews.get(this.mCurrent + 1);
		if (v != null) {
			this.slideViewOntoScreen(v);
		}

	}

	public void moveToPrevious() {
		View v = (View)this.mChildViews.get(this.mCurrent - 1);
		if (v != null) {
			this.slideViewOntoScreen(v);
		}

	}

	private int smartAdvanceAmount(int screenHeight, int max) {
		int advance = (int)((double)screenHeight * 0.9D + 0.5D);
		int leftOver = max % advance;
		int steps = max / advance;
		if (leftOver != 0) {
			if ((double)((float)leftOver / (float)steps) <= (double)screenHeight * 0.05D) {
				advance += (int)((double)((float)leftOver / (float)steps) + 0.5D);
			} else {
				int overshoot = advance - leftOver;
				if ((double)((float)overshoot / (float)steps) <= (double)screenHeight * 0.1D) {
					advance -= (int)((double)((float)overshoot / (float)steps) + 0.5D);
				}
			}
		}

		if (advance > max) {
			advance = max;
		}

		return advance;
	}

	public void smartMoveForwards() {
		View v = (View)this.mChildViews.get(this.mCurrent);
		if (v != null) {
			int screenWidth = this.getWidth();
			int screenHeight = this.getHeight();
			int remainingX = this.mScroller.getFinalX() - this.mScroller.getCurrX();
			int remainingY = this.mScroller.getFinalY() - this.mScroller.getCurrY();
			int top = -(v.getTop() + this.mYScroll + remainingY);
			int right = screenWidth - (v.getLeft() + this.mXScroll + remainingX);
			int bottom = screenHeight + top;
			int docWidth = v.getMeasuredWidth();
			int docHeight = v.getMeasuredHeight();
			int xOffset;
			int yOffset;
			if (bottom >= docHeight) {
				if (right + screenWidth > docWidth) {
					View nv = (View)this.mChildViews.get(this.mCurrent + 1);
					if (nv == null) {
						return;
					}

					int nextTop = -(nv.getTop() + this.mYScroll + remainingY);
					int nextLeft = -(nv.getLeft() + this.mXScroll + remainingX);
					int nextDocWidth = nv.getMeasuredWidth();
					int nextDocHeight = nv.getMeasuredHeight();
					yOffset = nextDocHeight < screenHeight ? nextDocHeight - screenHeight >> 1 : 0;
					if (nextDocWidth < screenWidth) {
						xOffset = nextDocWidth - screenWidth >> 1;
					} else {
						xOffset = right % screenWidth;
						if (xOffset + screenWidth > nextDocWidth) {
							xOffset = nextDocWidth - screenWidth;
						}
					}

					xOffset -= nextLeft;
					yOffset -= nextTop;
				} else {
					xOffset = screenWidth;
					yOffset = screenHeight - bottom;
				}
			} else {
				xOffset = 0;
				yOffset = this.smartAdvanceAmount(screenHeight, docHeight - bottom);
			}

			this.mScrollerLastX = this.mScrollerLastY = 0;
			this.mScroller.startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
			this.mStepper.prod();
		}
	}

	public void smartMoveBackwards() {
		View v = (View)this.mChildViews.get(this.mCurrent);
		if (v != null) {
			int screenWidth = this.getWidth();
			int screenHeight = this.getHeight();
			int remainingX = this.mScroller.getFinalX() - this.mScroller.getCurrX();
			int remainingY = this.mScroller.getFinalY() - this.mScroller.getCurrY();
			int left = -(v.getLeft() + this.mXScroll + remainingX);
			int top = -(v.getTop() + this.mYScroll + remainingY);
			int docHeight = v.getMeasuredHeight();
			int xOffset;
			int yOffset;
			if (top <= 0) {
				if (left < screenWidth) {
					View pv = (View)this.mChildViews.get(this.mCurrent - 1);
					if (pv == null) {
						return;
					}

					int prevDocWidth = pv.getMeasuredWidth();
					int prevDocHeight = pv.getMeasuredHeight();
					yOffset = prevDocHeight < screenHeight ? prevDocHeight - screenHeight >> 1 : 0;
					int prevLeft = -(pv.getLeft() + this.mXScroll);
					int prevTop = -(pv.getTop() + this.mYScroll);
					if (prevDocWidth < screenWidth) {
						xOffset = prevDocWidth - screenWidth >> 1;
					} else {
						xOffset = left > 0 ? left % screenWidth : 0;
						if (xOffset + screenWidth > prevDocWidth) {
							xOffset = prevDocWidth - screenWidth;
						}

						while(xOffset + screenWidth * 2 < prevDocWidth) {
							xOffset += screenWidth;
						}
					}

					xOffset -= prevLeft;
					yOffset -= prevTop - prevDocHeight + screenHeight;
				} else {
					xOffset = -screenWidth;
					yOffset = docHeight - screenHeight + top;
				}
			} else {
				xOffset = 0;
				yOffset = -this.smartAdvanceAmount(screenHeight, top);
			}

			this.mScrollerLastX = this.mScrollerLastY = 0;
			this.mScroller.startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
			this.mStepper.prod();
		}
	}

	public void resetupChildren() {
		for(int i = 0; i < this.mChildViews.size(); ++i) {
			this.onChildSetup(this.mChildViews.keyAt(i), (View)this.mChildViews.valueAt(i));
		}

	}

	public void applyToChildren(ViewMapper mapper) {
		for(int i = 0; i < this.mChildViews.size(); ++i) {
			mapper.applyToView((View)this.mChildViews.valueAt(i));
		}

	}

	public void refresh() {
		this.mResetLayout = true;
		this.mScale = 1.0F;
		this.mXScroll = this.mYScroll = 0;
		this.mAdapter.refresh();
		int numChildren = this.mChildViews.size();

		for(int i = 0; i < this.mChildViews.size(); ++i) {
			View v = (View)this.mChildViews.valueAt(i);
			this.onNotInUse(v);
			this.removeViewInLayout(v);
		}

		this.mChildViews.clear();
		this.mViewCache.clear();
		this.requestLayout();
	}

	public View getView(int i) {
		return (View)this.mChildViews.get(i);
	}

	public View getDisplayedView() {
		return (View)this.mChildViews.get(this.mCurrent);
	}

	public void run() {
		if (!this.mScroller.isFinished()) {
			this.mScroller.computeScrollOffset();
			int x = this.mScroller.getCurrX();
			int y = this.mScroller.getCurrY();
			this.mXScroll += x - this.mScrollerLastX;
			this.mYScroll += y - this.mScrollerLastY;
			this.mScrollerLastX = x;
			this.mScrollerLastY = y;
			this.requestLayout();
			this.mStepper.prod();
		} else if (!this.mUserInteracting) {
			View v = (View)this.mChildViews.get(this.mCurrent);
			if (v != null) {
				this.postSettle(v);
			}
		}

	}

	public boolean onDown(MotionEvent arg0) {
		this.mScroller.forceFinished(true);
		return true;
	}

	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		if (this.mScaling) {
			return true;
		} else {
			View v = (View)this.mChildViews.get(this.mCurrent);
			if (v != null) {
				Rect bounds = this.getScrollBounds(v);
				View vr;
				switch(directionOfTravel(velocityX, velocityY)) {
					case 1:
						if (bounds.left >= 0) {
							vr = (View)this.mChildViews.get(this.mCurrent + 1);
							if (vr != null) {
								this.slideViewOntoScreen(vr);
								return true;
							}
						}
						break;
					case 2:
						if (bounds.right <= 0) {
							vr = (View)this.mChildViews.get(this.mCurrent - 1);
							if (vr != null) {
								this.slideViewOntoScreen(vr);
								return true;
							}
						}
					case 3:
					case 4:
				}

				this.mScrollerLastX = this.mScrollerLastY = 0;
				Rect expandedBounds = new Rect(bounds);
				expandedBounds.inset(-100, -100);
				if (withinBoundsInDirectionOfTravel(bounds, velocityX, velocityY) && expandedBounds.contains(0, 0)) {
					this.mScroller.fling(0, 0, (int)velocityX, (int)velocityY, bounds.left, bounds.right, bounds.top, bounds.bottom);
					this.mStepper.prod();
				}
			}

			return true;
		}
	}

	public void onLongPress(MotionEvent e) {
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		com.artifex.mupdf.viewer.PageView pageView = (com.artifex.mupdf.viewer.PageView)this.getDisplayedView();
		if (!this.tapDisabled) {
			this.onDocMotion();
		}

		if (!this.mScaling) {
			this.mXScroll = (int)((float)this.mXScroll - distanceX);
			this.mYScroll = (int)((float)this.mYScroll - distanceY);
			this.requestLayout();
		}

		return true;
	}

	public void onShowPress(MotionEvent e) {
	}

	public boolean onScale(ScaleGestureDetector detector) {
		float previousScale = this.mScale;
		this.mScale = Math.min(Math.max(this.mScale * detector.getScaleFactor(), 1.0F), 64.0F);
		float factor = this.mScale / previousScale;
		View v = (View)this.mChildViews.get(this.mCurrent);
		if (v != null) {
			float currentFocusX = detector.getFocusX();
			float currentFocusY = detector.getFocusY();
			int viewFocusX = (int)currentFocusX - (v.getLeft() + this.mXScroll);
			int viewFocusY = (int)currentFocusY - (v.getTop() + this.mYScroll);
			this.mXScroll = (int)((float)this.mXScroll + ((float)viewFocusX - (float)viewFocusX * factor));
			this.mYScroll = (int)((float)this.mYScroll + ((float)viewFocusY - (float)viewFocusY * factor));
			if (this.mLastScaleFocusX >= 0.0F) {
				this.mXScroll = (int)((float)this.mXScroll + (currentFocusX - this.mLastScaleFocusX));
			}

			if (this.mLastScaleFocusY >= 0.0F) {
				this.mYScroll = (int)((float)this.mYScroll + (currentFocusY - this.mLastScaleFocusY));
			}

			this.mLastScaleFocusX = currentFocusX;
			this.mLastScaleFocusY = currentFocusY;
			this.requestLayout();
		}

		return true;
	}

	public boolean onScaleBegin(ScaleGestureDetector detector) {
		this.tapDisabled = true;
		this.mScaling = true;
		this.mXScroll = this.mYScroll = 0;
		this.mLastScaleFocusX = this.mLastScaleFocusY = -1.0F;
		return true;
	}

	public void onScaleEnd(ScaleGestureDetector detector) {
		this.mScaling = false;
	}

	public boolean onTouchEvent(MotionEvent event) {
		if ((event.getAction() & event.getActionMasked()) == 0) {
			this.tapDisabled = false;
		}

		this.mScaleGestureDetector.onTouchEvent(event);
		this.mGestureDetector.onTouchEvent(event);
		if ((event.getAction() & 255) == 0) {
			this.mUserInteracting = true;
		}

		if ((event.getAction() & 255) == 1) {
			this.mUserInteracting = false;
			View v = (View)this.mChildViews.get(this.mCurrent);
			if (v != null) {
				if (this.mScroller.isFinished()) {
					this.slideViewOntoScreen(v);
				}

				if (this.mScroller.isFinished()) {
					this.postSettle(v);
				}
			}
		}

		this.requestLayout();
		return true;
	}

	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int n = this.getChildCount();

		for(int i = 0; i < n; ++i) {
			this.measureView(this.getChildAt(i));
		}

	}

	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		try {
			this.onLayout2(changed, left, top, right, bottom);
		} catch (OutOfMemoryError var7) {
			System.out.println("Out of memory during layout");
		}

	}

	private void onLayout2(boolean changed, int left, int top, int right, int bottom) {
		if (!this.isInEditMode()) {
			View cv = (View)this.mChildViews.get(this.mCurrent);
			Point cvOffset;
			int cvTop;
			int cvBottom;
			int cvLeft;
			int cvRight;
			if (!this.mResetLayout) {
				if (cv != null) {
					cvOffset = this.subScreenSizeOffset(cv);
					boolean move = cv.getLeft() + cv.getMeasuredWidth() + cvOffset.x + 10 + this.mXScroll < this.getWidth() / 2;
					if (move && this.mCurrent + 1 < this.mAdapter.getCount()) {
						this.postUnsettle(cv);
						this.mStepper.prod();
						this.onMoveOffChild(this.mCurrent);
						++this.mCurrent;
						this.onMoveToChild(this.mCurrent);
					}

					move = cv.getLeft() - cvOffset.x - 10 + this.mXScroll >= this.getWidth() / 2;
					if (move && this.mCurrent > 0) {
						this.postUnsettle(cv);
						this.mStepper.prod();
						this.onMoveOffChild(this.mCurrent);
						--this.mCurrent;
						this.onMoveToChild(this.mCurrent);
					}
				}

				cvLeft = this.mChildViews.size();
				int[] childIndices = new int[cvLeft];

				for(cvTop = 0; cvTop < cvLeft; ++cvTop) {
					childIndices[cvTop] = this.mChildViews.keyAt(cvTop);
				}

				for(cvTop = 0; cvTop < cvLeft; ++cvTop) {
					cvBottom = childIndices[cvTop];
					if (cvBottom < this.mCurrent - 1 || cvBottom > this.mCurrent + 1) {
						View v = (View)this.mChildViews.get(cvBottom);
						this.onNotInUse(v);
						this.mViewCache.add(v);
						this.removeViewInLayout(v);
						this.mChildViews.remove(cvBottom);
					}
				}
			} else {
				this.mResetLayout = false;
				this.mXScroll = this.mYScroll = 0;
				cvLeft = this.mChildViews.size();

				for(cvRight = 0; cvRight < cvLeft; ++cvRight) {
					View v = (View)this.mChildViews.valueAt(cvRight);
					this.onNotInUse(v);
					this.mViewCache.add(v);
					this.removeViewInLayout(v);
				}

				this.mChildViews.clear();
				this.mStepper.prod();
			}

			boolean notPresent = this.mChildViews.get(this.mCurrent) == null;
			cv = this.getOrCreateChild(this.mCurrent);
			cvOffset = this.subScreenSizeOffset(cv);
			if (notPresent) {
				cvLeft = cvOffset.x;
				cvTop = cvOffset.y;
			} else {
				cvLeft = cv.getLeft() + this.mXScroll;
				cvTop = cv.getTop() + this.mYScroll;
			}

			this.mXScroll = this.mYScroll = 0;
			cvRight = cvLeft + cv.getMeasuredWidth();
			cvBottom = cvTop + cv.getMeasuredHeight();
			Point corr;
			if (!this.mUserInteracting && this.mScroller.isFinished()) {
				corr = this.getCorrection(this.getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
				cvRight += corr.x;
				cvLeft += corr.x;
				cvTop += corr.y;
				cvBottom += corr.y;
			} else if (cv.getMeasuredHeight() <= this.getHeight()) {
				corr = this.getCorrection(this.getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
				cvTop += corr.y;
				cvBottom += corr.y;
			}

			cv.layout(cvLeft, cvTop, cvRight, cvBottom);
			Point rightOffset;
			int gap;
			View rv;
			if (this.mCurrent > 0) {
				rv = this.getOrCreateChild(this.mCurrent - 1);
				rightOffset = this.subScreenSizeOffset(rv);
				gap = rightOffset.x + 20 + cvOffset.x;
				rv.layout(cvLeft - rv.getMeasuredWidth() - gap, (cvBottom + cvTop - rv.getMeasuredHeight()) / 2, cvLeft - gap, (cvBottom + cvTop + rv.getMeasuredHeight()) / 2);
			}

			if (this.mCurrent + 1 < this.mAdapter.getCount()) {
				rv = this.getOrCreateChild(this.mCurrent + 1);
				rightOffset = this.subScreenSizeOffset(rv);
				gap = cvOffset.x + 20 + rightOffset.x;
				rv.layout(cvRight + gap, (cvBottom + cvTop - rv.getMeasuredHeight()) / 2, cvRight + rv.getMeasuredWidth() + gap, (cvBottom + cvTop + rv.getMeasuredHeight()) / 2);
			}

			this.invalidate();
		}
	}

	public Adapter getAdapter() {
		return this.mAdapter;
	}

	public View getSelectedView() {
		return null;
	}

	public void setAdapter(Adapter adapter) {
		if (this.mAdapter != null && this.mAdapter != adapter) {
			this.mAdapter.releaseBitmaps();
		}

		this.mAdapter = (PageAdapter)adapter;
		this.requestLayout();
	}

	public void setSelection(int arg0) {
		throw new UnsupportedOperationException(this.getContext().getString(R.string.not_supported));
	}

	private View getCached() {
		return this.mViewCache.size() == 0 ? null : (View)this.mViewCache.removeFirst();
	}

	private View getOrCreateChild(int i) {
		View v = (View)this.mChildViews.get(i);
		if (v == null) {
			v = this.mAdapter.getView(i, this.getCached(), this);
			this.addAndMeasureChild(i, v);
			this.onChildSetup(i, v);
		}

		return v;
	}

	private void addAndMeasureChild(int i, View v) {
		LayoutParams params = v.getLayoutParams();
		if (params == null) {
			params = new LayoutParams(-2, -2);
		}

		this.addViewInLayout(v, 0, params, true);
		this.mChildViews.append(i, v);
		this.measureView(v);
	}

	private void measureView(View v) {
		v.measure(0, 0);
		float scale = Math.min((float)this.getWidth() / (float)v.getMeasuredWidth(), (float)this.getHeight() / (float)v.getMeasuredHeight());
		v.measure(1073741824 | (int)((float)v.getMeasuredWidth() * scale * this.mScale), 1073741824 | (int)((float)v.getMeasuredHeight() * scale * this.mScale));
	}

	private Rect getScrollBounds(int left, int top, int right, int bottom) {
		int xmin = this.getWidth() - right;
		int xmax = -left;
		int ymin = this.getHeight() - bottom;
		int ymax = -top;
		if (xmin > xmax) {
			xmin = xmax = (xmin + xmax) / 2;
		}

		if (ymin > ymax) {
			ymin = ymax = (ymin + ymax) / 2;
		}

		return new Rect(xmin, ymin, xmax, ymax);
	}

	private Rect getScrollBounds(View v) {
		return this.getScrollBounds(v.getLeft() + this.mXScroll, v.getTop() + this.mYScroll, v.getLeft() + v.getMeasuredWidth() + this.mXScroll, v.getTop() + v.getMeasuredHeight() + this.mYScroll);
	}

	private Point getCorrection(Rect bounds) {
		return new Point(Math.min(Math.max(0, bounds.left), bounds.right), Math.min(Math.max(0, bounds.top), bounds.bottom));
	}

	private void postSettle(final View v) {
		this.post(new Runnable() {
			public void run() {
				onSettle(v);
			}
		});
	}

	private void postUnsettle(final View v) {
		this.post(new Runnable() {
			public void run() {
				onUnsettle(v);
			}
		});
	}

	private void slideViewOntoScreen(View v) {
		Point corr = this.getCorrection(this.getScrollBounds(v));
		if (corr.x != 0 || corr.y != 0) {
			this.mScrollerLastX = this.mScrollerLastY = 0;
			this.mScroller.startScroll(0, 0, corr.x, corr.y, 400);
			this.mStepper.prod();
		}

	}

	private Point subScreenSizeOffset(View v) {
		return new Point(Math.max((this.getWidth() - v.getMeasuredWidth()) / 2, 0), Math.max((this.getHeight() - v.getMeasuredHeight()) / 2, 0));
	}

	private static int directionOfTravel(float vx, float vy) {
		if (Math.abs(vx) > 2.0F * Math.abs(vy)) {
			return vx > 0.0F ? 2 : 1;
		} else if (Math.abs(vy) > 2.0F * Math.abs(vx)) {
			return vy > 0.0F ? 4 : 3;
		} else {
			return 0;
		}
	}

	private static boolean withinBoundsInDirectionOfTravel(Rect bounds, float vx, float vy) {
		switch(directionOfTravel(vx, vy)) {
			case 0:
				return bounds.contains(0, 0);
			case 1:
				return bounds.left <= 0;
			case 2:
				return bounds.right >= 0;
			case 3:
				return bounds.top <= 0;
			case 4:
				return bounds.bottom >= 0;
			default:
				throw new NoSuchElementException();
		}
	}

	protected void onTapMainDocArea() {
	}

	protected void onDocMotion() {
	}

	public void setLinksEnabled(boolean b) {
		this.mLinksEnabled = b;
		this.resetupChildren();
		this.invalidate();
	}

	public boolean onSingleTapUp(MotionEvent e) {
		Link link = null;
		if (!this.tapDisabled) {
			com.artifex.mupdf.viewer.PageView pageView = (com.artifex.mupdf.viewer.PageView)this.getDisplayedView();
			if (this.mLinksEnabled && pageView != null) {
				int page = pageView.hitLink(e.getX(), e.getY());
				if (page > 0) {
					this.pushHistory();
					this.setDisplayedViewIndex(page);
				}
			} else if (e.getX() < (float)this.tapPageMargin) {
				this.smartMoveBackwards();
			} else if (e.getX() > (float)(super.getWidth() - this.tapPageMargin)) {
				this.smartMoveForwards();
			} else if (e.getY() < (float)this.tapPageMargin) {
				this.smartMoveBackwards();
			} else if (e.getY() > (float)(super.getHeight() - this.tapPageMargin)) {
				this.smartMoveForwards();
			} else {
				this.onTapMainDocArea();
			}
		}

		return true;
	}

	protected void onChildSetup(int i, View v) {
		if (com.artifex.mupdf.viewer.SearchTaskResult.get() != null && com.artifex.mupdf.viewer.SearchTaskResult.get().pageNumber == i) {
			((com.artifex.mupdf.viewer.PageView)v).setSearchBoxes(com.artifex.mupdf.viewer.SearchTaskResult.get().searchBoxes);
		} else {
			((com.artifex.mupdf.viewer.PageView)v).setSearchBoxes((Quad[])null);
		}

		((com.artifex.mupdf.viewer.PageView)v).setLinkHighlighting(this.mLinksEnabled);
	}

	protected void onMoveToChild(int i) {
		if (com.artifex.mupdf.viewer.SearchTaskResult.get() != null && com.artifex.mupdf.viewer.SearchTaskResult.get().pageNumber != i) {
			com.artifex.mupdf.viewer.SearchTaskResult.set((SearchTaskResult)null);
			this.resetupChildren();
		}

	}

	protected void onMoveOffChild(int i) {
	}

	protected void onSettle(View v) {
		((com.artifex.mupdf.viewer.PageView)v).updateHq(false);
	}

	protected void onUnsettle(View v) {
		((com.artifex.mupdf.viewer.PageView)v).removeHq();
	}

	protected void onNotInUse(View v) {
		((PageView)v).releaseResources();
	}

	abstract static class ViewMapper {
		ViewMapper() {
		}

		abstract void applyToView(View var1);
	}
}
