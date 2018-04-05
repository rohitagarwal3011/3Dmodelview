package com.hrily.artutorial.model3D.view;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.location.Location;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.hrily.artutorial.AugmentedPOI;
import com.hrily.artutorial.MyCurrentAzimuth;
import com.hrily.artutorial.OnAzimuthChangedListener;
import com.hrily.artutorial.OnLocationChangedListener;
import com.hrily.artutorial.R;
import com.hrily.artutorial.model3D.services.ExampleSceneLoader;
import com.hrily.artutorial.model3D.services.SceneLoader;
import com.hrily.artutorial.util.Utils;
import com.hrily.artutorial.util.content.ContentUtils;

/**
 * This activity represents the container for our 3D viewer.
 * 
 * @author andresoviedo
 */
public class ModelActivity extends Activity implements SurfaceHolder.Callback, OnLocationChangedListener, OnAzimuthChangedListener {

	private static final int REQUEST_CODE_OPEN_FILE = 1000;

	private String paramAssetDir;
	private String paramAssetFilename;
	/**
	 * The file to load. Passed as input parameter
	 */
	private String paramFilename;
	/**
	 * Enter into Android Immersive mode so the renderer is full screen or not
	 */
	private boolean immersiveMode = true;
	/**
	 * Background GL clear color. Default is light gray
	 */
	private float[] backgroundColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

	private ModelSurfaceView gLView;

	private SceneLoader scene;

	private Handler handler;

    private Camera mCamera;
    private SurfaceHolder mSurfaceHolder;
    private boolean isCameraViewOn = false;
    private AugmentedPOI mPoi;

    private double mAzimuthReal = 0;
    private double mAzimuthTheoretical = 0;
    private static double AZIMUTH_ACCURACY = 25;
    private double mMyLatitude = 0;
    private double mMyLongitude = 0;

    private MyCurrentAzimuth myCurrentAzimuth;
    private MyCurrentLocation myCurrentLocation;
    Display display;
    FrameLayout mainlayout;

    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;
    private OnLocationChangedListener onLocationChangedListener;



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model);

        mainlayout= (FrameLayout) findViewById(R.id.mainLayout);

        mainlayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //getLocation();
               // mLastLocation = getLocation(getApplicationContext());
                Log.d("New Longitude", String.valueOf(mMyLongitude));
                Log.d("New Latitude", String.valueOf(mMyLatitude));
                if (mLastLocation != null) {

                    Toast.makeText(ModelActivity.this,"new location",Toast.LENGTH_LONG).show();
//                    mPoi = new AugmentedPOI(
//                            "NITK",
//                            "Surathkal",
//                            mLastLocation.getLatitude(),
//                            mLastLocation.getLongitude()
//                    );
                    //onLocationChangedListener.onLocationChanged(mLastLocation);
                }

                else {
                    Toast.makeText(ModelActivity.this,"old location",Toast.LENGTH_LONG).show();
//                    mPoi = new AugmentedPOI(
//                            "NITK",
//                            "Surathkal",
//                            63.0124554,
//                            74.7980362
//                    );
                }

            }
        });


		// Try to get input parameters
		Bundle b = getIntent().getExtras();
		if (b != null) {
			this.paramAssetDir = b.getString("assetDir");
			this.paramAssetFilename = b.getString("assetFilename");
			this.paramFilename = b.getString("uri");
			this.immersiveMode = "true".equalsIgnoreCase(b.getString("immersiveMode"));
			try{
				String[] backgroundColors = b.getString("backgroundColor").split(" ");
				backgroundColor[0] = Float.parseFloat(backgroundColors[0]);
				backgroundColor[1] = Float.parseFloat(backgroundColors[1]);
				backgroundColor[2] = Float.parseFloat(backgroundColors[2]);
				backgroundColor[3] = Float.parseFloat(backgroundColors[3]);
			}catch(Exception ex){
				// Assuming default background color
			}
		}
		Log.i("Renderer", "Params: assetDir '" + paramAssetDir + "', assetFilename '" + paramAssetFilename + "', uri '"
				+ paramFilename + "'");

		handler = new Handler(getMainLooper());

		// Create a GLSurfaceView instance and set it
		// as the ContentView for this Activity.

		gLView = new ModelSurfaceView(this);
        mainlayout.addView(gLView);
        gLView.setZOrderOnTop(true);
        gLView.setVisibility(View.GONE);
        // gLView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
//        gLView.setRenderer(glRenderer);
        gLView.getHolder().setFormat(PixelFormat.TRANSPARENT);

        gLView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
		display = ((android.view.WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

		setupListeners();
		setupLayout();
		setAugmentedRealityPoint();

		//FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(200,200);




        // Create our 3D sceneario
		if (paramFilename == null && paramAssetFilename == null) {
			scene = new ExampleSceneLoader(this);
		} else {
			scene = new SceneLoader(this);
		}
		scene.init();

		// Show the Up button in the action bar.
		setupActionBar();
        setupLayout();

		// TODO: Alert user when there is no multitouch support (2 fingers). He won't be able to rotate or zoom for
		// example
		Utils.printTouchCapabilities(getPackageManager());

		setupOnSystemVisibilityChangeListener();


    }

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		// getActionBar().setDisplayHomeAsUpEnabled(true);
		// }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.model, menu);
		return true;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupOnSystemVisibilityChangeListener() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			return;
		}
		getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
				// Note that system bars will only be "visible" if none of the
				// LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
				if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
					// TODO: The system bars are visible. Make any desired
					// adjustments to your UI, such as showing the action bar or
					// other navigational controls.
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
						hideSystemUIDelayed(3000);
					}
				} else {
					// TODO: The system bars are NOT visible. Make any desired
					// adjustments to your UI, such as hiding the action bar or
					// other navigational controls.
				}
			}
		});
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				if (immersiveMode) hideSystemUIDelayed(5000);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.model_toggle_wireframe:
			scene.toggleWireframe();
			break;
		case R.id.model_toggle_boundingbox:
			scene.toggleBoundingBox();
			break;
		case R.id.model_toggle_textures:
			scene.toggleTextures();
			break;
		case R.id.model_toggle_lights:
			scene.toggleLighting();
			break;
		case R.id.model_load_texture:
			Intent target = Utils.createGetContentIntent();
			Intent intent = Intent.createChooser(target, "Select a file");
			try {
				startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
			} catch (ActivityNotFoundException e) {
				// The reason for the existence of aFileChooser
			}
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void hideSystemUIDelayed(long millis) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			return;
		}
		handler.postDelayed(new Runnable() {
			public void run() {
				hideSystemUI();
			}
		}, millis);
	}

	private void hideSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			hideSystemUIKitKat();
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			hideSystemUIJellyBean();
		}
	}

	// This snippet hides the system bars.
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void hideSystemUIKitKat() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			return;
		}
		// Set the IMMERSIVE flag.
		// Set the content to appear under the system bars so that the content
		// doesn't resize when the system bars hide and show.
		final View decorView = getWindow().getDecorView();
		decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
				| View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
				| View.SYSTEM_UI_FLAG_IMMERSIVE);
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void hideSystemUIJellyBean() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
			return;
		}
		final View decorView = getWindow().getDecorView();
		decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE);
	}

	// This snippet shows the system bars. It does this by removing all the flags
	// except for the ones that make the content appear under the system bars.
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void showSystemUI() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
			return;
		}
		final View decorView = getWindow().getDecorView();
		decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
	}

	public File getParamFile() {
		return getParamFilename() != null ? new File(getParamFilename()) : null;
	}

	public String getParamAssetDir() {
		return paramAssetDir;
	}

	public String getParamAssetFilename() {
		return paramAssetFilename;
	}

	public String getParamFilename() {
		return paramFilename;
	}

	public float[] getBackgroundColor(){
		return backgroundColor;
	}

	public SceneLoader getScene() {
		return scene;
	}

	public ModelSurfaceView getgLView() {
		return gLView;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_CODE_OPEN_FILE:
				if (resultCode == RESULT_OK) {
					// The URI of the selected file
					final Uri uri = data.getData();
					Log.i("Menu", "Loading '" + uri.toString() + "'");
					if (uri != null) {
						final String path = ContentUtils.getPath(getApplicationContext(), uri);
						if (path != null) {
							try {
								scene.loadTexture(null, new URL("file://"+path));
							} catch (MalformedURLException e) {
								Toast.makeText(getApplicationContext(), "Problem loading texture '" + uri.toString() + "'",
										Toast.LENGTH_SHORT).show();
							}
						} else {
							Toast.makeText(getApplicationContext(), "Problem loading texture '" + uri.toString() + "'",
									Toast.LENGTH_SHORT).show();
						}
					}
				} else {
					Toast.makeText(getApplicationContext(), "Result when loading texture was '" + resultCode + "'",
							Toast.LENGTH_SHORT).show();
				}
		}
	}



    private void setAugmentedRealityPoint() {
        mPoi = new AugmentedPOI(
                "NITK",
                "Surathkal",
                28.4620152,
                77.0915596
        );
    }

    public double calculateTheoreticalAzimuth() {
        // Calculates azimuth angle (phi) of POI
        double dy = mPoi.getPoiLatitude() - mMyLatitude;
        double dx = mPoi.getPoiLongitude() - mMyLongitude;

        double phiAngle;
        double tanPhi;

        tanPhi = Math.abs(dx / dy);
        phiAngle = Math.atan(tanPhi);
        phiAngle = Math.toDegrees(phiAngle);

        // phiAngle = [0,90], check quadrant and return correct phiAngle
        if (dy > 0 && dx > 0) { // I quadrant
            return phiAngle;
        } else if (dy < 0 && dx > 0) { // II
            return 180 - phiAngle;
        } else if (dy < 0 && dx < 0) { // III
            return 180 + phiAngle;
        } else if (dy > 0 && dx < 0) { // IV
            return 360 - phiAngle;
        }

        return phiAngle;
    }

    private List<Double> calculateAzimuthAccuracy(double azimuth) {
        // Returns the Camera View Sector
        List<Double> minMax = new ArrayList<Double>();
        double minAngle = (azimuth - AZIMUTH_ACCURACY + 360) % 360;
        double maxAngle = (azimuth + AZIMUTH_ACCURACY) % 360;
        minMax.clear();
        minMax.add(minAngle);
        minMax.add(maxAngle);
        return minMax;
    }

    private boolean isBetween(double minAngle, double maxAngle, double azimuth) {
        // Checks if the azimuth angle lies in minAngle and maxAngle of Camera View Sector
        if (minAngle > maxAngle) {
            if (isBetween(0, maxAngle, azimuth) || isBetween(minAngle, 360, azimuth))
                return true;
        } else if (azimuth > minAngle && azimuth < maxAngle)
            return true;
        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        // Function to handle Change in Location
        mMyLatitude = location.getLatitude();
        mMyLongitude = location.getLongitude();
        mAzimuthTheoretical = calculateTheoreticalAzimuth();
    }

    @Override
    public Location getLocation(Context context) {

	    Log.d("New Longitude", String.valueOf(myCurrentLocation.getLocation(context).getLongitude()));
        return null;
    }

    @Override
    public void onAzimuthChanged(float azimuthChangedFrom, float azimuthChangedTo) {
        // Function to handle Change in azimuth angle
        mAzimuthReal = azimuthChangedTo;
        mAzimuthTheoretical = calculateTheoreticalAzimuth();

        // Since Camera View is perpendicular to device plane
        mAzimuthReal = (mAzimuthReal+90)%360;

        double minAngle = calculateAzimuthAccuracy(mAzimuthReal).get(0);
        double maxAngle = calculateAzimuthAccuracy(mAzimuthReal).get(1);

        if (isBetween(minAngle, maxAngle, mAzimuthTheoretical)) {
            float ratio = ((float) (mAzimuthTheoretical - minAngle + 360.0) % 360) / ((float) (maxAngle - minAngle + 360.0) % 360);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.CENTER);
//            lp.topMargin = (int) (getScreenHeight() * ratio);
//            lp.leftMargin = ((int)(getScreenWidth()* ratio));
//            lp.topMargin = (int) (getScreenHeight() * ratio);
//            lp.leftMargin = getScreenWidth()/2 - mainlayout.getWidth()/2;
            gLView.setLayoutParams(lp);
//            if(mainlayout!=null) {
//                Toast.makeText(this,"show",Toast.LENGTH_SHORT).show();
//                if(gLView.getParent()!=null)
//                    ((ViewGroup)gLView.getParent()).removeView(gLView);
//                mainlayout.addView(gLView);
//            }
            //gLView=(ModelSurfaceView)findViewById(R.id.glsurfaceview);

            gLView.setVisibility(View.VISIBLE);


        } else {
            gLView.setVisibility(View.GONE);
//            if(mainlayout!=null) {
//                if(gLView.getParent()!=null)
//                    ((ViewGroup)gLView.getParent()).removeView(gLView);
//            }
        }

    }

    private int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        return width;
    }

    private int getScreenHeight() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        return height;
    }
    @Override
    protected void onStop() {
        myCurrentAzimuth.stop();
        myCurrentLocation.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        myCurrentAzimuth.start();
        myCurrentLocation.start();
    }

    private void setupListeners() {
        myCurrentLocation = new MyCurrentLocation(this);
        myCurrentLocation.buildGoogleApiClient(this);
        myCurrentLocation.start();

        myCurrentAzimuth = new MyCurrentAzimuth(this, this);
        myCurrentAzimuth.start();
    }

    private void setupLayout() {

        getWindow().setFormat(PixelFormat.UNKNOWN);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.cameraview);
        mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        if (isCameraViewOn) {
            mCamera.stopPreview();
            isCameraViewOn = false;
        }

        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
                isCameraViewOn = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open();
        mCamera.setDisplayOrientation(90);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        isCameraViewOn = false;
    }

}
