package tdr.concurrent

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration

/** Concurrency primitives for the analyzer.
  *
  * The analyzer's parallel work is dominated by *blocking* operations: reading
  * thousands of files and waiting on the `git` subprocess. Virtual threads
  * (JDK 21+, JEP 444) are the right fit:
  *
  *   - Each task gets its own cheap virtual thread, so blocking on I/O parks the
  *     virtual thread and frees the carrier thread instead of holding an OS
  *     thread hostage.
  *   - Because tasks are not competing for a small, fixed pool of carrier
  *     threads, a task may safely block waiting on other tasks without the
  *     classic "thread-pool starvation" deadlock you'd hit on a bounded pool.
  */
object Concurrency:

  /** A fresh virtual-thread-per-task executor. The caller owns it and must
    * `shutdown()` it (see [[withVirtualThreads]] for a managed version).
    */
  def virtualThreadExecutor(): ExecutorService =
    Executors.newVirtualThreadPerTaskExecutor()

  /** Runs `body` with an `ExecutionContext` backed by virtual threads, then
    * shuts the executor down. The blocking `Await` happens on the *calling*
    * thread, never on a pool thread, so it cannot starve the executor.
    */
  def withVirtualThreads[A](body: ExecutionContext ?=> Future[A]): A =
    val es = virtualThreadExecutor()
    try
      given ec: ExecutionContext = ExecutionContext.fromExecutorService(es)
      Await.result(body, Duration.Inf)
    finally es.shutdown()
