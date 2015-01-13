package com.squareup.seismic;

import android.hardware.SensorEvent;

import org.junit.Test;

import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;

public class ShakeDetectorTest {
  @Test public void testInitialShaking() {
    ShakeDetector.SampleQueue q = new ShakeDetector.SampleQueue();
    assertThat(q.isShaking()).isFalse();
  }

  /** Tests LG Ally sample rate. */
  @Test public void testShakingSampleCount3() {
    ShakeDetector.SampleQueue q = new ShakeDetector.SampleQueue();

    // These times approximate the data rate of the slowest device we've
    // found, the LG Ally.
    // On the LG Ally the queue holds 500000000 ns (0.5ms) of samples or
    // 4 samples, whichever is greater.
    // 500000000
    q.add(1000000000L, false);
    q.add(1300000000L, false);
    q.add(1600000000L, false);
    q.add(1900000000L, false);
    assertContent(q, false, false, false, false);
    assertThat(q.isShaking()).isFalse();

    // The oldest two entries will be removed.
    q.add(2200000000L, true);
    q.add(2500000000L, true);
    assertContent(q, false, false, true, true);
    assertThat(q.isShaking()).isFalse();

    // Another entry should be removed, now 3 out of 4 are true.
    q.add(2800000000L, true);
    assertContent(q, false, true, true, true);
    assertThat(q.isShaking()).isTrue();

    q.add(3100000000L, false);
    assertContent(q, true, true, true, false);
    assertThat(q.isShaking()).isTrue();

    q.add(3400000000L, false);
    assertContent(q, true, true, false, false);
    assertThat(q.isShaking()).isFalse();
  }

  private void assertContent(ShakeDetector.SampleQueue q, boolean... expected) {
    List<ShakeDetector.Sample> samples = q.asList();

    StringBuilder sb = new StringBuilder();
    for (ShakeDetector.Sample s : samples) {
      sb.append(String.format("[%b,%d] ", s.accelerating, s.timestamp));
    }

    assertThat(samples).hasSize(expected.length);
    for (int i = 0; i < expected.length; i++) {
      assertThat(samples.get(i).accelerating).isEqualTo(expected[i]);
    }
  }

  @Test public void testClear() {
    ShakeDetector.SampleQueue q = new ShakeDetector.SampleQueue();
    q.add(1000000000L, true);
    q.add(1200000000L, true);
    q.add(1400000000L, true);
    assertThat(q.isShaking()).isTrue();
    q.clear();
    assertThat(q.isShaking()).isFalse();
  }


  public class ShakeCounter implements ShakeDetector.Listener {
    public int shakes = 0;

    @Override
    public void hearShake() {
      shakes++;
    }
  }

  long now = 0;

  /** Force-feeds d milliseconds of shaking to s.
    */

  public void addShake(ShakeDetector s, int d) {
    int n = 0;
    while (n < d) {
      s.actualOnSensorChanged(new float[] { 1000.0f, 1000.0f, 9.81f }, now);
      n += 40;
      now += 40000000;
    }
  }


  /** Force-feeds d milliseconds of perfect peace to s.
    */

  public void addSilence(ShakeDetector s, int d) {
    int n = 0;
    while (n < d) {
      s.actualOnSensorChanged(new float[] { 0.0f, 0.0f, 9.81f }, now);
      n += 40;
      now += 40000000;
    }
  }


  /** Tests that a one-second shake results in one hearShake() call. */
  @Test public void testHearShake() {
    ShakeCounter c = new ShakeCounter();
    ShakeDetector s = new ShakeDetector(c);

    addShake(s, 1000);

    assertThat(c.shakes == 1).isTrue();
  }


  /** Tests that a one-second shake followed by one second of peace
   *  followed by another shake results in two hearShake() calls.
    */

  @Test public void testHearTwoShakes() {
    ShakeCounter c = new ShakeCounter();
    ShakeDetector s = new ShakeDetector(c);

    addShake(s, 1000);
    addSilence(s, 1000);
    addShake(s, 1000);

    assertThat(c.shakes == 2).isTrue();
  }


  /** Tests that a briefly interrupted shakes only count as one.
   */

  @Test public void testDisregardCloseShakes() {
    ShakeCounter c = new ShakeCounter();
    ShakeDetector s = new ShakeDetector(c);

    addShake(s, 1000);
    addSilence(s, 100);
    addShake(s, 1000);
    assertThat(c.shakes == 1).isTrue();

    addSilence(s, 1000);
    addShake(s, 1000);
    assertThat(c.shakes == 2).isTrue();
  }


  /** Tests that short shakes simply aren't. */

  @Test public void testDisregardShortShakes() {
    ShakeCounter c = new ShakeCounter();
    ShakeDetector s = new ShakeDetector(c);

    addShake(s, 200);
    addSilence(s, 1000);
    addShake(s, 200);

    assertThat(c.shakes == 0).isTrue();
  }


}
