/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.proxy.backend.handler.distsql.ral.updatable;

import org.apache.shardingsphere.data.pipeline.common.config.process.PipelineProcessConfiguration;
import org.apache.shardingsphere.data.pipeline.common.context.PipelineContextKey;
import org.apache.shardingsphere.data.pipeline.core.job.service.InventoryIncrementalJobAPI;
import org.apache.shardingsphere.data.pipeline.core.job.service.InventoryIncrementalJobManager;
import org.apache.shardingsphere.data.pipeline.core.job.service.PipelineJobAPI;
import org.apache.shardingsphere.distsql.handler.ral.update.RALUpdater;
import org.apache.shardingsphere.distsql.statement.ral.updatable.AlterInventoryIncrementalRuleStatement;
import org.apache.shardingsphere.infra.instance.metadata.InstanceType;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.proxy.backend.handler.distsql.ral.updatable.converter.InventoryIncrementalProcessConfigurationSegmentConverter;

/**
 * Alter inventory incremental rule updater.
 */
public final class AlterInventoryIncrementalRuleUpdater implements RALUpdater<AlterInventoryIncrementalRuleStatement> {
    
    @Override
    public void executeUpdate(final String databaseName, final AlterInventoryIncrementalRuleStatement sqlStatement) {
        InventoryIncrementalJobManager jobManager = new InventoryIncrementalJobManager((InventoryIncrementalJobAPI) TypedSPILoader.getService(PipelineJobAPI.class, sqlStatement.getJobTypeName()));
        PipelineProcessConfiguration processConfig = InventoryIncrementalProcessConfigurationSegmentConverter.convert(sqlStatement.getProcessConfigSegment());
        jobManager.alterProcessConfiguration(new PipelineContextKey(InstanceType.PROXY), processConfig);
    }
    
    @Override
    public Class<AlterInventoryIncrementalRuleStatement> getType() {
        return AlterInventoryIncrementalRuleStatement.class;
    }
}
