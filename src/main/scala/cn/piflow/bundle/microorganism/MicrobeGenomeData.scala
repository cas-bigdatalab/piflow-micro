package cn.piflow.bundle.microorganism

import java.io._

import cn.piflow.bundle.microorganism.util.{CustomIOTools,Process}
import cn.piflow.conf.bean.PropertyDescriptor
import cn.piflow.conf.util.{ImageUtil, MapUtil}
import cn.piflow.conf.{ConfigurableStop, Port, StopGroup}
import cn.piflow.{JobContext, JobInputStream, JobOutputStream, ProcessContext}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataInputStream, FileSystem, Path}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.biojavax.bio.seq.{RichSequence, RichSequenceIterator}
import org.json.JSONObject


class MicrobeGenomeData extends ConfigurableStop{
  override val authorEmail: String = "yangqidong@cnic.cn"
  override val description: String = "Parse MicrobeGenome data"
  override val inportList: List[String] =List(Port.DefaultPort.toString)
  override val outportList: List[String] = List(Port.DefaultPort.toString)

  var cachePath:String = _
  override def perform(in: JobInputStream, out: JobOutputStream, pec: JobContext): Unit = {
    val spark = pec.get[SparkSession]()
    val inDf: DataFrame = in.read()

    val configuration: Configuration = new Configuration()
    var pathStr: String =inDf.take(1)(0).get(0).asInstanceOf[String]
    val pathARR: Array[String] = pathStr.split("\\/")
    var hdfsUrl:String=""
    for (x <- (0 until 3)){
      hdfsUrl+=(pathARR(x) +"/")
    }
    configuration.set("fs.defaultFS",hdfsUrl)
    var fs: FileSystem = FileSystem.get(configuration)

    val hdfsPathTemporary = hdfsUrl+cachePath+"/microbeGenomeCache/microbeGenomeCache.json"

    val path: Path = new Path(hdfsPathTemporary)
    if(fs.exists(path)){
      fs.delete(path)
    }
    fs.create(path).close()

    val hdfsWriter: OutputStreamWriter = new OutputStreamWriter(fs.append(path))

    var fdis: FSDataInputStream =null
    var br: BufferedReader = null
    var sequences: RichSequenceIterator = null
    var doc: JSONObject = null
    var seq: RichSequence = null

    var count:Int=0
    inDf.collect().foreach(row => {

      pathStr = row.get(0).asInstanceOf[String]

      fdis = fs.open(new Path(pathStr))
      br = new BufferedReader(new InputStreamReader(fdis))
      sequences = CustomIOTools.IOTools.readGenbankProtein(br, null)

      while (sequences.hasNext) {
        count += 1
        doc = new JSONObject()
        seq = sequences.nextRichSequence()
        Process.processSingleSequence(seq, doc)

        doc.write(hdfsWriter)
        hdfsWriter.write("\n")

        seq = null
        }
        br.close()
        fdis.close()
    })
    hdfsWriter.close()

    println("start parser HDFSjsonFile")
    val df: DataFrame = spark.read.json(hdfsPathTemporary)
    out.write(df)
  }


  def setProperties(map: Map[String, Any]): Unit = {
    cachePath=MapUtil.get(map,key="cachePath").asInstanceOf[String]
  }
  override def getPropertyDescriptor(): List[PropertyDescriptor] = {
    var descriptor : List[PropertyDescriptor] = List()
    val cachePath = new PropertyDescriptor().name("cachePath").displayName("cachePath").description("Temporary Cache File Path")
      .defaultValue("/microbeGenome").required(true)
    descriptor = cachePath :: descriptor
    descriptor
  }

  override def getIcon(): Array[Byte] = {
    ImageUtil.getImage("icon/microorganism/MicrobeGEnomeData.png")
  }

  override def getGroup(): List[String] = {
    List(StopGroup.MicroorganismGroup)
  }

  override def initialize(ctx: ProcessContext): Unit = {

  }
}
