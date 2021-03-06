package com.github.mcfongtw.io;

import com.codahale.metrics.ScheduledReporter;
import com.github.mcfongtw.AbstractBenchmarkLifecycle;
import com.github.mcfongtw.BenchmarkBase;
import com.github.mcfongtw.utils.SudoExecutors;
import com.google.common.io.Files;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.github.mcfongtw.io.AbstractIoBenchmarkBase.AbstractRandomAccessIoBenchmarkLifecycle.DataType.*;

public abstract class AbstractIoBenchmarkBase extends BenchmarkBase {

    protected static final int SLEEP_TIME_BETWEEN_TRIALS_IN_MILLIS = 30 * 1000;

    protected static final int UNIT_ONE_KILO = 1024;

    protected static final int UNIT_ONE_MEGA = UNIT_ONE_KILO * UNIT_ONE_KILO;

    protected static final int UNIT_ONE_GIGA = UNIT_ONE_KILO * UNIT_ONE_MEGA;

    protected static final int UNIT_ONE_PAGE = 4 * UNIT_ONE_KILO;

    protected static ScheduledReporter metricReporter = InfluxdbReporterSingleton.newInstance();

    protected static abstract class AbstractIoBenchmarkLifecycle extends AbstractBenchmarkLifecycle {
        protected Logger logger = LoggerFactory.getLogger(this.getClass());

        private String sudoPassword = "";

        private boolean isMetricReporterEnabled = false;


        public AbstractIoBenchmarkLifecycle() {
            isMetricReporterEnabled = Boolean.valueOf(System.getProperty("isMetricReporterEnabled", "false"));

            if(isMetricReporterEnabled) {
                logger.info("Starting reporting metric...");
                metricReporter.start(500, TimeUnit.MILLISECONDS);
                logger.info("Starting reporting metric...DONE");
            }

            String sudoPasswordVal = System.getProperty("sudoPassword");
            if(StringUtils.isNotEmpty(sudoPasswordVal)) {
                sudoPassword = sudoPasswordVal;
            }
            logger.debug("SudoPassword: {}", sudoPassword);
        }

        private void persistCacheToStorage() throws IOException, InterruptedException {
            if(StringUtils.isNotEmpty(sudoPassword)) {
                logger.debug("Start to sync page cache to disk...");
                SudoExecutors.exec("sync", sudoPassword);
                logger.debug("Start to sync page cache to disk...DONE");
            }
        }

        private void flushSystemCache() throws IOException, InterruptedException {
            if(StringUtils.isNotEmpty(sudoPassword)) {
                logger.debug("Start to drop free pagecache, dentries and inodes...");
                SudoExecutors.exec("echo 3 > /proc/sys/vm/drop_caches", sudoPassword);
                logger.debug("Start to drop free pagecache, dentries and inodes...DONE");
            }
        }


        @Override
        public void postTrialTearDown() throws Exception {
            super.postTrialTearDown();
            logger.info("Stopping reporting metric...");
            if(isMetricReporterEnabled) {
                metricReporter.stop();
                Thread.sleep(SLEEP_TIME_BETWEEN_TRIALS_IN_MILLIS);
            }
            logger.info("Stopping reporting metric...DONE");
        }


        @Override
        public void postIterationTearDown()  throws Exception {
            super.postIterationTearDown();
            persistCacheToStorage();
            flushSystemCache();
        }

    }


    @Getter
    protected static abstract class AbstractSequentialIoBenchmarkLifecycle extends AbstractIoBenchmarkLifecycle {

        protected String finPath;

        protected String foutPath;

        protected File tempDir;

        private static final int TOTAL_DATA_WRITTEN = 32 * UNIT_ONE_MEGA;

        @Override
        public void preTrialSetUp() throws Exception {
            super.preTrialSetUp();

            tempDir = Files.createTempDir();
            new File(tempDir.getAbsolutePath()).mkdirs();

            finPath = tempDir.getAbsolutePath() + "/in.data";
            foutPath = tempDir.getAbsolutePath() + "/out.data";

            FileUtils.touch(new File(finPath));
            FileUtils.touch(new File(foutPath));

            //Sequential data generation
            try(
                    FileOutputStream fin = new FileOutputStream(finPath);
            ) {
                for(int i = 0; i < TOTAL_DATA_WRITTEN; i++) {
                    fin.write((byte) i);
                }
            }

            logger.debug("Temp dir created at [{}]", tempDir.getAbsolutePath());
            logger.debug("File created at [{}]", finPath);
            logger.debug("File created at [{}]", foutPath);
        }

        @Override
        public void postTrialTearDown() throws Exception {
            super.postTrialTearDown();

            FileUtils.deleteDirectory(tempDir);
            logger.debug("Temp dir deleted at [{}]", tempDir.getAbsolutePath());
        }
    }

    @Getter
    protected static abstract class AbstractReplicationIoBenchmarkLifecycle extends AbstractIoBenchmarkLifecycle {

        protected String finPath;

        protected String foutPath;

        protected File tempDir;

        protected int fileSize;

        @Override
        public void preTrialSetUp() throws Exception {
            super.preTrialSetUp();

            tempDir = Files.createTempDir();
            new File(tempDir.getAbsolutePath()).mkdirs();

            finPath = tempDir.getAbsolutePath() + "/in.data";
            foutPath = tempDir.getAbsolutePath() + "/out.data";

            FileUtils.touch(new File(finPath));
            FileUtils.touch(new File(foutPath));

            //Sequential data generation
            try(
                    FileOutputStream fin = new FileOutputStream(finPath);
            ) {
                for(int i = 0; i < fileSize; i++) {
                    fin.write((byte) i);
                }
                logger.debug("File [{}] generated with size [{}]", finPath, fin.getChannel().size());

                assert fileSize != 0 && fin.getChannel().size() == fileSize;
            }

            logger.debug("Temp dir created at [{}]", tempDir.getAbsolutePath());
            logger.debug("File created at [{}]", finPath);
            logger.debug("File created at [{}]", foutPath);
        }

        @Override
        public void postTrialTearDown() throws Exception {
            super.postTrialTearDown();

            FileUtils.deleteDirectory(tempDir);
            logger.debug("Temp dir deleted at [{}]", tempDir.getAbsolutePath());
        }
    }

    @Getter
    public static class AbstractRandomAccessIoBenchmarkLifecycle extends AbstractIoBenchmarkLifecycle {
        private static final int TOTAL_DATA_WRITTEN = 32 * UNIT_ONE_MEGA;

        protected String fmetaPath;

        protected String fsummaryPath;

        protected String finPath;

        protected List<String> listOfFoutPath = new ArrayList<>();

        protected File tempDir;

        public enum DataType {
            INTEGER(1, 4),
            DOUBLE(2, 8),
            CHAR(3, 2),
            BYTE(4, 1);

            private int type;

            private int sizeOf;

            DataType(int id, int size) {
                type = id;
                this.sizeOf = size;
            }

            public int getSizeOf() {
                return sizeOf;
            }


            public static DataType getDataTypeByTypeId(int type) {
                switch(type) {
                    case 1:
                        return INTEGER;
                    case 2:
                        return DOUBLE;
                    case 3:
                        return CHAR;
                    case 4:
                        return BYTE;
                    default:
                        return null;
                }
            }

            public static DataType getDataTypeBySizeOf(int size) {
                switch(size) {
                    case 1:
                        return BYTE;
                    case 2:
                        return CHAR;
                    case 4:
                        return INTEGER;
                    case 8:
                        return DOUBLE;
                    default:
                        //return smallest data type - byte
                        return BYTE;
                }
            }

            public static int COUNT = DataType.values().length;
        }

        @Override
        public void preTrialSetUp() throws Exception {
            super.preTrialSetUp();

            Random rand = new Random(System.currentTimeMillis());

            tempDir = Files.createTempDir();
            new File(tempDir.getAbsolutePath()).mkdirs();

            finPath = tempDir.getAbsolutePath() + "/in.data";
            fmetaPath = tempDir.getAbsolutePath() + "/meta.data";
            fsummaryPath = tempDir.getAbsolutePath() + "/summary.data";

            FileUtils.touch(new File(finPath));
            FileUtils.touch(new File(fmetaPath));

            try (
                    PrintWriter fmeta = new PrintWriter(new FileOutputStream(fmetaPath));
                    PrintWriter fsummary = new PrintWriter(new FileOutputStream(fsummaryPath));
                    FileChannel channel = new RandomAccessFile(finPath, "rw").getChannel();
            ) {
                int foutFileSize[] = new int[COUNT];

                ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, TOTAL_DATA_WRITTEN);

                for(int currentIndex = 0; currentIndex < TOTAL_DATA_WRITTEN; ) {
                    DataType nextDataType = getDataTypeByTypeId(rand.nextInt(3) + 1);
                    int nextDataTypeLength = rand.nextInt(10) + 1;

                    if(nextDataTypeLength * nextDataType.getSizeOf() + currentIndex >= TOTAL_DATA_WRITTEN) {
                        int remainingByte = TOTAL_DATA_WRITTEN - currentIndex;
                        logger.debug("Remaining {} bytes in file: [{}]", remainingByte, finPath);

                        //NOTE: get the next affordable data type
                        nextDataType = DataType.BYTE;
                        nextDataTypeLength = remainingByte;
                    }

                    logger.debug("[{}] | [{}] |\t [{}] |\t [{}]", new Object[]{finPath, DataType.getDataTypeByTypeId(nextDataType.type), currentIndex, nextDataTypeLength});
                    fmeta.println(finPath + "," + nextDataType.type + "," + currentIndex + "," + nextDataTypeLength);

                    for(int i = 0; i < nextDataTypeLength; i++) {
                        switch (nextDataType) {
                            case INTEGER:
                                buffer = buffer.putInt(currentIndex, rand.nextInt());
                                foutFileSize[nextDataType.ordinal()] += nextDataType.sizeOf;
                                break;
                            case DOUBLE:
                                buffer = buffer.putDouble(currentIndex, rand.nextDouble());
                                foutFileSize[nextDataType.ordinal()] += nextDataType.sizeOf;
                                break;
                            case CHAR:
                                buffer = buffer.putChar(currentIndex, (char) (rand.nextInt(26) + 'a'));
                                foutFileSize[nextDataType.ordinal()] += nextDataType.sizeOf;
                                break;
                            case BYTE:
                                buffer = buffer.put(currentIndex, (byte) rand.nextInt());
                                foutFileSize[nextDataType.ordinal()] += nextDataType.sizeOf;
                                break;
                            default:
                                break;
                        }
                        currentIndex += nextDataType.sizeOf;
                    }


                }

                buffer.flip();

                // Write summary file
                for (int i = 0; i < foutFileSize.length; i++) {
                    int size = foutFileSize[i];
                    fsummary.println(size);
                }
            }

            for(int i = 0; i < DataType.COUNT; i++) {
                String foutPath = tempDir.getAbsolutePath() + "/out-" + DataType.getDataTypeByTypeId(i+1).name() + ".data";
                FileUtils.touch(new File(foutPath));
                logger.debug("File created at [{}]", foutPath);
                listOfFoutPath.add(foutPath);
            }

            logger.debug("Temp dir created at [{}]", tempDir.getAbsolutePath());
            logger.debug("File created at [{}]", finPath);
            logger.debug("File created at [{}]", fmetaPath);
        }

        @Override
        public void postTrialTearDown() throws Exception {
            super.postTrialTearDown();

            FileUtils.deleteDirectory(tempDir);
            logger.debug("Temp dir deleted at [{}]", tempDir.getAbsolutePath());
        }
    }

    @Getter
    protected static abstract class AbstractPagingIoBenchmarkLifecycle extends AbstractIoBenchmarkLifecycle {

        protected String finPath;

        protected String foutPath;

        protected File tempDir;

        protected int fileSize;

        protected List<String> listOfFinPath = new ArrayList<>();

        protected List<String> listOfFoutPath = new ArrayList<>();

        public final static int MAX_NUM_FILES = 100;

        public final static int BUFFER_SIZE = UNIT_ONE_PAGE;

        @Override
        public void preTrialSetUp() throws Exception {
            super.preTrialSetUp();

            tempDir = Files.createTempDir();
            new File(tempDir.getAbsolutePath()).mkdirs();

            for(int index = 0; index < MAX_NUM_FILES; index ++) {
                String finPath = String.format(tempDir.getAbsolutePath() + "/in-%d.data", index);
                String foutPath = String.format(tempDir.getAbsolutePath() + "/out-%d.data", index);
                listOfFinPath.add(finPath);
                listOfFoutPath.add(foutPath);

                FileUtils.touch(new File(finPath));
                FileUtils.touch(new File(foutPath));

                //Sequential data generation
                try(
                        FileOutputStream fin = new FileOutputStream(finPath);
                ) {
                    for(int i = 0; i < fileSize; i++) {
                        fin.write((byte) i);
                    }

                    logger.debug("File [{}] generated with size [{}]", finPath, fin.getChannel().size());

                    assert fileSize != 0 && fin.getChannel().size() == fileSize;
                }

                logger.debug("Temp dir created at [{}]", tempDir.getAbsolutePath());
                logger.debug("File created at [{}]", finPath);
                logger.debug("File created at [{}]", foutPath);
            }
        }

        @Override
        public void postTrialTearDown() throws Exception {
            super.postTrialTearDown();

            FileUtils.deleteDirectory(tempDir);
            logger.debug("Temp dir deleted at [{}]", tempDir.getAbsolutePath());
        }
    }

}
