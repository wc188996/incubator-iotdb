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
package org.apache.iotdb.db.qp.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.qp.constant.SQLConstant;
import org.apache.iotdb.db.qp.executor.AbstractQueryProcessExecutor;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.physical.crud.DeletePlan;
import org.apache.iotdb.db.qp.physical.crud.InsertPlan;
import org.apache.iotdb.db.qp.physical.crud.UpdatePlan;
import org.apache.iotdb.db.qp.physical.sys.AuthorPlan;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.fill.IFill;
import org.apache.iotdb.tsfile.exception.filter.QueryFilterOptimizationException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.IExpression;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.apache.iotdb.tsfile.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implement a simple executor with a memory demo reading processor for test.
 */
public class MemIntQpExecutor extends AbstractQueryProcessExecutor {

  private static Logger LOG = LoggerFactory.getLogger(MemIntQpExecutor.class);

  // pathStr, TreeMap<time, value>
  private Map<String, TestSeries> demoMemDataBase = new HashMap<>();

  private TreeSet<Long> timeStampUnion = new TreeSet<>();
  private Map<String, List<String>> fakeAllPaths;

  public void setFakeAllPaths(Map<String, List<String>> fakeAllPaths) {
    this.fakeAllPaths = fakeAllPaths;
  }

  @Override
  public TSDataType getSeriesType(Path fullPath) {
    if (fullPath.equals(SQLConstant.RESERVED_TIME)) {
      return TSDataType.INT64;
    }
    if (fullPath.equals(SQLConstant.RESERVED_FREQ)) {
      return TSDataType.FLOAT;
    }
    if (fakeAllPaths != null && fakeAllPaths.containsKey(fullPath.toString())) {
      return TSDataType.INT32;
    }
    if (demoMemDataBase.containsKey(fullPath.toString())) {
      return TSDataType.FLOAT;
    }
    return null;
  }

  @Override
  public boolean processNonQuery(PhysicalPlan plan) throws ProcessorException {
    switch (plan.getOperatorType()) {
      case DELETE:
        return delete((DeletePlan) plan);
      case UPDATE:
        UpdatePlan update = (UpdatePlan) plan;
        boolean flag = true;
        for (Pair<Long, Long> timePair : update.getIntervals()) {
          flag &= update(update.getPath(), timePair.left, timePair.right, update.getValue());
        }
        return flag;
      case INSERT:
        return insert((InsertPlan) plan);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public QueryDataSet aggregate(List<Path> paths, List<String> aggres, IExpression expression,
      QueryContext context)
      throws ProcessorException, IOException, PathErrorException, StorageEngineException,
      QueryFilterOptimizationException {
    return null;
  }

  @Override
  public QueryDataSet groupBy(List<Path> paths, List<String> aggres, IExpression expression,
      long unit, long origin, List<Pair<Long, Long>> intervals, QueryContext context)
      throws ProcessorException, IOException, PathErrorException, StorageEngineException,
      QueryFilterOptimizationException {
    return null;
  }

  @Override
  public QueryDataSet fill(List<Path> fillPaths, long queryTime, Map<TSDataType, IFill> fillTypes,
      QueryContext context)
      throws ProcessorException, IOException, PathErrorException, StorageEngineException {
    return null;
  }


  @Override
  public boolean judgePathExists(Path path) {
    if (SQLConstant.isReservedPath(path)) {
      return true;
    }
    if (fakeAllPaths != null) {
      return fakeAllPaths.containsKey(path.toString());
    }
    return demoMemDataBase.containsKey(path.toString());
  }

  @Override
  public boolean update(Path path, long startTime, long endTime, String value) {
    if (!demoMemDataBase.containsKey(path.toString())) {
      LOG.warn("no series:{}", path);
      return false;
    }
    TestSeries series = demoMemDataBase.get(path.toString());
    for (Entry<Long, Integer> entry : series.data.entrySet()) {
      long timestamp = entry.getKey();
      if (timestamp >= startTime && timestamp <= endTime) {
        entry.setValue(Integer.valueOf(value));
      }
    }
    LOG.info("update, series:{}, time range:<{},{}>, value:{}", path, startTime, endTime, value);
    return true;
  }

  @Override
  public boolean delete(Path path, long deleteTime) {
    if (!demoMemDataBase.containsKey(path.toString())) {
      return true;
    }
    TestSeries series = demoMemDataBase.get(path.toString());
    TreeMap<Long, Integer> delResult = new TreeMap<>();
    for (Entry<Long, Integer> entry : series.data.entrySet()) {
      long timestamp = entry.getKey();
      if (timestamp >= deleteTime) {
        delResult.put(timestamp, entry.getValue());
      }
    }
    series.data = delResult;
    LOG.info("delete series:{}, timestamp:{}", path, deleteTime);
    return true;
  }

  @Override
  public List<String> getAllPaths(String fullPath) {
    return fakeAllPaths != null ? fakeAllPaths.get(fullPath) : new ArrayList<String>() {
      {
        add(fullPath);
      }
    };
  }

  @Override
  public boolean insert(InsertPlan insertPlan) {
    for (int i = 0; i < insertPlan.getMeasurements().length; i++) {
      String strPath = insertPlan.getDeviceId() + IoTDBConstant.PATH_SEPARATOR + insertPlan.getMeasurements()[i];
      if (!demoMemDataBase.containsKey(strPath)) {
        demoMemDataBase.put(strPath, new TestSeries());
      }
      demoMemDataBase.get(strPath).data.put(insertPlan.getTime(), Integer.valueOf(insertPlan.getValues()[i]));
      timeStampUnion.add(insertPlan.getTime());
    }
    return true;
  }

  @Override
  protected QueryDataSet processAuthorQuery(AuthorPlan plan, QueryContext context) {
    return null;
  }

  private class TestSeries {

    public TreeMap<Long, Integer> data = new TreeMap<>();
  }
}
