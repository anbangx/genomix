package edu.uci.ics.genomix.hadoop.contrailgraphbuilding;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.junit.Before;
import org.junit.Test;

import edu.uci.ics.genomix.hadoop.graph.GenerateGraphViz;
import edu.uci.ics.genomix.hadoop.pmcommon.HadoopMiniClusterTest;

@SuppressWarnings({ "deprecation", "unused" })
public class GraphBuildingTestCase extends TestCase{

    private final String RESULT_PATH;
    private final String HADOOP_CONF_PATH;
    private final String HDFS_INPUTPATH;
    private FileSystem dfs;
    private JobConf conf;

    private static final int COUNT_REDUCER = 1;
    private final int SIZE_KMER;
    private final int LINES_PERMAP;
        
    public GraphBuildingTestCase(String resultFileDir, String hadoopConfPath,
            String hdfsInputPath, int kmerSize, int linesPerMap,
            FileSystem dfs, JobConf conf){
        super("test");
        this.RESULT_PATH = resultFileDir;
        this.HADOOP_CONF_PATH = hadoopConfPath;
        this.HDFS_INPUTPATH = hdfsInputPath;
        this.SIZE_KMER = kmerSize;
        this.LINES_PERMAP = linesPerMap;
        this.dfs = dfs;
        this.conf = conf;
    }
    
    private void waitawhile() throws InterruptedException {
        synchronized (this) {
            this.wait(20);
        }
    }
    
    @Test
    public void test() throws Exception {
        setUp();
        TestMapKmerToNode();
        tearDown();
        waitawhile();
    }
    
    public void TestMapKmerToNode() throws Exception {
        GenomixDriver driver = new GenomixDriver();
        driver.run(HDFS_INPUTPATH, RESULT_PATH, COUNT_REDUCER, SIZE_KMER, LINES_PERMAP, true, HADOOP_CONF_PATH);
        dumpResult();
    }
    
    private void dumpResult() throws Exception {
//        Path src = new Path(RESULT_PATH);
//        Path dest = new Path(RESULT_PATH);
//        dfs.copyToLocalFile(src, dest);
        HadoopMiniClusterTest.copyResultsToLocal(RESULT_PATH, RESULT_PATH + "/", false, conf, true, dfs);
        GenerateGraphViz.convertGraphBuildingOutputToGraphViz(RESULT_PATH + "/bin", RESULT_PATH + "/graphviz");
    }
}