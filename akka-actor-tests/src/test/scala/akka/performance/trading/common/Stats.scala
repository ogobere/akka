package akka.performance.trading.common

import scala.collection.immutable.TreeMap

case class Stats(
  name: String,
  load: Int,
  timestamp: Long = System.currentTimeMillis,
  durationNanos: Long,
  n: Long,
  min: Long,
  max: Long,
  mean: Double,
  tps: Double,
  percentiles: TreeMap[Int, Long]) {

  def median: Long = percentiles(50)
}

