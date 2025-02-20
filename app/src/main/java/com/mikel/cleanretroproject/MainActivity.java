// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.mikel.cleanretroproject;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.mikel.cleanretroproject.Yolov8Ncnn;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import android.support.v4.app.ActivityCompat;
//import android.support.v4.content.ContextCompat;

public class MainActivity extends Activity
{
	private static final String TAG = "AndroidCameraApi";
	public static final int REQUEST_CAMERA = 100;

	//private Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();
	private int facing = 0;

	private Spinner spinnerModel;
	private Spinner spinnerCPUGPU;
	private int current_model = 0;
	private int current_cpugpu = 0;

	private TextureView textureView;
	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	private String cameraId;
	protected CameraDevice cameraDevice;
	protected CameraCaptureSession cameraCaptureSessions;
	protected CaptureRequest captureRequest;
	protected CaptureRequest.Builder captureRequestBuilder;
	private Size imageDimension;
	private ImageReader imageReader;
	private File file;
	private static final int REQUEST_CAMERA_PERMISSION = 200;
	private boolean mFlashSupported;
	private Handler mBackgroundHandler;
	private HandlerThread mBackgroundThread;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		textureView = (TextureView) findViewById(R.id.texture);
		assert textureView != null;
		textureView.setSurfaceTextureListener(textureListener);
		/*
		takePictureButton = (Button) findViewById(R.id.btn_takepicture);
		assert takePictureButton != null;
		takePictureButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				takePicture();
			}
		});
		 */
/*
		Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);
		buttonSwitchCamera.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {

				int new_facing = 1 - facing;

				yolov8ncnn.closeCamera();

				yolov8ncnn.openCamera(new_facing);

				facing = new_facing;
			}
		});
*/
		spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
		spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
			{
				if (position != current_model)
				{
					current_model = position;
					//reload();
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0)
			{
			}
		});

		spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
		spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
			{
				if (position != current_cpugpu)
				{
					current_cpugpu = position;
					//reload();
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0)
			{
			}
		});
/*
		reload();
		*/
	}

	TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			//open your camera here
			openCamera();
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
			// Transform you image captured size according to the surface width and height
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			return false;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		}
	};
	private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(CameraDevice camera) {
			//This is called when the camera is open
			Log.e(TAG, "onOpened");
			cameraDevice = camera;
			createCameraPreview();
		}

		@Override
		public void onDisconnected(CameraDevice camera) {
			cameraDevice.close();
		}

		@Override
		public void onError(CameraDevice camera, int error) {
			cameraDevice.close();
			cameraDevice = null;
		}
	};
	final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
		@Override
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			super.onCaptureCompleted(session, request, result);
			Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
			createCameraPreview();
		}
	};

	protected void startBackgroundThread() {
		mBackgroundThread = new HandlerThread("Camera Background");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
	}

	protected void stopBackgroundThread() {
		mBackgroundThread.quitSafely();
		try {
			mBackgroundThread.join();
			mBackgroundThread = null;
			mBackgroundHandler = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected void getImage() {
		try {
			// preview
			SurfaceTexture texture = textureView.getSurfaceTexture();
			assert texture != null;
			texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
			Surface surface = new Surface(texture);

			// data
			int width = 640;
			int height = 480;
			ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

			captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			captureRequestBuilder.addTarget(surface);
			captureRequestBuilder.addTarget(reader.getSurface());
			captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
			// Orientation
			int rotation = getWindowManager().getDefaultDisplay().getRotation();
			captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

			List<Surface> outputSurfaces = new ArrayList<Surface>(2);
			outputSurfaces.add(reader.getSurface());
			outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
			cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
				@Override
				public void onConfigured(CameraCaptureSession cameraCaptureSession) {
					//The camera is already closed
					if (null == cameraDevice) {
						return;
					}
					// When the session is ready, we start displaying the preview.
					cameraCaptureSessions = cameraCaptureSession;
					updatePreview();
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
					Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
				}
			}, null);

			ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
				@Override
				public void onImageAvailable(ImageReader reader) {
					Image image = null;
					try {
						image = reader.acquireLatestImage();
						ByteBuffer buffer = image.getPlanes()[0].getBuffer();
						byte[] bytes = new byte[buffer.capacity()];
						buffer.get(bytes);
						//save(bytes);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (image != null) {
							image.close();
						}
					}
				}
			};
			reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}
	protected void takePicture() {
		if (null == cameraDevice) {
			Log.e(TAG, "cameraDevice is null");
			return;
		}
		CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		try {
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
			Size[] jpegSizes = null;
			if (characteristics != null) {
				jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
			}
			int width = 640;
			int height = 480;
			if (jpegSizes != null && 0 < jpegSizes.length) {
				width = jpegSizes[0].getWidth();
				height = jpegSizes[0].getHeight();
			}
			ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
			List<Surface> outputSurfaces = new ArrayList<Surface>(2);
			outputSurfaces.add(reader.getSurface());
			outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
			final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(reader.getSurface());
			captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
			// Orientation
			int rotation = getWindowManager().getDefaultDisplay().getRotation();
			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
			final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");
			ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
				@Override
				public void onImageAvailable(ImageReader reader) {
					Image image = null;
					try {
						image = reader.acquireLatestImage();
						ByteBuffer buffer = image.getPlanes()[0].getBuffer();
						byte[] bytes = new byte[buffer.capacity()];
						buffer.get(bytes);
						save(bytes);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (image != null) {
							image.close();
						}
					}
				}

				private void save(byte[] bytes) throws IOException {
					OutputStream output = null;
					try {
						output = new FileOutputStream(file);
						output.write(bytes);
					} finally {
						if (null != output) {
							output.close();
						}
					}
				}
			};
			reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
			final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
				@Override
				public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
					super.onCaptureCompleted(session, request, result);
					Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
					createCameraPreview();
				}
			};
			cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
				@Override
				public void onConfigured(CameraCaptureSession session) {
					try {
						session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
					} catch (CameraAccessException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession session) {
				}
			}, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	protected void createCameraPreview() {
		try {
			SurfaceTexture texture = textureView.getSurfaceTexture();
			assert texture != null;
			texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
			Surface surface = new Surface(texture);
			captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			captureRequestBuilder.addTarget(surface);
			cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
				@Override
				public void onConfigured(CameraCaptureSession cameraCaptureSession) {
					//The camera is already closed
					if (null == cameraDevice) {
						return;
					}
					// When the session is ready, we start displaying the preview.
					cameraCaptureSessions = cameraCaptureSession;
					updatePreview();
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
					Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
				}
			}, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void openCamera() {
		CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		Log.e(TAG, "is camera open");
		try {
			cameraId = manager.getCameraIdList()[1];
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
			StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
			assert map != null;
			imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
			// Add permission for camera and let user grant the permission
			//if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			//	ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
			//	return;
			//}
			manager.openCamera(cameraId, stateCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		Log.e(TAG, "openCamera X");
	}

	protected void updatePreview() {
		if (null == cameraDevice) {
			Log.e(TAG, "updatePreview error, return");
		}
		captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
		try {
			cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void closeCamera() {
		if (null != cameraDevice) {
			cameraDevice.close();
			cameraDevice = null;
		}
		if (null != imageReader) {
			imageReader.close();
			imageReader = null;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_CAMERA_PERMISSION) {
			if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
				// close the app
				Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
				finish();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.e(TAG, "onResume");
		startBackgroundThread();
		if (textureView.isAvailable()) {
			openCamera();
		} else {
			textureView.setSurfaceTextureListener(textureListener);
		}
	}

	@Override
	protected void onPause() {
		Log.e(TAG, "onPause");
		//closeCamera();
		stopBackgroundThread();
		super.onPause();
	}
}