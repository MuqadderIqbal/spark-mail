package com.uebercomputing.analytics.basic

import org.apache.log4j.Logger
import com.uebercomputing.mailrecord.MailRecordAnalytic
import com.uebercomputing.mailrecord.ExecutionTimer
import org.apache.spark.SparkContext.numericRDDToDoubleRDDFunctions
import org.apache.spark.SparkContext._
import com.uebercomputing.mailrecord.Implicits._

/**
 * Run with two args:
 *
 * Enron:
 * --avroMailInput /opt/rpm1/enron/enron_mail_20110402/mail.avro --master local[4]
 *
 * JebBush (all)
 * --avroMailInput /opt/rpm1/jebbush/avro-monthly --master local[4]
 *
 * JebBush (1999)
 * --avroMailInput /opt/rpm1/jebbush/avro-monthly/1999 --master local[4]
 */
object EmailAdxStats extends ExecutionTimer {

  val LOGGER = Logger.getLogger(EmailAdxStats.getClass)

  val To = "To"
  val From = "From"
  val Cc = "Cc"
  val Bcc = "Bcc"

  def main(args: Array[String]): Unit = {
    startTimer()
    val appName = "EmailAdxStats"
    val additionalSparkProps = Map[String, String]()
    val analyticInput = MailRecordAnalytic.getAnalyticInput(appName, args, additionalSparkProps, LOGGER)

    val emailAdxPairRdd = analyticInput.mailRecordRdd.flatMap { mailRecord =>
      val to = mailRecord.getToOpt().getOrElse(Nil)
      val cc = mailRecord.getCcOpt().getOrElse(Nil)
      val bcc = mailRecord.getBccOpt().getOrElse(Nil)
      val adxLists = List((To, to), (Cc, cc), (Bcc, bcc))

      val typeEmailAdxTuples = adxLists.flatMap {
        typeAdxTuple =>
          val (adxType, adx) = typeAdxTuple
          getAdxTuples(adxType, adx)
      }
      (From, mailRecord.getFrom()) :: typeEmailAdxTuples
    }

    //show how this is cached in UI (http://localhost:4040/storage)
    emailAdxPairRdd.cache()

    println(s"Total emails in RDD: ${analyticInput.mailRecordRdd.count()}")

    val uniqueAdxRdd = emailAdxPairRdd.values.distinct()
    uniqueAdxRdd.saveAsTextFile("uniqueAdx")

    val uniqueFroms = emailAdxPairRdd.filter { typeAdxTuple =>
      val (adxType, adx) = typeAdxTuple
      adxType == From
    }.values.distinct(4)
    uniqueFroms.saveAsTextFile("uniqueFroms")

    stopTimer()
    val prefixMsg = s"Executed over ${analyticInput.config.avroMailInput} in: "
    logTotalTime(prefixMsg, LOGGER)
  }

  def getAdxTuples(adxType: String, adx: List[String]): List[(String, String)] = {
    adx.map { addr =>
      (adxType, addr)
    }
  }

}
