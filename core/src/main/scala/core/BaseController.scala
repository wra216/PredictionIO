package io.prediction.core

import scala.reflect.Manifest


// FIXME(yipjustin). I am being lazy...
import io.prediction._
import org.apache.spark.rdd.RDD
import scala.reflect.ClassTag
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf

abstract
class BaseCleanser[TD, CD, CP <: BaseParams: Manifest]
  extends AbstractParameterizedDoer[CP] {
  def cleanseBase(trainingData: Any): CD
}


abstract
class LocalCleanser[TD, CD : Manifest, CP <: BaseParams: Manifest]
  extends BaseCleanser[RDD[TD], RDD[CD], CP] {

  def cleanseBase(trainingData: Any): RDD[CD] = {
    println("Local.cleanseBase.")
    trainingData
      .asInstanceOf[RDD[TD]]
      .map(cleanse)
  }

  def cleanse(trainingData: TD): CD
}

abstract
class SparkCleanser[TD, CD, CP <: BaseParams: Manifest]
  extends BaseCleanser[TD, CD, CP] {
  def cleanseBase(trainingData: Any): CD = {
    println("SparkCleanser.cleanseBase")
    cleanse(trainingData.asInstanceOf[TD])
  }

  def cleanse(trainingData: TD): CD
}

/* Algorithm */

trait LocalModelAlgorithm {
  def getModel(baseModel: Any): RDD[Any]
}

abstract class BaseAlgorithm[CD, F : Manifest, P, M, AP <: BaseParams: Manifest]
  extends AbstractParameterizedDoer[AP] {
  def trainBase(sc: SparkContext, cleansedData: CD): M

  def predictBase(baseModel: Any, baseFeature: Any): P

  def featureClass() = manifest[F]

}

abstract class LocalAlgorithm[CD, F : Manifest, P, M: Manifest,
    AP <: BaseParams: Manifest]
  extends BaseAlgorithm[RDD[CD], F, P, RDD[M], AP] 
  with LocalModelAlgorithm {
  def trainBase(sc: SparkContext, cleansedData: RDD[CD]): RDD[M] = {
    cleansedData.map(train)
  }
 
  override
  def predictBase(baseModel: Any, baseFeature: Any): P = {
    predict(baseModel.asInstanceOf[M], baseFeature.asInstanceOf[F])
  }
  
  def getModel(baseModel: Any): RDD[Any] = {
    baseModel.asInstanceOf[RDD[Any]]
  }

  def train(cleansedData: CD): M

  def predict(model: M, feature: F): P
}

abstract class Spark2LocalAlgorithm[CD, F : Manifest, P, M: Manifest,
    AP <: BaseParams: Manifest]
  extends BaseAlgorithm[CD, F, P, RDD[M], AP]
  with LocalModelAlgorithm {
  // train returns a local object M, and we parallelize it.
  //def trainBase(sc: SparkContext, cleansedData: CD): RDD[Any] = {
  def trainBase(sc: SparkContext, cleansedData: CD): RDD[M] = {
    val m: M = train(cleansedData)
    sc.parallelize(Array(m))
  }

  def getModel(baseModel: Any): RDD[Any] = {
    baseModel.asInstanceOf[RDD[Any]]
  }
  
  override
  def predictBase(baseModel: Any, baseFeature: Any): P = {
    predict(baseModel.asInstanceOf[M], baseFeature.asInstanceOf[F])
  }

  def train(cleansedData: CD): M

  def predict(model: M, feature: F): P
}

/* Server */
abstract class BaseServer[F, P, SP <: BaseParams: Manifest]
  extends AbstractParameterizedDoer[SP] {

  def combineBase(
    baseFeature: Any,
    basePredictions: Seq[Any])
    : P = {
    combine(
      baseFeature.asInstanceOf[F],
      basePredictions.map(_.asInstanceOf[P]))
  }

  def combine(feature: F, predictions: Seq[P]): P

}

/* Engine */
class BaseEngine[TD, CD, F, P](
    val cleanserClass
      : Class[_ <: BaseCleanser[TD, CD, _ <: BaseParams]],
    val algorithmClassMap
      : Map[String, Class[_ <: BaseAlgorithm[CD, F, P, _, _ <: BaseParams]]],
    val serverClass: Class[_ <: BaseServer[F, P, _ <: BaseParams]])

/*
class LocalEngine[
    TD,
    CD,
    F,
    P](
    cleanserClass
      : Class[_ <: LocalCleanser[TD, CD, _ <: BaseParams]],
    algorithmClassMap
      : Map[String,
        Class[_ <:
          LocalAlgorithm[CD, F, P, _, _]]],
    serverClass: Class[_ <: BaseServer[F, P, _ <: BaseParams]])
    extends BaseEngine(cleanserClass, algorithmClassMap, serverClass)

class SparkEngine[
    TD,
    CD,
    F,
    P](
    cleanserClass
      : Class[_ <: SparkCleanser[TD, CD, _ <: BaseParams]],
    algorithmClassMap
      : Map[String,
        Class[_ <:
          Spark2LocalAlgorithm[CD, F, P, _, _]]],
    serverClass: Class[_ <: BaseServer[F, P, _ <: BaseParams]])
    extends BaseEngine(cleanserClass, algorithmClassMap, serverClass)
<<<<<<< HEAD
=======
*/