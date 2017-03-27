// Copyright 2010 Square, Inc.
package com.squareup.seismic;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects phone shaking. If more than 75% of the samples taken in the past 0.5s are
 * accelerating, the device is a) shaking, or b) free falling 1.84m (h =
 * 1/2*g*t^2*3/4).
 *
 * @author Bob Lee (bob@squareup.com)
 * @author Eric Burke (eric@squareup.com)
 */
public class ShakeDetector implements SensorEventListener {

  public static final int SENSITIVITY_LIGHT = 11;
  public static final int SENSITIVITY_MEDIUM = 13;
  public static final int SENSITIVITY_HARD = 15;

  private static final int DEFAULT_ACCELERATION_THRESHOLD = SENSITIVITY_MEDIUM;

  /**
   * When the magnitude of total acceleration exceeds this
   * value, the phone is accelerating.
   */
  private int accelerationThreshold = DEFAULT_ACCELERATION_THRESHOLD;

  /** Listens for shakes. */
  public interface Listener {
    /** Called on the main thread when the device is shaken. */
    void hearShake();
  }

  private final SampleQueue queue = new SampleQueue();
  private final Listener listener;

  private SensorManager sensorManager;
  private Sensor accelerometer;

  private boolean stillShaking = false;

  public ShakeDetector(Listener listener) {
    this.listener = listener;
  }

  /**
   * Starts listening for shakes on devices with appropriate hardware.
   *
   * @return true if the device supports shake detection.
   */
  public boolean start(SensorManager sensorManager) {
    // Already started?
    if (accelerometer != null) {
      return true;
    }

    accelerometer = sensorManager.getDefaultSensor(
        Sensor.TYPE_ACCELEROMETER);

    // If this phone has an accelerometer, listen to it.
    if (accelerometer != null) {
      this.sensorManager = sensorManager;
      sensorManager.registerListener(this, accelerometer,
          SensorManager.SENSOR_DELAY_FASTEST);
    }
    return accelerometer != null;
  }

  /**
   * Stops listening.  Safe to call when already stopped.  Ignored on devices
   * without appropriate hardware.
   */
  public void stop() {
    if (accelerometer != null) {
      queue.clear();
      sensorManager.unregisterListener(this, accelerometer);
      sensorManager = null;
      accelerometer = null;
    }
  }

  @Override public void onSensorChanged(SensorEvent event) {
    actualOnSensorChanged(event.values, event.timestamp);
  }


  /** This slight change of onSensorChanged() exists for testing;
   *  the tests cannot create a SensorEvent so onSensorChanged()
   *  itself cannot be used in the tests.
   */
  public void actualOnSensorChanged(final float[] values, long timestamp) {
    queue.add(timestamp, isAccelerating(values));
    if (!queue.canDecide()) {
      // not enough data yet, wait
    } else if (!queue.isShaking()) {
      // device is not longer shaking, so allow another shake to commence
      stillShaking = false;
    } else if (!stillShaking) {
      // the device is shaking, and we've seen enough of a pause to
      // be sure it's not just a continuation of the previous shake.
      queue.clear();
      listener.hearShake();
      stillShaking = true;
    } else {
      // the device is shaking, but we've already reacted to this shake,
      // the user just hasn't stopped shaking yet.
    }
  }

  /** Returns true if the device is currently accelerating. */
  private boolean isAccelerating(final float[] values) {
    float ax = values[0];
    float ay = values[1];
    float az = values[2];

    // Instead of comparing magnitude to ACCELERATION_THRESHOLD,
    // compare their squares. This is equivalent and doesn't need the
    // actual magnitude, which would be computed using (expensive) Math.sqrt().
    final double magnitudeSquared = ax * ax + ay * ay + az * az;
    return magnitudeSquared > accelerationThreshold * accelerationThreshold;
  }

  /** Sets the acceleration threshold sensitivity. */
  public void setSensitivity(int accelerationThreshold) {
    this.accelerationThreshold = accelerationThreshold;
  }

  /** Queue of samples. Keeps a running average. */
  static class SampleQueue {

    /** Window size in ns. Used to compute the average. */
    private static final long MAX_WINDOW_SIZE = 500000000; // 0.5s
    private static final long MIN_WINDOW_SIZE = MAX_WINDOW_SIZE >> 1; // 0.25s

    /**
     * Ensure the queue size never falls below this size, even if the device
     * fails to deliver this many events during the time window. The LG Ally
     * is one such device.
     */
    private static final int MIN_QUEUE_SIZE = 4;

    private final SamplePool pool = new SamplePool();

    private Sample oldest;
    private Sample newest;
    private int sampleCount;
    private int acceleratingCount;

    /**
     * Adds a sample.
     *
     * @param timestamp    in nanoseconds of sample
     * @param accelerating true if > {@link #accelerationThreshold}.
     */
    void add(long timestamp, boolean accelerating) {
      // Purge samples that proceed window.
      purge(timestamp - MAX_WINDOW_SIZE);

      // Add the sample to the queue.
      Sample added = pool.acquire();
      added.timestamp = timestamp;
      added.accelerating = accelerating;
      added.next = null;
      if (newest != null) {
        newest.next = added;
      }
      newest = added;
      if (oldest == null) {
        oldest = added;
      }

      // Update running average.
      sampleCount++;
      if (accelerating) {
        acceleratingCount++;
      }
    }

    /** Removes all samples from this queue. */
    void clear() {
      while (oldest != null) {
        Sample removed = oldest;
        oldest = removed.next;
        pool.release(removed);
      }
      newest = null;
      sampleCount = 0;
      acceleratingCount = 0;
    }

    /** Purges samples with timestamps older than cutoff. */
    void purge(long cutoff) {
      while (sampleCount >= MIN_QUEUE_SIZE
          && oldest != null && cutoff - oldest.timestamp > 0) {
        // Remove sample.
        Sample removed = oldest;
        if (removed.accelerating) {
          acceleratingCount--;
        }
        sampleCount--;

        oldest = removed.next;
        if (oldest == null) {
          newest = null;
        }
        pool.release(removed);
      }
    }

    /** Copies the samples into a list, with the oldest entry at index 0. */
    List<Sample> asList() {
      List<Sample> list = new ArrayList<Sample>();
      Sample s = oldest;
      while (s != null) {
        list.add(s);
        s = s.next;
      }
      return list;
    }

    /**
     * Returns true if we have enough samples to decide whether the
     * user is shaking the device.
     */
    boolean canDecide() {
      return newest != null
          && oldest != null
          && newest.timestamp - oldest.timestamp >= MIN_WINDOW_SIZE;
    }

    /**
     * Returns true if we have enough samples and more than 3/4 of those samples
     * are accelerating.
     */
    boolean isShaking() {
      return canDecide()
          && acceleratingCount >= (sampleCount >> 1) + (sampleCount >> 2);
    }
  }

  /** An accelerometer sample. */
  static class Sample {
    /** Time sample was taken. */
    long timestamp;

    /** If acceleration > {@link #accelerationThreshold}. */
    boolean accelerating;

    /** Next sample in the queue or pool. */
    Sample next;
  }

  /** Pools samples. Avoids garbage collection. */
  static class SamplePool {
    private Sample head;

    /** Acquires a sample from the pool. */
    Sample acquire() {
      Sample acquired = head;
      if (acquired == null) {
        acquired = new Sample();
      } else {
        // Remove instance from pool.
        head = acquired.next;
      }
      return acquired;
    }

    /** Returns a sample to the pool. */
    void release(Sample sample) {
      sample.next = head;
      head = sample;
    }
  }

  @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }
}
