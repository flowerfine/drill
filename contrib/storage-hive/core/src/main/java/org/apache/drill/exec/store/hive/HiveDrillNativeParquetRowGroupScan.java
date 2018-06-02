/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.store.hive;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.exec.physical.base.PhysicalOperator;
import org.apache.drill.exec.proto.UserBitShared.CoreOperatorType;
import org.apache.drill.exec.store.StoragePluginRegistry;
import org.apache.drill.exec.store.parquet.AbstractParquetRowGroupScan;
import org.apache.drill.exec.store.parquet.RowGroupReadEntry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.io.parquet.ProjectionPusher;
import org.apache.hadoop.mapred.JobConf;

import java.io.IOException;
import java.util.List;

@JsonTypeName("hive-drill-native-parquet-row-group-scan")
public class HiveDrillNativeParquetRowGroupScan extends AbstractParquetRowGroupScan {

  private final HiveStoragePlugin hiveStoragePlugin;
  private final HiveStoragePluginConfig hiveStoragePluginConfig;
  private final HivePartitionHolder hivePartitionHolder;

  @JsonCreator
  public HiveDrillNativeParquetRowGroupScan(@JacksonInject StoragePluginRegistry registry,
                                            @JsonProperty("userName") String userName,
                                            @JsonProperty("hiveStoragePluginConfig") HiveStoragePluginConfig hiveStoragePluginConfig,
                                            @JsonProperty("rowGroupReadEntries") List<RowGroupReadEntry> rowGroupReadEntries,
                                            @JsonProperty("columns") List<SchemaPath> columns,
                                            @JsonProperty("hivePartitionHolder") HivePartitionHolder hivePartitionHolder,
                                            @JsonProperty("filter") LogicalExpression filter) throws ExecutionSetupException {
    this(userName,
        (HiveStoragePlugin) registry.getPlugin(hiveStoragePluginConfig),
        rowGroupReadEntries,
        columns,
        hivePartitionHolder,
        filter);
  }

  public HiveDrillNativeParquetRowGroupScan(String userName,
                                            HiveStoragePlugin hiveStoragePlugin,
                                            List<RowGroupReadEntry> rowGroupReadEntries,
                                            List<SchemaPath> columns,
                                            HivePartitionHolder hivePartitionHolder,
                                            LogicalExpression filter) {
    super(userName, rowGroupReadEntries, columns, filter);
    this.hiveStoragePlugin = Preconditions.checkNotNull(hiveStoragePlugin, "Could not find format config for the given configuration");
    this.hiveStoragePluginConfig = hiveStoragePlugin.getConfig();
    this.hivePartitionHolder = hivePartitionHolder;
  }

  @JsonProperty
  public HiveStoragePluginConfig getHiveStoragePluginConfig() {
    return hiveStoragePluginConfig;
  }

  @JsonProperty
  public HivePartitionHolder getHivePartitionHolder() {
    return hivePartitionHolder;
  }

  @JsonIgnore
  public HiveStoragePlugin getHiveStoragePlugin() {
    return hiveStoragePlugin;
  }

  @Override
  public PhysicalOperator getNewWithChildren(List<PhysicalOperator> children) {
    Preconditions.checkArgument(children.isEmpty());
    return new HiveDrillNativeParquetRowGroupScan(getUserName(), hiveStoragePlugin, rowGroupReadEntries, columns, hivePartitionHolder, filter);
  }

  @Override
  public int getOperatorType() {
    return CoreOperatorType.HIVE_DRILL_NATIVE_PARQUET_ROW_GROUP_SCAN_VALUE;
  }

  @Override
  public AbstractParquetRowGroupScan copy(List<SchemaPath> columns) {
    return new HiveDrillNativeParquetRowGroupScan(getUserName(), hiveStoragePlugin, rowGroupReadEntries, columns, hivePartitionHolder, filter);
  }

  @Override
  public boolean areCorruptDatesAutoCorrected() {
    return true;
  }

  @Override
  public Configuration getFsConf(RowGroupReadEntry rowGroupReadEntry) throws IOException {
    Path path = new Path(rowGroupReadEntry.getPath()).getParent();
    return new ProjectionPusher().pushProjectionsAndFilters(
        new JobConf(hiveStoragePlugin.getHiveConf()),
        path.getParent());
  }

  @Override
  public boolean supportsFileImplicitColumns() {
    return false;
  }

  @Override
  public List<String> getPartitionValues(RowGroupReadEntry rowGroupReadEntry) {
    return hivePartitionHolder.get(rowGroupReadEntry.getPath());
  }
}
