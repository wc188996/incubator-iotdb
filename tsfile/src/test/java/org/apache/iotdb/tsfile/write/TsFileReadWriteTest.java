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
package org.apache.iotdb.tsfile.write;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.ReadOnlyTsFile;
import org.apache.iotdb.tsfile.read.TsFileSequenceReader;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.read.expression.QueryExpression;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.apache.iotdb.tsfile.write.record.RowBatch;
import org.apache.iotdb.tsfile.write.record.TSRecord;
import org.apache.iotdb.tsfile.write.record.datapoint.*;
import org.apache.iotdb.tsfile.write.schema.FileSchema;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TsFileReadWriteTest {

  private final double delta = 0.0000001;
  private String path = "read_write_rle.tsfile";
  private File f;

  @Before
  public void setUp() throws Exception {
    f = new File(path);
    if (f.exists()) {
      assertTrue(f.delete());
    }
  }

  @After
  public void tearDown() throws Exception {
    f = new File(path);
    if (f.exists()) {
      assertTrue(f.delete());;
    }
  }

  @Test
  public void intTest() throws IOException, WriteProcessException {
    writeDataByTSRecord(TSDataType.INT32, (i) -> new IntDataPoint("sensor_1", (int) i), TSEncoding.RLE);
    readData((i, field, delta) -> assertEquals(i, field.getIntV()));
  }

  @Test
  public void longTest() throws IOException, WriteProcessException {
    writeDataByTSRecord(TSDataType.INT64, (i) -> new LongDataPoint("sensor_1", i), TSEncoding.RLE);
    readData((i, field, delta) -> assertEquals(i, field.getLongV()));
  }

  @Test
  public void floatTest() throws IOException, WriteProcessException {
    writeDataByTSRecord(TSDataType.FLOAT, (i) -> new FloatDataPoint("sensor_1", (float) i), TSEncoding.RLE);
    readData((i, field, delta) -> assertEquals(i, field.getFloatV(), delta));
  }

  @Test
  public void doubleTest() throws IOException, WriteProcessException {
    writeDataByTSRecord(TSDataType.DOUBLE, (i) -> new DoubleDataPoint("sensor_1", (double) i), TSEncoding.RLE);
    readData((i, field, delta) -> assertEquals(i, field.getDoubleV(), delta));
  }

  @Test
  public void rowBatchTest() throws IOException, WriteProcessException {
    writeDataByRowBatch();
    readData((i, field, delta) -> assertEquals(i, field.getLongV()));
  }

  @Test
  public void readEmptyMeasurementTest() throws IOException, WriteProcessException {
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      // add measurements into file schema
      tsFileWriter
          .addMeasurement(new MeasurementSchema("sensor_1", TSDataType.FLOAT, TSEncoding.RLE));
      tsFileWriter
          .addMeasurement(new MeasurementSchema("sensor_2", TSDataType.INT32, TSEncoding.TS_2DIFF));
      // construct TSRecord
      TSRecord tsRecord = new TSRecord(1, "device_1");
      DataPoint dPoint1 = new FloatDataPoint("sensor_1", 1.2f);
      tsRecord.addTuple(dPoint1);
      // write a TSRecord to TsFile
      tsFileWriter.write(tsRecord);
    }

    // read example : no filter
    TsFileSequenceReader reader = new TsFileSequenceReader(path);
    ReadOnlyTsFile readTsFile = new ReadOnlyTsFile(reader);
    ArrayList<Path> paths = new ArrayList<>();
    paths.add(new Path("device_1.sensor_2"));
    QueryExpression queryExpression = QueryExpression.create(paths, null);
    QueryDataSet queryDataSet = readTsFile.query(queryExpression);
    assertFalse(queryDataSet.hasNext());
    reader.close();
    assertTrue(f.delete());
  }

  @Test
  public void readMeasurementWithRegularEncodingTest() throws IOException, WriteProcessException {
    TSFileConfig.timeEncoder = "REGULAR";
    writeDataByTSRecord(TSDataType.INT64, (i) -> new LongDataPoint("sensor_1", i), TSEncoding.REGULAR);
    readData((i, field, delta) -> assertEquals(i, field.getLongV()));
    TSFileConfig.timeEncoder = "TS_2DIFF";
  }

  private void writeDataByTSRecord(TSDataType dataType, DataPointProxy proxy, TSEncoding encodingType)
          throws IOException, WriteProcessException {
    int floatCount = 1024 * 1024 * 13 + 1023;
    // add measurements into file schema
    try (TsFileWriter tsFileWriter = new TsFileWriter(f)) {
      tsFileWriter
          .addMeasurement(new MeasurementSchema("sensor_1", dataType, encodingType));
      for (long i = 1; i < floatCount; i++) {
        // construct TSRecord
        TSRecord tsRecord = new TSRecord(i, "device_1");
        DataPoint dPoint1 = proxy.generateOne(i);
        tsRecord.addTuple(dPoint1);
        // write a TSRecord to TsFile
        tsFileWriter.write(tsRecord);
      }
    }
  }

  private void writeDataByRowBatch()
          throws IOException, WriteProcessException {
    FileSchema fileSchema = new FileSchema();
    fileSchema.registerMeasurement(
            new MeasurementSchema("sensor_1", TSDataType.INT64, TSEncoding.TS_2DIFF));
    int rowNum = 1024 * 1024;
    int sensorNum = 1;
    TsFileWriter tsFileWriter = new TsFileWriter(f, fileSchema);
    RowBatch rowBatch = fileSchema.createRowBatch("device_1");
    long[] timestamps = rowBatch.timestamps;
    Object[] sensors = rowBatch.values;
    long timestamp = 1;
    long value = 1L;
    for (int r = 0; r < rowNum; r++, value++) {
      int row = rowBatch.batchSize++;
      timestamps[row] = timestamp++;
      for (int i = 0; i < sensorNum; i++) {
        long[] sensor = (long[]) sensors[i];
        sensor[row] = value;
      }
      if (rowBatch.batchSize == rowBatch.getMaxBatchSize()) {
        tsFileWriter.write(rowBatch);
        rowBatch.reset();
      }
    }
    if (rowBatch.batchSize != 0) {
      tsFileWriter.write(rowBatch);
      rowBatch.reset();
    }
    tsFileWriter.close();
  }

  private void readData(ReadDataPointProxy proxy) throws IOException {
    TsFileSequenceReader reader = new TsFileSequenceReader(path);
    ReadOnlyTsFile readTsFile = new ReadOnlyTsFile(reader);
    ArrayList<Path> paths = new ArrayList<>();
    paths.add(new Path("device_1.sensor_1"));
    QueryExpression queryExpression = QueryExpression.create(paths, null);

    QueryDataSet queryDataSet = readTsFile.query(queryExpression);
    for (int j = 0; j < paths.size(); j++) {
      assertEquals(paths.get(j), queryDataSet.getPaths().get(j));
    }
    int i = 1;
    while (queryDataSet.hasNext()) {
      RowRecord r = queryDataSet.next();
      assertEquals(i, r.getTimestamp());
      proxy.assertEqualProxy(i, r.getFields().get(0), delta);
      i++;
    }
    reader.close();
  }

  private interface DataPointProxy {
    DataPoint generateOne(long value);
  }
  private interface ReadDataPointProxy {
    void assertEqualProxy(long i, Field field, double delta);
  }
}
