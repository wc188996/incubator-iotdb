/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.tsfile.write.chunk;

import java.io.IOException;
import java.math.BigDecimal;
import org.apache.iotdb.tsfile.utils.Binary;
import org.apache.iotdb.tsfile.write.writer.TsFileIOWriter;

/**
 * IChunkWriter provides a list of writing methods for different value types.
 *
 * @author kangrong
 */
public interface IChunkWriter {

  /**
   * write a time value pair.
   */
  void write(long time, int value);

  /**
   * write a time value pair.
   */
  void write(long time, long value);

  /**
   * write a time value pair.
   */
  void write(long time, boolean value);

  /**
   * write a time value pair.
   */
  void write(long time, float value);

  /**
   * write a time value pair.
   */
  void write(long time, double value);

  /**
   * write a time value pair.
   */
  void write(long time, BigDecimal value);

  /**
   * write a time value pair.
   */
  void write(long time, Binary value);

  /**
   * write time series
   */
  void write(long[] timestamps, int[] values);

  /**
   * write time series
   */
  void write(long[] timestamps, long[] values);

  /**
   * write time series
   */
  void write(long[] timestamps, boolean[] values);

  /**
   * write time series
   */
  void write(long[] timestamps, float[] values);

  /**
   * write time series
   */
  void write(long[] timestamps, double[] values);

  /**
   * write time series
   */
  void write(long[] timestamps, BigDecimal[] values);

  /**
   * write time series
   */
  void write(long[] timestamps, Binary[] values);

  /**
   * flush data to TsFileIOWriter.
   */
  void writeToFileWriter(TsFileIOWriter tsfileWriter) throws IOException;

  /**
   * estimate memory usage of this series.
   */
  long estimateMaxSeriesMemSize();

  /**
   * return the serialized size of the chunk header + all pages (not including the un-sealed page).
   * Notice, call this method before calling writeToFileWriter(), otherwise the page buffer in
   * memory will be cleared.
   */
  long getCurrentChunkSize();

  /**
   * seal the current page which may has not enough data points in force.
   */
  void sealCurrentPage();

  int getNumOfPages();
}
