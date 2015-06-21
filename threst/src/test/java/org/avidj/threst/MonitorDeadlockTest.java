package org.avidj.threst;

import static org.avidj.threst.ConcurrentTest.thread;
import static org.avidj.threst.ConcurrentTest.threads;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorDeadlockTest {
  private static final Logger LOG = LoggerFactory.getLogger(MonitorDeadlockTest.class);

  @Test
  public void testMonitorDeadlock() {
    final MonitorDeadlocker testClass = new MonitorDeadlocker();
    threads(
        thread().exec(() -> testClass.a() ),
        thread().exec(() -> testClass.b() ))
        .assertSuccess();
  }
  
  private static class MonitorDeadlocker {
    private final Object lockA = new Object();
    private final Object lockB = new Object();
    private volatile boolean aLocked = false;
    private volatile boolean bLocked = false;
    
    void a() throws InterruptedException {
      synchronized ( lockA ) {
        aLocked = true;
        LOG.info("a");
        aWaitBLocked();
        b();
      }
      aLocked = false;
    }
    
    private void aWaitBLocked() throws InterruptedException {
      while ( !bLocked ) {
        lockA.wait();
      }
    }

    void b() throws InterruptedException {
      synchronized ( lockB ) {
        bLocked = true;
        LOG.info("b");
        aWaitALocked();
        a();
      }
      bLocked = false;
    }

    private void aWaitALocked() throws InterruptedException {
      while ( !aLocked ) {
        lockB.wait();
      }
    }
  }
}
