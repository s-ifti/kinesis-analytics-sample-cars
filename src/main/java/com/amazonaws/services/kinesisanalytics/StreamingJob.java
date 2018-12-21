/*
 * Flink Car Average Speed Example 
 * The app reads stream of car events and calculate average speed within last 30 seconds window.
 */

package com.amazonaws.services.kinesisanalytics;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.kinesisanalytics.util.CloudwatchMetricSink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisProducer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.api.common.time.Time;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.apache.flink.table.api.StreamTableEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.flink.table.api.TableEnvironment;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;

import org.apache.flink.api.common.functions.FilterFunction;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
//import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisProducer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * A sample flink stream processing job.
 *
 * The app reads stream of car events and calculate average speed within last 30 seconds window.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {

    private static Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static String VERSION = "1.0.2";
    private static String DEFAULT_REGION = "us-east-1";
    private static int DEFAULT_PARALLELISM = 4;

    private static Properties appProperties = null;

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        LOG.info("Starting Kinesis Analytics Cars Sample - Calc Average Speed App Version " + VERSION);

        appProperties = getRuntimeConfigProperties();

        // use a specific input stream name
        String streamName = getAppProperty("inputStreamName", "");

        if(StringUtils.isBlank(streamName)) {
            LOG.error("inputStreamName should be pass using CarProperties config within create-application API call");
            throw new Exception("inputStreamName should be pass using CarProperties config within create-application API call, aborting ..." );
        }

        // use a specific input stream name
        String region = getAppProperty("region", DEFAULT_REGION);

        int parallelism = getAppPropertyInt( "parallelism", DEFAULT_PARALLELISM);

        String metricTag = getAppProperty("metricTag", "None");


        LOG.info("Starting Kinesis Analytics Cars Sample using parallelism {} " +
                " stream {} region {} metricTag {} ",
                parallelism, streamName, region, metricTag);

        final ParameterTool params = ParameterTool.fromArgs(args);

        // Enable checkpointing
        env.enableCheckpointing(TimeUnit.MINUTES.toMillis(5L));
        StateBackend stateBackend = env.getStateBackend();
        // advanced options:
        // set mode to exactly-once (this is the default)
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        // make sure 500 ms of progress happen between checkpoints
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        // checkpoints have to complete within one minute, or are discarded
        env.getCheckpointConfig().setCheckpointTimeout(60000);
        // allow only one checkpoint to be in progress at the same time
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        // enable externalized checkpoints
        env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);


        env.getConfig().setGlobalJobParameters(params);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        env.setParallelism(parallelism);

        // Add kinesis as source
        Properties consumerConfig = new Properties();
        consumerConfig.put(ConsumerConfigConstants.AWS_REGION, region);
        consumerConfig.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");

        DataStream<String> inputStream = env.addSource(new FlinkKinesisConsumer<>(
                streamName, new SimpleStringSchema(), consumerConfig))
                .name("kinesis");



        // an example Car stream processing job graph
        DataStream<Tuple2<Boolean,Double>> sampleSpeed =
                //start with inputStream
                inputStream
                //process JSON and return a model POJO class
                .map(c -> {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readValue(c, JsonNode.class);
                    Timestamp timestamp = null;
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
                        Date parsedDate = dateFormat.parse(jsonNode.get("dataTimestamp").asText());
                        timestamp = new java.sql.Timestamp(parsedDate.getTime());
                    } catch (Exception e) {
                        LOG.error("Error processing timestamp " + e.toString());
                    }
                    return new Car(
                            jsonNode.get("vehicleId").asText(),
                            timestamp,
                            jsonNode.get("hasMoonRoof").asText().equals("true"),
                            jsonNode.get("speed").asDouble()
                    );

                }).
                returns(Car.class)
                .name("map_Car")
                //assign timestamp for time window processing
                .assignTimestampsAndWatermarks(new TimeLagWatermarkGenerator())
                .name("timestamp")
                //create tuple of moonroof flag and speed
                //log input car object
                .map(event -> {
                            LOG.info("Car: " + event.toString());
                            return new Tuple2<>(event.getMoonRoof(), event.getSpeed());
                        }
                ).returns(TypeInformation.of(new TypeHint<Tuple2<Boolean, Double>>() {
                }))
                .name("map_Speed");

        DataStream<Stats> avgProcessing = sampleSpeed
                .timeWindowAll(org.apache.flink.streaming.api.windowing.time.Time.seconds(30))
                //calc statsfor last 30 seconds window
                .aggregate(new StatsAggregate(), new MyProcessWindowFunction())
                .name("avg_30Sec_Speed")
                .map(stats -> {
                    LOG.info("avg Speed: " + stats.getAvg());
                    return stats;
                })
                .name("map_logSpeed");

                avgProcessing
                        .map(stats -> stats.getAvg())
                        .addSink(new CloudwatchMetricSink<Double>("MyCars-" + metricTag, "AvgSpeed") )
                        .name("cloudwatch_AvgSpeed_Sink");
                avgProcessing
                        .map(stats -> stats.getCount())
                        .addSink(new CloudwatchMetricSink<Double>("MyCars-" + metricTag, "SampleCount") )
                        .name("cloudwatch_SampleCounts_Sink");
                avgProcessing.print()

                .name("stdout");


        env.execute();
    }


    private static String getAppProperty(String name, final String defaultValue) {
        String value = defaultValue;
        if (appProperties != null) {
            value = appProperties.getProperty(name);
            value = StringUtils.isBlank(value)? defaultValue : value;
        }
        return value;
    }

    private static int getAppPropertyInt(String name, final int defaultIntValue) {
        String value = getAppProperty(name, "" + defaultIntValue);
        try {
            return Integer.parseInt(value);
        }
        catch(NumberFormatException e) {
            LOG.error("invalid string value {} given for property {} using default value ", value, name);
            return defaultIntValue;
        }
    }

    // helper method to return runtime properties for Property Group CarProperties
    public static Properties getRuntimeConfigProperties() {
        try {
            Map<String, Properties> runConfigurations = KinesisAnalyticsRuntime.getApplicationProperties();
            return (Properties) runConfigurations.get("CarProperties");
        } catch (IOException var1) {
            LOG.error("Could not retrieve the runtime config properties for {}, exception {}", "CarProperties", var1);
            return null;
        }
    }


    // Helper Function definitions for time window processing

    /**
     * The Stats accumulator is used to keep a running sum and a count.
     */
    private static class StatsAggregate
            implements AggregateFunction<Tuple2<Boolean, Double>, Stats, Stats> {
        @Override
        public Stats createAccumulator() {
            return new Stats(0.0, 0.0, 0.0, 0.0);
        }

        @Override
        public Stats add(Tuple2<Boolean, Double> value, Stats accumulator) {
            return new Stats(
                    Math.min(accumulator.getMin(), value.f1) ,
                    Math.max(accumulator.getMax(), value.f1),
                    accumulator.getCount() + 1L,
                    accumulator.getSum() + value.f1
            );
        }

        @Override
        public Stats getResult(Stats accumulator) {
            return accumulator;
        }

        @Override
        public Stats merge(Stats a, Stats b) {
            return new Stats(
                    Math.min(a.getMin(), b.getMin()) ,
                    Math.max(a.getMax(), b.getMax()),
                    a.getCount() + b.getCount(),
                    a.getSum() + b.getSum()
            );
        }
    }

    private static class MyProcessWindowFunction
            extends ProcessAllWindowFunction<Stats, Stats, TimeWindow> {

        public void process(
                Context context,
                Iterable<Stats> averages,
                Collector<Stats> out) {
            Stats average = averages.iterator().next();
            out.collect(average);
        }
    }

    // for generating timestamp and watermark, required for using any time Window processing
    public static class TimeLagWatermarkGenerator implements AssignerWithPeriodicWatermarks<Car> {

        private final long maxTimeLag = 5000; // 5 seconds

        @Override
        public long extractTimestamp(Car car, long previousElementTimestamp) {
            return car.getTimestamp().toInstant().toEpochMilli();
        }

        @Override
        public Watermark getCurrentWatermark() {
            // return the watermark as current time minus the maximum time lag
            return new Watermark(System.currentTimeMillis() - maxTimeLag);
        }
    }
}
