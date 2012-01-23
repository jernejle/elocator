package feri.rvir.elocator.android;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.restlet.data.ChallengeScheme;
import org.restlet.resource.ClientResource;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

import feri.rvir.elocator.android.maps.MapControls;
import feri.rvir.elocator.android.maps.TrackingItemizedOverlay;
import feri.rvir.elocator.android.util.AsyncTaskResult;
import feri.rvir.elocator.android.util.Serializer;
import feri.rvir.elocator.android.util.ToastCentered;
import feri.rvir.elocator.rest.resource.location.LocationResource;
import feri.rvir.elocator.rest.resource.user.User;
import feri.rvir.elocator.rest.resource.user.UsersResource;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;

public class TrackingOverviewActivity extends MapActivity {
	
	private TrackingOverviewActivity thisActivity;
	
	private MapView mapView;
	private List<Overlay> mapOverlays;
	private TrackingItemizedOverlay itemizedOverlayHome;
	private TrackingItemizedOverlay itemizedOverlayTracking;
	private Drawable drawableHome;
	private Drawable drawableUser;
	
	private ArrayList<User> users=new ArrayList<User>();
	private ArrayList<feri.rvir.elocator.rest.resource.location.Location> locations=new ArrayList<feri.rvir.elocator.rest.resource.location.Location>();
	
	private User user;
	private boolean gotFirstLocation=false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trackingoverview);
		thisActivity=this;

		Serializer<User> serializer=new Serializer<User>();
		try {
			user=serializer.unserialize(openFileInput(getString(R.string.user_data_store)));
		} catch (FileNotFoundException e) {
			user=null;
			e.printStackTrace();
		}
		
		mapView=(MapView)findViewById(R.id.trackingoverview_mapview);
		mapView.setBuiltInZoomControls(true);
		
		mapOverlays = mapView.getOverlays();
		drawableHome=getResources().getDrawable(R.drawable.ic_marker_home);
		drawableUser=getResources().getDrawable(R.drawable.ic_marker_user);
		
		LocationManager locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
		LocationListener locationListener=new LocationListener() {
			
			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}
			
			@Override
			public void onProviderEnabled(String provider) {
			}
			
			@Override
			public void onProviderDisabled(String provider) {
			}
			
			@Override
			public void onLocationChanged(Location location) {
				System.out.println("LOCATION CHANGED");
				GeoPoint point=new GeoPoint((int)(location.getLatitude()*1e6), (int)(location.getLongitude()*1e6));
				OverlayItem overlayItem=new OverlayItem(point, user.getUsername(), Calendar.getInstance().getTime().toGMTString());
				
				if(!gotFirstLocation) {
					System.out.println("GOT LOCATION FIRST TIME");
					itemizedOverlayHome=new TrackingItemizedOverlay(drawableHome, thisActivity);
					itemizedOverlayHome.addOverlay(overlayItem);
					mapOverlays.add(itemizedOverlayHome);
					gotFirstLocation=true;
					mapView.invalidate();
					adjustZoom();
				} else {
					int pos=mapOverlays.indexOf(itemizedOverlayHome);
					itemizedOverlayHome=new TrackingItemizedOverlay(drawableHome, thisActivity);
					itemizedOverlayHome.addOverlay(overlayItem);
					mapOverlays.set(pos, itemizedOverlayHome);
					mapView.invalidate();
					adjustZoom();
				}
			}
		};
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		
		new GetLocationsTask().execute(user.getUsername(), user.getPassword());
		
	}
	
	private void populateOverlay() {
		itemizedOverlayTracking=new TrackingItemizedOverlay(drawableUser, thisActivity);
		for(int i=0;i<users.size();i++) {
			User u=users.get(i);
			feri.rvir.elocator.rest.resource.location.Location l=locations.get(i);
			if(l!=null) {
				OverlayItem overlayItem=new OverlayItem(new GeoPoint((int)(l.getLatitude()*1E6), (int)(l.getLongitude()*1E6)), u.getUsername(), l.getTimestamp().toGMTString());
				itemizedOverlayTracking.addOverlay(overlayItem);
				mapOverlays.add(itemizedOverlayTracking);
			}
			adjustZoom();
		}
	}
	
	private void adjustZoom() {
		ArrayList<OverlayItem> overlayItems=new ArrayList<OverlayItem>();
		if(itemizedOverlayTracking!=null) {
			overlayItems.addAll(itemizedOverlayTracking.getOverlayItems());
		}
		if(itemizedOverlayHome!=null) {
			overlayItems.addAll(itemizedOverlayHome.getOverlayItems());
		}
		MapControls.adjustZoom(overlayItems, mapView);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		adjustZoom();
	}
	
	private class GetLocationsTask extends AsyncTask<String, Void, Integer> {
		
		private String username;
		private String password;
		
		private ArrayList<User> users=new ArrayList<User>();;
		private ArrayList<feri.rvir.elocator.rest.resource.location.Location> locations=new ArrayList<feri.rvir.elocator.rest.resource.location.Location>();
		
		@Override
		protected  Integer doInBackground(String... params) {
			username=params[0];
			password=params[1];
			ClientResource cr=new ClientResource(getString(R.string.gae_server_address)+"/rest/users");
	        cr.setRequestEntityBuffering(true);
	        cr.setChallengeResponse(ChallengeScheme.HTTP_BASIC, username, password);
	        System.out.println(password);
			try {
				UsersResource ur=cr.wrap(UsersResource.class);
				users=ur.accept(username);
				for(User u:users) {
					System.out.println("GETTING FOR "+u.getUsername());
					cr=new ClientResource(getString(R.string.gae_server_address)+"/rest/users/"+u.getUsername()+"/location");
					cr.setRequestEntityBuffering(true);
				    cr.setChallengeResponse(ChallengeScheme.HTTP_BASIC, username, password);
					LocationResource lr=cr.wrap(LocationResource.class);
					locations.add(lr.retrieve());
				}
	        	return AsyncTaskResult.SUCCESSFUL;
			} catch (RuntimeException e) {
				if(cr.getStatus().equals(org.restlet.data.Status.CLIENT_ERROR_UNAUTHORIZED)) {
					return AsyncTaskResult.UNAUTHORIZED;
				} else if(!cr.getStatus().isSuccess()) {
					return AsyncTaskResult.CONNECTION_FAILED;
				}
			}
			return AsyncTaskResult.CONNECTION_FAILED;
		}
		
		@Override
		protected void onPostExecute(Integer result) {
			
			switch (result) {
			case AsyncTaskResult.UNAUTHORIZED:
				ToastCentered.makeText(thisActivity, "Unauthorized.").show();
				break;
			case AsyncTaskResult.SUCCESSFUL:
				thisActivity.users=users;
				thisActivity.locations=locations;
				populateOverlay();
				break;
			default:
				ToastCentered.makeText(thisActivity, "Connection to server failed.").show();
				break;
			}
		}
	}

}
