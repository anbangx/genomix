package edu.uci.ics.graphbuilding;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MiniMRCluster;
import org.junit.Test;

import edu.uci.ics.utils.TestUtils;

public class GraphBuildingTest {

    private static final String ACTUAL_RESULT_DIR = "actual";
    private JobConf conf = new JobConf();
    private static final String HADOOP_CONF_PATH = ACTUAL_RESULT_DIR + File.separator + "conf.xml";
    private static final String DATA_PATH = "data/webmap/text.txt";
    private static final String HDFS_PATH = "/webmap";
    private static final String RESULT_PATH = "/result2";
    private static final String DUMPED_RESULT = ACTUAL_RESULT_DIR + RESULT_PATH + "/part-00000";
    private static final String EXPECTED_PATH = "expected/result2";

    private MiniDFSCluster dfsCluster;
    private MiniMRCluster mrCluster;
    private FileSystem dfs;

    @Test
    public void test() throws Exception {
        FileUtils.forceMkdir(new File(ACTUAL_RESULT_DIR));
        FileUtils.cleanDirectory(new File(ACTUAL_RESULT_DIR));
        startHadoop();

        // run graph transformation tests
        GenomixDriver tldriver = new GenomixDriver();
        tldriver.run(HDFS_PATH, RESULT_PATH, 2, HADOOP_CONF_PATH);
        dumpResult();
        TestUtils.compareWithResult(new File(DUMPED_RESULT), new File(EXPECTED_PATH));

        cleanupHadoop();

    }

    private void startHadoop() throws IOException {
        FileSystem lfs = FileSystem.getLocal(new Configuration());
        lfs.delete(new Path("build"), true);
        System.setProperty("hadoop.log.dir", "logs");
        dfsCluster = new MiniDFSCluster(conf, 2, true, null);
        dfs = dfsCluster.getFileSystem();
        mrCluster = new MiniMRCluster(4, dfs.getUri().toString(), 2);

        Path src = new Path(DATA_PATH);
        Path dest = new Path(HDFS_PATH + "/");
        dfs.mkdirs(dest);
        dfs.copyFromLocalFile(src, dest);

        DataOutputStream confOutput = new DataOutputStream(new FileOutputStream(new File(HADOOP_CONF_PATH)));
        conf.writeXml(confOutput);
        confOutput.flush();
        confOutput.close();
    }

    private void cleanupHadoop() throws IOException {
        mrCluster.shutdown();
        dfsCluster.shutdown();
    }

    private void dumpResult() throws IOException {
        Path src = new Path(RESULT_PATH);
        Path dest = new Path(ACTUAL_RESULT_DIR + "/");
        dfs.copyToLocalFile(src, dest);
    }
}
