/*
 * Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may
 * not use this file except in compliance with the License. A copy of the
 * License is located at
 *
 *    http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.services.kinesisanalytics.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.kinesisanalytics.StreamingJob;
import com.google.common.base.Supplier;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

/**
 * A Cloudwatch metric sink, use this to write any numeric value as a metric to cloudwatch !
 * e.g. for Car sample we use it to send average of car speed seen within input stream as a metric
 */
public class CloudwatchMetricSink<T> extends RichSinkFunction<T> implements CheckpointedFunction {
    private static Logger LOG = LoggerFactory.getLogger(CloudwatchMetricSink.class);

    private final String metricName;
    private final String flinkEventCategory;

    private long lastBufferFlush;
    private List<String> values;
    private int batchSize;
    private long maxBufferTime;

    private transient AmazonCloudWatch cw;


    public CloudwatchMetricSink(String flinkEventCategory, String metricName) {

        this.flinkEventCategory = flinkEventCategory;
        this.metricName = metricName;

        this.lastBufferFlush = System.currentTimeMillis();
        this.batchSize = 10;
        this.maxBufferTime = 10000;
        this.values = new ArrayList<String>();


    }

    private void flushValuesBuffer()  {
        //send all data to cloudwatch
        Dimension dimension = new Dimension()
                .withName("Events")
                .withValue(flinkEventCategory);

        values.forEach(v-> {
            MetricDatum datum = new MetricDatum()
                    .withMetricName(metricName)
                    .withUnit(StandardUnit.None)
                    .withValue(Double.parseDouble(v))
                    .withDimensions(dimension);

            PutMetricDataRequest request = new PutMetricDataRequest()
                    .withNamespace("MyKinesisAnalytics/CarAvgSpeed")
                    .withMetricData(datum);
            PutMetricDataResult response = cw.putMetricData(request);
        });

        values.clear();
        lastBufferFlush = System.currentTimeMillis();
    }
    @Override
    public void invoke(T document)  {

        values.add(document.toString());

        if (values.size() >= batchSize || System.currentTimeMillis() - lastBufferFlush >= maxBufferTime) {
            try {
                flushValuesBuffer();
            } catch (Exception e) {
                //if the request fails, that's fine, just retry on the next invocation
                LOG.error("Issue flushing buffer : " + e.toString());
            }
        }
    }



    @Override
    public void open(Configuration configuration) throws Exception{
        super.open(configuration);
        // following class was not found due to POM shading when running in kubernetes (i.e. not in EMR)
        // changing to use AWSCredentialsProviderChain
        //final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
        LOG.warn("IN OPEN CLOUDWATCH METRIC SINK " + this.metricName);

        this.lastBufferFlush = System.currentTimeMillis();
        this.batchSize = 10;
        this.maxBufferTime = 5000;
        this.values = new ArrayList<String>();

        final AWSCredentialsProvider credentialsProvider  = new AWSCredentialsProviderChain(
                new SystemPropertiesCredentialsProvider(),
                new EnvironmentVariableCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new InstanceProfileCredentialsProvider()
        );
        cw = AmazonCloudWatchClientBuilder.standard().withCredentials(credentialsProvider)
                .build();

    }


    @Override
    public void snapshotState(FunctionSnapshotContext functionSnapshotContext) throws Exception {
        do {
            try {
                flushValuesBuffer();
            } catch (Exception e) {
                //if the request fails, that's fine, just retry on the next iteration
               LOG.error("snapshotState Issue flushing buffer : " + e.toString());

            }
        } while (! values.isEmpty());
    }


    @Override
    public void initializeState(FunctionInitializationContext functionInitializationContext) throws Exception {
        //nothing to initialize, as in flight values are completely flushed during checkpointing
    }
}
