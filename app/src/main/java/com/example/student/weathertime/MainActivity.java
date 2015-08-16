package com.example.student.weathertime;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity implements OnMapReadyCallback {
    private String cityName;
    private String condition;
    private String temperature;
    private String dewPoint;
    private String visibility;
    private String relativeHumidity;
    private String wind;
    private String windDirection;
    private String latitude;
    private String longtitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //start an async task to load the search box
        new PopulateCitySearch().execute();

        //start the map
        MapFragment mMapFragment = MapFragment.newInstance();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.add(R.id.map, mMapFragment);
        transaction.commit();
        mMapFragment.getMapAsync(this);

        //when select a city from the search results, display weather information for the city
        AutoCompleteTextView actv = (AutoCompleteTextView)findViewById(R.id.citySearch);
        actv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selected = ((TextView) view).getText().toString();
                cityName = selected;
                new GetWeatherTask().execute(selected);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.help) {
            startActivity(new Intent(this, HelpActivity.class));
            return true;
        }
        if (id == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /////////////////////////////////////////
    //POPULATE THE SEARCH BOX WITH CITY NAMES
    public ArrayList<String> getCitiesList(){
        InputStream in = null;
        ArrayList<String> cities = new ArrayList<>();

        //connect to the city list on weatheroffice.ec.gc.ca
        try {
            in = OpenHttpConnection("http://dd.weatheroffice.ec.gc.ca/citypage_weather/xml/siteList.xml");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //populate the arraylist with city names
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(in,null);
            String currentTag = null;
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = xpp.getName();
                }
                //https://androidcookbook.com/Recipe.seam?recipeId=2217
                else if (eventType == XmlPullParser.TEXT && currentTag != null && currentTag.equals("nameEn")) {
                    cities.add(xpp.getText());
                }
                eventType = xpp.next();
            }

            in.close();

        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return cities;
    }

    /////////////////////////////////////////////////////
    //GET WEB ADDRESS OF THE FILE CORRESPONDING TO A CITY
    public String getWeatherFile(String city){
        InputStream in = null;
        String filename = null;
        String provinceCode = null;
        //connect to the city list on weatheroffice.ec.gc.ca
        try {
            in = OpenHttpConnection("http://dd.weatheroffice.ec.gc.ca/citypage_weather/xml/siteList.xml");
        } catch (IOException e) {
            e.printStackTrace();
        }
        //populate the arraylist with city names
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(in, null);
            String currentTag = null;
            String currentName = null;
            boolean found = false;
            int eventType = xpp.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = xpp.getName();
                    if(currentTag.equals("site")){
                        currentName = xpp.getAttributeValue(null,"code");
                    }
                    else if(currentTag.equals("nameEn")) {
                        String cityName = xpp.nextText();
                        if(cityName.equals(city)) {
                            found = true;
                            filename = currentName;
                        }
                    }
                    else if (currentTag.equals("provinceCode") && found) {
                        provinceCode = xpp.nextText();
                        found = false;
                    }
                }
                eventType = xpp.next();
            }
            in.close();

        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "http://dd.weatheroffice.ec.gc.ca/citypage_weather/xml/"
                + provinceCode
                + "/" + filename + "_e.xml";
    }

    ////////////////////////////////////////////////////
    //PUT ALL WEATHER INFORMATION OF A CITY INTO MAIN VARIABLES
    public void getWeatherInfo(String url) {
        InputStream in = null;

        //connect to the file containing the weather info
        try {
            in = OpenHttpConnection(url);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //get weather information and put into global variables
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(in,null);
            String currentTag;
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = xpp.getName();
                    if (currentTag.equals("name")) {
                        latitude = xpp.getAttributeValue(null, "lat");
                        longtitude = xpp.getAttributeValue(null,"lon");
                    }
                    else if (currentTag.equals("currentConditions")){
                        boolean notdone = true;

                        while(notdone){
                            if(eventType == XmlPullParser.START_TAG) {
                                currentTag = xpp.getName();
                                if(currentTag.equals("condition")) {
                                    condition = xpp.nextText();
                                }
                                else if (currentTag.equals("temperature")){
                                    String unitType = xpp.getAttributeValue(null, "units");
                                    temperature = xpp.nextText() + unitType;
                                }
                                else if (currentTag.equals("dewpoint")) {
                                    String unitType = xpp.getAttributeValue(null, "units");
                                    dewPoint = xpp.nextText() + unitType;
                                }
                                else if (currentTag.equals("visibility")) {
                                    String unitType = xpp.getAttributeValue(null, "units");
                                    visibility = xpp.nextText() + unitType;
                                }
                                else if (currentTag.equals("relativeHumidity")){
                                    String unitType = xpp.getAttributeValue(null, "units");
                                    relativeHumidity = xpp.nextText() + unitType;
                                }
                                else if (currentTag.equals("speed")) {
                                    String unitType = xpp.getAttributeValue(null, "units");
                                    wind = xpp.nextText() + unitType;
                                }
                                else if (currentTag.equals("direction")) {
                                    windDirection = xpp.nextText();
                                }
                            }
                            else if (eventType == XmlPullParser.END_TAG && xpp.getName().equals("currentConditions")){
                                notdone = false;
                            }
                            eventType = xpp.next();
                        }
                    }
                }
                eventType = xpp.next();
            }

            in.close();

        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /////////////////////////////////////////
    //OPEN A CONNECTION TO AN ONLINE RESOURCE
    private InputStream OpenHttpConnection(String urlString)
            throws IOException {
        InputStream in = null;
        int response;

        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();

        if (!(conn instanceof HttpURLConnection))
            throw new IOException("Not an HTTP connection");
        try {
            HttpURLConnection httpConn = (HttpURLConnection) conn;
            httpConn.setAllowUserInteraction(false);
            httpConn.setInstanceFollowRedirects(true);
            httpConn.setRequestMethod("GET");
            httpConn.connect();
            response = httpConn.getResponseCode();
            if (response == HttpURLConnection.HTTP_OK) {
                in = httpConn.getInputStream();
            }
        } catch (Exception ex) {
            Log.d("Networking", ex.getLocalizedMessage());
            throw new IOException("Error connecting");
        }
        return in;
    }

    //INITIALIZE THE MAP ON STARTUP
    @Override
    public void onMapReady(GoogleMap googleMap) {
        googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(0,0))
                .title("You're here"));
    }

    /////////////////////////////////////
    //AN ASYNCTASK TO POPULATE CITY SEARCH
    private class PopulateCitySearch extends
            AsyncTask<Void, Void, ArrayList<String>> {
        protected ArrayList<String> doInBackground(Void... params) {
            return getCitiesList();
        }

        protected void onPostExecute(ArrayList<String> result) {
            AutoCompleteTextView tv = (AutoCompleteTextView) findViewById(R.id.citySearch);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(tv.getContext(), android.R.layout.simple_dropdown_item_1line, result);
            tv.setAdapter(adapter);
            tv.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            Toast.makeText(getApplicationContext(),"City names loaded.",Toast.LENGTH_LONG).show();
        }
    }

    //////////////////////////////////////
    //AN ASYNCTASK TO GET WEATHER INFORMATION FOR A CITY
    //AND DISPLAY ON THE UI AFTER COMPLETION
    private class GetWeatherTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String weatherfile = getWeatherFile(params[0]);
            getWeatherInfo(weatherfile);

            return null;
        }

        protected void onPostExecute(Void result) {
            TextView tv1 = (TextView)findViewById(R.id.tvLat);
            TextView tv2 = (TextView)findViewById(R.id.tvLon);
            tv1.setText(latitude);
            tv2.setText(longtitude);

            //get only the decimal part of from the coordinates (original format: 99.99N)
            double lat = Double.valueOf(latitude.substring(0, latitude.length() -1));
            double lon = Double.valueOf(longtitude.substring(0, longtitude.length() - 1));

            //N and E are positive, S and W are negative
            if (latitude.substring(latitude.length()-1).equals("S")) {
                lat *= -1;
            }
            if (longtitude.substring(longtitude.length()-1).equals("W")) {
                lon *= -1;
            }

            GoogleMap map = ((MapFragment)getFragmentManager().findFragmentById(R.id.map)).getMap();
            LatLng coordinates = new LatLng(lat, lon);

            //clear all markers on map
            map.clear();
            //put a marker on map that has a custom InfoWindow
            map.setInfoWindowAdapter(new CustomWeatherInfo());
            Marker marker = map.addMarker(new MarkerOptions().position(coordinates));
            marker.showInfoWindow();

            //move camera to the marker
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(coordinates, 10);
            map.moveCamera(cameraUpdate);
        }
    }

    //A CUSTOM INFOWINDOW THAT CONTAINS WEATHER INFORMATION FOR A CITY
    //http://stackoverflow.com/questions/16317224/custom-infowindow-with-google-maps-api-v2
    private class CustomWeatherInfo implements GoogleMap.InfoWindowAdapter {
        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

        //use this to use the default infowindow style
        @Override
        public View getInfoContents(Marker marker) {
            //bind a layout to a view used as the info window
            View infoView = getLayoutInflater().inflate(R.layout.weather_info, null);

            //get references to all the views in the layout
            TextView tvCity = (TextView) infoView.findViewById(R.id.tvCityName);
            TextView tvCondition = (TextView) infoView.findViewById(R.id.tvCondition);
            TextView tvTemp = (TextView) infoView.findViewById(R.id.tvTemperature);
            TextView tvDew = (TextView) infoView.findViewById(R.id.tvDewPoint);
            TextView tvVisibility = (TextView) infoView.findViewById(R.id.tvVisibility);
            TextView tvHumidity = (TextView) infoView.findViewById(R.id.tvHumidity);
            TextView tvWind = (TextView) infoView.findViewById(R.id.tvWindSpeed);
            TextView tvWindDir = (TextView) infoView.findViewById(R.id.tvWindDirection);
            //set texts (weather information) for such views
            tvCity.setText(cityName);
            tvCondition.setText(condition);
            tvTemp.setText(temperature);
            tvDew.setText(dewPoint);
            tvVisibility.setText(visibility);
            tvHumidity.setText(relativeHumidity);
            tvWind.setText(wind);
            tvWindDir.setText(windDirection);

            return infoView;
        }
    }
}
