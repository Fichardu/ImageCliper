package com.xf.imagecliper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.example.imagecliper.R;

public class PhotoEditActivity extends Activity implements OnClickListener {
	private static final int TAKE_PIC_CODE = 1;
	private static final int GET_PIC_CODE = 2;

	private ImageView mImageView = null;
	private Bitmap mBitmap = null;
	private String mPhotoPath;

	private int mBitmapWidth;
	private int mBitmapHeight;
	private int mTopOffset = 0;
	private int mLeftOffset = 0;
	private int mClipWidth;
	private int mClipHeight;

	private final int mScaleImageWidth = 192;
	private final int mScaleImageHeight = 132;

	private float RATIO = ((float) mScaleImageWidth) / mScaleImageHeight;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_photo_edit);
		Button submitBtn = (Button) findViewById(R.id.photo_edit_submit_btn);
		Button chooseBtn = (Button) findViewById(R.id.photo_edit_choose_btn);
		submitBtn.setOnClickListener(this);
		chooseBtn.setOnClickListener(this);

		mImageView = (ImageView) findViewById(R.id.photo_edit_Image);
		mImageView.setOnTouchListener(new ImageTouchListener());

		mClipWidth = getWindowManager().getDefaultDisplay().getWidth();
		mClipHeight = (int) (mClipWidth / RATIO);

		View squareView = findViewById(R.id.photo_edit_square);
		squareView.setLayoutParams(new LinearLayout.LayoutParams(mClipWidth,
				mClipHeight));
	}

	@Override
	protected void onDestroy() {
		if (mBitmap != null) {
			mBitmap.recycle();
			mBitmap = null;
		}
		super.onDestroy();
	}

	private class ImageTouchListener implements OnTouchListener {

		private Matrix matrix = new Matrix();
		private Matrix savedMatrix = new Matrix();
		private PointF startPoint = new PointF();
		private PointF midPoint = new PointF();

		private static final int STATE_NONE = 0;
		private static final int STATE_DRAG = 1;
		private static final int STATE_ZOOM = 2;

		private int currentMode;
		private float startDistance;
		private float[] mMatrixValue = new float[9];
		private float[] mTranslateLimit = new float[2];

		public ImageTouchListener() {
			currentMode = STATE_NONE;
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				matrix.set(mImageView.getImageMatrix());
				savedMatrix.set(matrix);
				matrix.getValues(mMatrixValue);

				float scaleFactor = mMatrixValue[0];
				mTranslateLimit[0] = mClipWidth - mBitmapWidth * scaleFactor;
				if (mTranslateLimit[0] > 0) {
					mTranslateLimit[0] = 0;
				}
				mTranslateLimit[1] = mClipHeight + mTopOffset - mBitmapHeight
						* scaleFactor;
				startPoint.set(event.getX(), event.getY());
				currentMode = STATE_DRAG;
				break;
			case MotionEvent.ACTION_POINTER_DOWN:
				startDistance = spacing(event);
				if (startDistance > 10f) {
					savedMatrix.set(matrix);
					getMidPoint(midPoint, event);
					currentMode = STATE_ZOOM;
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (currentMode == STATE_DRAG) {
					matrix.set(savedMatrix);
					matrix.postTranslate(event.getX() - startPoint.x,
							event.getY() - startPoint.y);
					matrix.getValues(mMatrixValue);
					float fx = mMatrixValue[2];
					float fy = mMatrixValue[5];
					boolean overroll = false;
					if (fx > 0) {
						fx = 0;
						overroll = true;
					} else if (fx < mTranslateLimit[0]) {
						fx = mTranslateLimit[0];
						overroll = true;
					}

					if (fy > mTopOffset) {
						fy = mTopOffset;
						overroll = true;
					} else if (fy < mTranslateLimit[1]) {
						fy = mTranslateLimit[1];
						overroll = true;
					}
					if (overroll) {
						matrix.setScale(mMatrixValue[0], mMatrixValue[0]);
						matrix.postTranslate(fx, fy);
					}

				} else if (currentMode == STATE_ZOOM) {
					float newDist = spacing(event);
					if (newDist > 10f) {
						matrix.set(savedMatrix);
						float scale = newDist / startDistance;
						matrix.postScale(scale, scale, midPoint.x, midPoint.y);
					}
				}
				break;
			case MotionEvent.ACTION_POINTER_UP:
				Matrix testMatrix = mImageView.getImageMatrix();
				float[] values = new float[9];
				testMatrix.getValues(values);
				float scale = values[0];

				if (scale < 1.0) {
					matrix.setScale(1.0f, 1.0f);
					matrix.postTranslate(0, mTopOffset);
				} else {
					float fx = values[2];
					float fy = values[5];
					boolean overroll = false;

					mTranslateLimit[0] = mClipWidth - mBitmapWidth * scale;
					if (mTranslateLimit[0] > 0) {
						mTranslateLimit[0] = 0;
					}
					mTranslateLimit[1] = mClipHeight + mTopOffset
							- mBitmapHeight * scale;

					if (fx > 0) {
						fx = 0;
						overroll = true;
					} else if (fx < mTranslateLimit[0]) {
						fx = mTranslateLimit[0];
						overroll = true;
					}

					if (fy > mTopOffset) {
						fy = mTopOffset;
						overroll = true;
					} else if (fy < mTranslateLimit[1]) {
						fy = mTranslateLimit[1];
						overroll = true;
					}

					if (overroll) {
						matrix.setScale(values[0], values[0]);
						matrix.postTranslate(fx, fy);
					}
				}
				currentMode = STATE_NONE;
				break;
			case MotionEvent.ACTION_UP:
				currentMode = STATE_NONE;
				break;
			default:
				break;
			}
			mImageView.setImageMatrix(matrix);
			return true;
		}

		private float spacing(MotionEvent event) {
			float x = event.getX(0) - event.getX(1);
			float y = event.getY(0) - event.getY(1);
			return FloatMath.sqrt(x * x + y * y);
		}

		private void getMidPoint(PointF point, MotionEvent event) {
			float x = event.getX(0) + event.getX(1);
			float y = event.getY(0) + event.getY(1);
			point.set(x / 2, y / 2);
		}

	}

	@Override
	public void onClick(View view) {
		int id = view.getId();
		switch (id) {
		case R.id.photo_edit_choose_btn:
			onChooseAction();
			break;
		case R.id.photo_edit_submit_btn:
			onSubmitAction();
			break;
		}
	}

	private void onChooseAction() {
		final Dialog photoDialog = new Dialog(this,
				android.R.style.Theme_Dialog);
		photoDialog.setContentView(R.layout.photo_take_dialog);
		Button cameraBtn = (Button) photoDialog
				.findViewById(R.id.diy_photo_take_camera);
		Button albumBtn = (Button) photoDialog
				.findViewById(R.id.diy_photo_take_album);
		Button cancelBtn = (Button) photoDialog
				.findViewById(R.id.diy_photo_take_cancelBtn);
		cameraBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				photoDialog.dismiss();
				dispatchTakePictureIntent(TAKE_PIC_CODE);
			}
		});

		albumBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				photoDialog.dismiss();
				dispatchGetPictureIntent(GET_PIC_CODE);
			}
		});

		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				photoDialog.dismiss();
			}
		});
		photoDialog.show();
	}

	private void onSubmitAction() {
		new SaveBitmapAsyncTask().execute();
	}

	private void dispatchTakePictureIntent(int actionCode) {
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		try {
			File picFile = new File(getCameraFilePath(), "temp.jpg");
			picFile.createNewFile();
			mPhotoPath = picFile.getPath();
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(picFile));
		} catch (IOException e) {
			e.printStackTrace();
		}
		startActivityForResult(intent, actionCode);
	}

	private void dispatchGetPictureIntent(int actionCode) {
		mPhotoPath = null;
		Intent intent = new Intent(Intent.ACTION_PICK, null);
		intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				"image/*");
		startActivityForResult(intent, actionCode);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			if (requestCode == TAKE_PIC_CODE || requestCode == GET_PIC_CODE) {
				if (mPhotoPath == null) {
					Uri photoUri = data.getData();
					ContentResolver cr = this.getContentResolver();
					Cursor cursor = cr.query(photoUri, null, null, null, null);
					if (cursor.moveToFirst()) {
						mPhotoPath = cursor.getString(cursor
								.getColumnIndex("_data"));
					}
					cursor.close();
				}
				new LoadBitmapAsyncTask(mClipWidth, mClipHeight)
						.execute(mPhotoPath);
			}
		}
	}

	private String getCameraFilePath() {
		File rootPath = Environment.getExternalStorageDirectory();
		File filePath = new File(rootPath, "/imageClip");
		if (!filePath.exists()) {
			filePath.mkdir();
		}
		return filePath.getAbsolutePath();
	}
	
	private class LoadBitmapAsyncTask extends AsyncTask<String, Void, Bitmap> {
		private ProgressDialog cmProgressDialog = null;
		private float cmSideWidthLimit;
		private float cmSideHeightLimit;

		public LoadBitmapAsyncTask(int viewWidth, int viewHeight) {
			cmSideWidthLimit = viewWidth;
			cmSideHeightLimit = viewHeight;
		}

		@Override
		protected void onPreExecute() {
			cmProgressDialog = new ProgressDialog(PhotoEditActivity.this);
			cmProgressDialog.setMessage("Loading");
			cmProgressDialog.show();
		}

		@Override
		protected Bitmap doInBackground(String... params) {
			String filePath = params[0];
			Options opt = new Options();
			opt.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(filePath, opt);

			int sampleFctor = 1;
			if (opt.outWidth > 480 || opt.outHeight > 800) {
				int sampleW = opt.outWidth / 480;
				int sampleH = opt.outHeight / 800;
				sampleFctor = sampleW < sampleH ? sampleW : sampleH;
			}
			Bitmap bitmap = decodeBitmap(filePath, sampleFctor);
			if (bitmap == null) {
				return null;
			}

			int bmpWidth = bitmap.getWidth();
			int bmpHeight = bitmap.getHeight();
			int rotateDegree = readPicDegree(filePath);
			Matrix matrix = new Matrix();
			if (rotateDegree == 90) {
				matrix.postRotate(90);
				bmpWidth = bitmap.getHeight();
				bmpHeight = bitmap.getWidth();
			}
			float scale = 1.0f;
			if (bmpWidth < bmpHeight) {
				scale = cmSideWidthLimit / bmpWidth;
			} else {
				scale = cmSideHeightLimit / bmpHeight;
			}
			matrix.postScale(scale, scale);

			Bitmap processedBitmap = null;
			try {
				processedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
						bitmap.getWidth(), bitmap.getHeight(), matrix, true);
				if (processedBitmap != bitmap) {
					bitmap.recycle();
					bitmap = null;
				}
			} catch (OutOfMemoryError e) {

			}
			return processedBitmap;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			mBitmap = result;
			mBitmapWidth = result.getWidth();
			mBitmapHeight = result.getHeight();
			mImageView.setImageBitmap(result);
			View squareView = findViewById(R.id.photo_edit_square);
			mTopOffset = squareView.getTop();
			if (mBitmapWidth >= mBitmapHeight) {
				Matrix matrix = mImageView.getImageMatrix();
				matrix.postTranslate(0, mTopOffset);
				mImageView.setImageMatrix(matrix);
			}
			cmProgressDialog.dismiss();
		}

		private int readPicDegree(String filePath) {
			int degree = 0;
			try {
				ExifInterface exifInterface = new ExifInterface(filePath);
				int orientation = exifInterface.getAttributeInt(
						ExifInterface.TAG_ORIENTATION,
						ExifInterface.ORIENTATION_NORMAL);
				switch (orientation) {
				case ExifInterface.ORIENTATION_ROTATE_90:
					degree = 90;
					break;
				case ExifInterface.ORIENTATION_ROTATE_180:
					degree = 180;
					break;
				case ExifInterface.ORIENTATION_ROTATE_270:
					degree = 270;
					break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return degree;
		}

		private Bitmap decodeBitmap(String filePath, int sampleSize) {
			if (sampleSize < 1) {
				sampleSize = 1;
			}
			Bitmap bitmap = null;
			while (bitmap == null) {
				try {
					Options opts = new Options();
					opts.inSampleSize = sampleSize;
					bitmap = BitmapFactory.decodeFile(filePath, opts);
				} catch (OutOfMemoryError oom) {
					++sampleSize;
				} catch (Exception e) {
					break;
				}
			}
			return bitmap;
		}
	}

	private class SaveBitmapAsyncTask extends AsyncTask<Void, Void, Void> {

		private ProgressDialog cmProgressDialog = null;

		@Override
		protected void onPreExecute() {
			cmProgressDialog = new ProgressDialog(PhotoEditActivity.this);
			cmProgressDialog.setMessage("Saving...");
			cmProgressDialog.show();
		}

		@Override
		protected Void doInBackground(Void... params) {
			Matrix matrix = mImageView.getImageMatrix();
			float[] values = new float[9];
			matrix.getValues(values);
			float scaleX = values[0];
			float transX = values[2];
			float scaleY = values[4];
			float transY = values[5];

			int x = (int) ((mLeftOffset - transX) / scaleX);
			int y = (int) ((mTopOffset - transY) / scaleY);
			int width = (int) (mClipWidth / scaleX);
			int height = (int) (mClipHeight / scaleY);
			if (x + width > mBitmapWidth) {
				width = mBitmapWidth - x;
			}
			Bitmap cropBitmap = Bitmap.createBitmap(mBitmap, x, y, width,
					height);
			Bitmap scaleBitmap = Bitmap.createScaledBitmap(cropBitmap,
					mScaleImageWidth, mScaleImageHeight, true);
			try {
				File outFile = new File(getCameraFilePath(),
						System.currentTimeMillis() + ".jpg");
				outFile.createNewFile();
				FileOutputStream outStream = new FileOutputStream(outFile);
				scaleBitmap.compress(CompressFormat.JPEG, 100, outStream);
				outStream.close();
			} catch (IOException e) {
			}
			cropBitmap.recycle();
			scaleBitmap.recycle();
			cropBitmap = null;
			scaleBitmap = null;
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			cmProgressDialog.dismiss();
			PhotoEditActivity.this.setResult(RESULT_OK);
			PhotoEditActivity.this.finish();
		}

	}

}
