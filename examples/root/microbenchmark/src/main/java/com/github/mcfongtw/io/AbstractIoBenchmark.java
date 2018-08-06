package com.github.mcfongtw.io;

import com.codahale.metrics.ScheduledReporter;
import com.github.mcfongtw.utils.SudoExecutors;
import org.apache.commons.lang3.StringUtils;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public abstract class AbstractIoBenchmark {

    protected static final int NUM_ITERATION = 100;

    protected static ScheduledReporter metricReporter = InfluxdbReporterSingleton.newInstance();

    @State(Scope.Benchmark)
    protected static abstract class AbstractExecutionPlan {
        protected Logger logger = LoggerFactory.getLogger(this.getClass());

        private volatile  boolean enableReporter = false;

        public AbstractExecutionPlan() {
            String propVal = System.getProperty("enableReporter");
            if(StringUtils.isNotEmpty(propVal)) {
                enableReporter = Boolean.parseBoolean(propVal);
            }

            logger.info("Metric Reporter Status: [{}]", enableReporter);

            if(enableReporter) {
                //TODO: Read reporting interval from configuration
                metricReporter.start(1, TimeUnit.SECONDS);

                logger.info("Starting reporting metric");
            }
        }


        @Override
        protected void finalize() {
            if(enableReporter) {
                metricReporter.stop();
            }
        }

        @Setup(Level.Trial)
        public void setUp() throws IOException {

        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException, InterruptedException {
            logger.info("Start to dropping free pagecache, dentries and inodes...");
            SudoExecutors.exec("echo 3 > /proc/sys/vm/drop_caches", "Love_0502");
            logger.info("Start to dropping free pagecache, dentries and inodes...DONE");
        }


    }



}