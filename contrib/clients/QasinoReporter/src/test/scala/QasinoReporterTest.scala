import org.scalatest._
import mediamath.metrics._
import com.codahale.metrics._
import com.fasterxml.jackson.module.scala._
import com.fasterxml.jackson.databind.ObjectMapper
import collection._
import scala.collection.immutable.ListMap

class QasinoReporterTest extends FlatSpec with Matchers {
	// name sanitization checks
	val sep = QasinoReporter.registryNameSeparator
	"A sanitized registry name" should "replace non-alphanumeric characters with underscores" in {
		QasinoReporter.sanitizeRegistryName("testing.abc") should be ("testing" + sep + "abc")
	}
	it should "change all uppercase characters to lowercase" in {
		QasinoReporter.sanitizeRegistryName("tEsT") should be ("test")
	}

	"The builder" should "throw an IllegalArgumentException if two metrics are built with names that are the same after sanitation" in {
		val counter1 = new Counter
		val counter1name = "testing_abc"
		val counter2 = new Counter
		val counter2name = "testing.abc"
		val metrics = new MetricRegistry
		metrics.register(MetricRegistry.name(counter1name), counter1)
		metrics.register(MetricRegistry.name(counter2name), counter2)
		intercept[IllegalArgumentException] {
			QasinoReporter.forRegistry(metrics).build()
		}
	}

	it should "throw an IllegalArgumentException if a suffix (column name) begins with a non-alpha character" in {
		val counter1 = new Counter
		val counter1name = "testing_123"
		val metrics = new MetricRegistry
		metrics.register(MetricRegistry.name(counter1name), counter1)
		intercept[IllegalArgumentException] {
			QasinoReporter.forRegistry(metrics).withGroupings(Set("testing")).build()
		}
	}

	{
		// JSON validation
		val mapper = new ObjectMapper()
		mapper.registerModule(DefaultScalaModule)
		val counter1 = new Counter
		val counter1name = "testing_abc"
		val counter2 = new Counter
		counter2.inc(100)
		val counter2name = "testing_def"
		val metrics = new MetricRegistry
		metrics.register(MetricRegistry.name(counter1name), counter1)
		metrics.register(MetricRegistry.name(counter2name), counter2)
		"The JSON generated by two separated metrics" should "be reported separately" in {
			val groupPrefix = "nothing_in_common"
			val reporter = QasinoReporter.forRegistry(metrics).withGroupings(Set(groupPrefix)).build()
			val jsonStrSeq = reporter.getJsonForMetrics(ListMap[String, Metric](counter1name -> counter1, counter2name -> counter2))
			assert(jsonStrSeq.size === 2)
		}
		"The JSON generated by two metrics with the same group" should "be reported together" in {
			val groupPrefix = "testing"
			val reporter = QasinoReporter.forRegistry(metrics).withGroupings(Set(groupPrefix)).build()
			val jsonStrSeq = reporter.getJsonForMetrics(ListMap[String, Metric](counter1name -> counter1, counter2name -> counter2))
			assert(jsonStrSeq.size === 1)
		}
		// Check JSON values
		{
			class IntGauge(value: Int) extends Gauge[Int] {
				def getValue = value
			}

			metrics.register("testing_gauge", new IntGauge(100))
			metrics.register("testing_meter", new Meter())
			metrics.register("testing_timer", new Timer())
			val groupPrefix = "testing"
			val reporter = QasinoReporter.forRegistry(metrics).withGroupings(Set(groupPrefix)).build()
			val jsonStrSeq = reporter.getJsonForMetrics(reporter.combineMetricsToMap())
			val dataMap = mapper.readValue(jsonStrSeq(0), classOf[Map[String, Any]])
			val tableDataMap = dataMap.getOrElse("table", {}).asInstanceOf[Map[String, Any]]
			"The op value" should "be add_table_data" in {
				assert("add_table_data" === dataMap("op"))
			}
			"The tablename value" should "be " + groupPrefix in {
				assert(groupPrefix === tableDataMap("tablename"))
			}
			val correctColumnNames = List(
        "gauge_value",
        "abc_count",
        "def_count",
        "meter_count",
        "meter_mean_rate",
        "meter_m1_rate",
        "meter_m5_rate",
        "meter_m15_rate",
        "meter_rate_unit",
        "timer_count",
        "timer_max",
        "timer_mean",
        "timer_min",
        "timer_stddev",
        "timer_p50",
        "timer_p75",
        "timer_p95",
        "timer_p98",
        "timer_p99",
        "timer_p999",
        "timer_mean_rate",
        "timer_m1_rate",
        "timer_m5_rate",
        "timer_m15_rate",
        "timer_rate_unit",
        "timer_duration_unit"
      )
			"The column_names" should "be " + correctColumnNames in {
				assert(tableDataMap("column_names") === correctColumnNames)
			}
			val correctColumnTypes = List(
        "string", // gauge_value
        "int", // abc_count
        "int", // def_count
        "int", // meter_count
        "int", // meter_mean_rate
        "int", // meter_m1_rate
        "int", // meter_m5_rate
        "int", // meter_m15_rate
        "string", // meter_rate_unit
        "int", // timer_count
        "int", // timer_max
        "int", // timer_mean
        "int", // timer_min
        "int", // timer_stddev
        "int", // timer_p50
        "int", // timer_p75
        "int", // timer_p95
        "int", // timer_p98
        "int", // timer_p99
        "int", // timer_p999
        "int", // timer_mean_rate
        "int", // timer_m1_rate
        "int", // timer_m5_rate
        "int", // timer_m15_rate
        "string", // timer_rate_unit
        "string"  // timer_duration_unit
      )
			"The column_types" should "be " + correctColumnTypes in {
				assert(tableDataMap("column_types") === correctColumnTypes)
			}
		}
	}

  import java.util.concurrent.TimeUnit
  class StringGauge(value: String) extends Gauge[String] {
    def getValue = value
  }
  class LongGauge(value: Long) extends Gauge[Long] {
    def getValue = value
  }

  val metrics = new MetricRegistry
  val markTime = System.currentTimeMillis
  println(s"Timestamp: $markTime")
  metrics.register("foo.stringGauge", new StringGauge("i'm a little teapot"))
  metrics.register("foo.longGauge", new LongGauge(markTime))
  val counter = new Counter
  counter.inc(100)
  metrics.register("foo.counter", counter)
  val reporter = QasinoReporter.forRegistry(metrics).withHost("localhost").withPersist().withGroupings(Set("foo")).build()
  try {
    reporter.start(1, TimeUnit.SECONDS)
    Thread.sleep(1000)
  }
  finally {
    reporter.shutdown()
  }
}
