/** Copyright 2014 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package io.prediction.tools

import io.prediction.data.storage.EngineManifest
import io.prediction.workflow.WorkflowUtils

import grizzled.slf4j.Logging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

import scala.sys.process._

import java.io.File

object RunWorkflow extends Logging {
  def runWorkflow(
      ca: ConsoleArgs,
      core: File,
      em: EngineManifest,
      variantJson: File): Unit = {
    // Collect and serialize PIO_* environmental variables
    val pioEnvVars = sys.env.filter(kv => kv._1.startsWith("PIO_")).map(kv =>
      s"${kv._1}=${kv._2}"
    ).mkString(",")

    val defaults = Map(
      "dsp" -> (ca.dataSourceParamsJsonPath, "datasource.json"),
      "pp" -> (ca.preparatorParamsJsonPath, "preparator.json"),
      "ap" -> (ca.algorithmsParamsJsonPath, "algorithms.json"),
      "sp" -> (ca.servingParamsJsonPath, "serving.json"),
      "mp" -> (ca.metricsParamsJsonPath, "metrics.json"))

    val sparkHome = ca.common.sparkHome.getOrElse(
      sys.env.get("SPARK_HOME").getOrElse("."))

    val hadoopConf = new Configuration
    val hdfs = FileSystem.get(hadoopConf)

    val extraFiles = WorkflowUtils.thirdPartyConfFiles

    val driverClassPathIndex =
      ca.common.sparkPassThrough.indexOf("--driver-class-path")
    val driverClassPathPrefix =
      if (driverClassPathIndex != -1)
        Seq(ca.common.sparkPassThrough(driverClassPathIndex + 1))
      else
        Seq()
    val extraClasspaths =
      driverClassPathPrefix ++ WorkflowUtils.thirdPartyClasspaths

    val workMode = ca.metricsClass.map(_ => "Evaluation").getOrElse("Training")
    val sparkSubmit =
      Seq(Seq(sparkHome, "bin", "spark-submit").mkString(File.separator)) ++
      ca.common.sparkPassThrough ++
      Seq(
        "--class",
        "io.prediction.workflow.CreateWorkflow",
        "--name",
        s"PredictionIO ${workMode}: ${em.id} ${em.version} (${ca.batch})",
        "--jars",
        (em.files ++ Console.builtinEngines(
          ca.common.pioHome.get).map(_.getCanonicalPath)).mkString(",")) ++
      (if (extraFiles.size > 0)
        Seq("--files", extraFiles.mkString(","))
      else
        Seq()) ++
      (if (extraClasspaths.size > 0)
        Seq("--driver-class-path", extraClasspaths.mkString(":"))
      else
        Seq()) ++
      Seq(
        core.getCanonicalPath,
        "--env",
        pioEnvVars,
        "--engineId",
        em.id,
        "--engineVersion",
        em.version,
        "--engineVariant",
        variantJson.getCanonicalPath) ++
      (if (ca.common.verbose) Seq("--verbose") else Seq()) ++
      (if (ca.common.debug) Seq("--debug") else Seq()) ++
      (if (ca.common.skipSanityCheck) Seq("--skip-sanity-check") else Seq()) ++
      (if (ca.common.stopAfterRead) Seq("--stop-after-read") else Seq()) ++
      (if (ca.common.stopAfterPrepare)
        Seq("--stop-after-prepare") else Seq()) ++
      ca.metricsClass.map(x => Seq("--metricsClass", x)).
        getOrElse(Seq()) ++
      (if (ca.batch != "") Seq("--batch", ca.batch) else Seq()) ++ Seq(
      "--jsonBasePath", ca.paramsPath) ++ defaults.flatMap { _ match {
        case (key, (path, default)) =>
          path.map(p => Seq(s"--$key", p)).getOrElse {
          if (hdfs.exists(new Path(ca.paramsPath + Path.SEPARATOR + default)))
            Seq(s"--$key", default)
          else
            Seq()
        }
      }}
    info(s"Submission command: ${sparkSubmit.mkString(" ")}")
    Process(sparkSubmit, None, "SPARK_YARN_USER_ENV" -> pioEnvVars).!
  }
}
