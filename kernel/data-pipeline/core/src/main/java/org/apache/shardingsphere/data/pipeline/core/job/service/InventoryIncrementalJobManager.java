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

package org.apache.shardingsphere.data.pipeline.core.job.service;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.data.pipeline.common.config.job.PipelineJobConfiguration;
import org.apache.shardingsphere.data.pipeline.common.config.process.PipelineProcessConfiguration;
import org.apache.shardingsphere.data.pipeline.common.config.process.PipelineProcessConfigurationUtils;
import org.apache.shardingsphere.data.pipeline.common.context.PipelineContextKey;
import org.apache.shardingsphere.data.pipeline.common.job.JobStatus;
import org.apache.shardingsphere.data.pipeline.common.job.progress.InventoryIncrementalJobItemProgress;
import org.apache.shardingsphere.data.pipeline.common.pojo.InventoryIncrementalJobItemInfo;
import org.apache.shardingsphere.data.pipeline.common.pojo.PipelineJobInfo;
import org.apache.shardingsphere.data.pipeline.core.job.PipelineJobIdUtils;
import org.apache.shardingsphere.data.pipeline.core.metadata.PipelineProcessConfigurationPersistService;
import org.apache.shardingsphere.elasticjob.infra.pojo.JobConfigurationPOJO;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Inventory incremental job manager.
 */
@RequiredArgsConstructor
public final class InventoryIncrementalJobManager {
    
    private final InventoryIncrementalJobAPI jobAPI;
    
    private final PipelineProcessConfigurationPersistService processConfigPersistService = new PipelineProcessConfigurationPersistService();
    
    /**
     * Alter process configuration.
     *
     * @param contextKey context key
     * @param processConfig process configuration
     */
    public void alterProcessConfiguration(final PipelineContextKey contextKey, final PipelineProcessConfiguration processConfig) {
        // TODO check rateLimiter type match or not
        processConfigPersistService.persist(contextKey, jobAPI.getType(), processConfig);
    }
    
    /**
     * Show process configuration.
     *
     * @param contextKey context key
     * @return process configuration, non-null
     */
    public PipelineProcessConfiguration showProcessConfiguration(final PipelineContextKey contextKey) {
        return PipelineProcessConfigurationUtils.convertWithDefaultValue(processConfigPersistService.load(contextKey, jobAPI.getType()));
    }
    
    /**
     * Get job infos.
     *
     * @param jobId job ID
     * @return job item infos
     */
    public List<InventoryIncrementalJobItemInfo> getJobItemInfos(final String jobId) {
        PipelineJobConfiguration jobConfig = new PipelineJobManager(jobAPI).getJobConfiguration(jobId);
        long startTimeMillis = Long.parseLong(Optional.ofNullable(PipelineJobIdUtils.getElasticJobConfigurationPOJO(jobId).getProps().getProperty("start_time_millis")).orElse("0"));
        Map<Integer, InventoryIncrementalJobItemProgress> jobProgress = getJobProgress(jobConfig);
        List<InventoryIncrementalJobItemInfo> result = new LinkedList<>();
        PipelineJobInfo jobInfo = jobAPI.getJobInfo(jobId);
        for (Entry<Integer, InventoryIncrementalJobItemProgress> entry : jobProgress.entrySet()) {
            int shardingItem = entry.getKey();
            InventoryIncrementalJobItemProgress jobItemProgress = entry.getValue();
            String errorMessage = new PipelineJobIteErrorMessageManager(jobId, shardingItem).getErrorMessage();
            if (null == jobItemProgress) {
                result.add(new InventoryIncrementalJobItemInfo(shardingItem, jobInfo.getTable(), null, startTimeMillis, 0, errorMessage));
                continue;
            }
            int inventoryFinishedPercentage = 0;
            if (JobStatus.EXECUTE_INCREMENTAL_TASK == jobItemProgress.getStatus() || JobStatus.FINISHED == jobItemProgress.getStatus()) {
                inventoryFinishedPercentage = 100;
            } else if (0 != jobItemProgress.getProcessedRecordsCount() && 0 != jobItemProgress.getInventoryRecordsCount()) {
                inventoryFinishedPercentage = (int) Math.min(100, jobItemProgress.getProcessedRecordsCount() * 100 / jobItemProgress.getInventoryRecordsCount());
            }
            result.add(new InventoryIncrementalJobItemInfo(shardingItem, jobInfo.getTable(), jobItemProgress, startTimeMillis, inventoryFinishedPercentage, errorMessage));
        }
        return result;
    }
    
    /**
     * Get job progress.
     *
     * @param jobConfig pipeline job configuration
     * @return each sharding item progress
     */
    public Map<Integer, InventoryIncrementalJobItemProgress> getJobProgress(final PipelineJobConfiguration jobConfig) {
        PipelineJobItemManager<InventoryIncrementalJobItemProgress> jobItemManager = new PipelineJobItemManager<>(jobAPI.getYamlJobItemProgressSwapper());
        String jobId = jobConfig.getJobId();
        JobConfigurationPOJO jobConfigPOJO = PipelineJobIdUtils.getElasticJobConfigurationPOJO(jobId);
        return IntStream.range(0, jobConfig.getJobShardingCount()).boxed().collect(LinkedHashMap::new, (map, each) -> {
            Optional<InventoryIncrementalJobItemProgress> jobItemProgress = jobItemManager.getProgress(jobId, each);
            jobItemProgress.ifPresent(optional -> optional.setActive(!jobConfigPOJO.isDisabled()));
            map.put(each, jobItemProgress.orElse(null));
        }, LinkedHashMap::putAll);
    }
}
