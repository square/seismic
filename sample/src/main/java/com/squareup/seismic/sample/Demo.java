package com.squareup.seismic.sample;

import android.app.Activity;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import com.squareup.seismic.ShakeDetector;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class Demo extends Activity implements ShakeDetector.Listener {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    ShakeDetector sd = new ShakeDetector(this);
    sd.start(sensorManager);

    TextView tv = new TextView(this);
    tv.setGravity(CENTER);
    tv.setText("Shake me, bro!");
    setContentView(tv, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
  }

  public void hearShake() {
    Toast.makeText(this, "Don't shake me, bro!", Toast.LENGTH_SHORT).show();
  }
}
