/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources

import org.apache.spark.sql.QueryTest
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.test.{SharedSparkSession, SQLTestUtils}

trait FileSourceCodecSuite extends QueryTest with SQLTestUtils with SharedSparkSession {

  protected def format: String
  protected val codecConfigName: String
  protected def availableCodecs: Seq[String]

  def testWithAllCodecs(name: String)(f: => Unit): Unit = {
    for (codec <- availableCodecs) {
      test(s"$name - file source $format - codec: $codec") {
        withSQLConf(codecConfigName -> codec) {
          f
        }
      }
    }
  }

  testWithAllCodecs("write and read") {
    withTempPath { dir =>
      testData
        .repartition(5)
        .write
        .format(format)
        .save(dir.getCanonicalPath)

      val df = spark.read.format(format).load(dir.getCanonicalPath)
      checkAnswer(df, testData)
    }
  }
}

class ParquetCodecSuite extends FileSourceCodecSuite {

  override def format: String = "parquet"
  override val codecConfigName: String = SQLConf.PARQUET_COMPRESSION.key
  // Exclude "lzo" because it is GPL-licenced so not included in Hadoop.
  // TODO(SPARK-36669): "lz4" codec fails due to HADOOP-17891.
  override protected def availableCodecs: Seq[String] =
    if (System.getProperty("os.arch") == "aarch64") {
      // Exclude "brotli" due to PARQUET-1975.
      Seq("none", "uncompressed", "snappy", "gzip", "zstd")
    } else {
      Seq("none", "uncompressed", "snappy", "gzip", "brotli", "zstd")
    }
}

class OrcCodecSuite extends FileSourceCodecSuite {

  override def format: String = "orc"
  override val codecConfigName: String = SQLConf.ORC_COMPRESSION.key
  override protected def availableCodecs = Seq("none", "uncompressed", "snappy",
    "zlib", "zstd", "lz4", "lzo")
}
