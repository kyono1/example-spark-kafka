// Copyright (C) 2011-2012 the original author or authors.
// See the LICENCE.txt file distributed with this work for additional
// information regarding copyright ownership.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package example

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.streaming._
import org.apache.spark.streaming.dstream.DStream

case class WordCount(word: String, count: Int)

object WordCount {

  def mapLines(lines: DStream[String])(implicit context: Broadcast[JobContext]): DStream[String] = {
    val stopWords = context.value.config.stopWords

    lines.flatMap(_.split("\\s"))
      .map(_.strip(",").strip(".").toLowerCase)
      .filter(!stopWords.contains(_)).filter(!_.isEmpty)
  }

  def countWords(words: DStream[String])(implicit context: Broadcast[JobContext]): DStream[WordCount] = {

    val windowDuration = Seconds(context.value.config.windowDuration)

    val slideDuration = Seconds(context.value.config.slideDuration)

    val reduce: (Int, Int) => Int = _ + _

    val inverseReduce: (Int, Int) => Int = _ - _

    words.map(x => (x, 1)).reduceByKeyAndWindow(reduce, inverseReduce, windowDuration, slideDuration).map {
      case (word: String, count: Int) => WordCount(word, count)
    }.filter(wordCount => wordCount.count > 0)
  }

  def storeResults(results: DStream[WordCount])(implicit context: Broadcast[JobContext]): Unit = {
    results.foreachRDD { rdd =>
      rdd.foreach { wordCount =>
        val sink = context.value.sink
        val outputTopic = context.value.config.outputTopic
        sink.write(outputTopic, wordCount.toString)
      }
    }
  }
}
