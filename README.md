threst
================

A utility for automated testing for synchronization issues such as deadlocks or starvation. 
Blocks to be performed concurrently are defined using lambdas. To provoke synchronization issues 
it is often required to execute a test many times. This produces more possible interleavings of 
not-mutually-excluded code blocks.

Consider the following example:

  threads(
    thread().exec(() -> testClass.a() ), // execute this block in one thread
    thread().exec(() -> testClass.b() )) // and that in another
      .repeat(1000)                      // repeat execution 1k times to provoke issues
      .assertSuccess();


  
Deadlocks are detected and cause unit tests to fail.

Consider this class from threst's test suite. Please note, this test is successful if a deadlock is found.

  public class MonitorDeadlockTest {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorDeadlockTest.class);

    @Test ( expected = AssertionError.class )
    public void testMonitorDeadlock() {
      final MonitorDeadlocker testClass = new MonitorDeadlocker();    
      threads(
        thread().exec(() -> testClass.a() ),
        thread().exec(() -> testClass.b() ))
          .assertSuccess();
    }
  }

This produces the following output:
  
  Deadlock detected:
  "Thread-1" Id=13 BLOCKED on java.lang.Object@29913cf4 owned by "Thread-2" Id=14
  "Thread-2" Id=14 BLOCKED on java.lang.Object@2756c7b6 owned by "Thread-1" Id=13

You may want to assert that only some threads succeed. Consider this test for a lock manager where only one thread
can successfully obtain a lock:

  @Test
  public void testForDeadlocks() {
    threads(
        thread().exec(() -> {
          boolean success = lm.readLock("1", key("a", "b", "c"), LockScope.DEEP);
          assertThat(success, is(true));
        }),
        thread().exec(() -> {
          boolean success = lm.writeLock("2", key("a", "b", "c"), LockScope.DEEP);
          assertThat(success, is(true));
        }))
      .repeat(10000)          
      .assertSuccessCount(1); // either one of the threads must fail
  }
