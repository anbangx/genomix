package edu.uci.ics.genomix.hadoop.graphclean.mergepaths.h3;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.junit.Test;

import edu.uci.ics.genomix.hadoop.pmcommon.GenomixMiniClusterTest;
import edu.uci.ics.genomix.hadoop.pmcommon.PathNodeInitial;
import edu.uci.ics.genomix.hyracks.driver.Driver.Plan;
import edu.uci.ics.genomix.hyracks.job.GenomixJobConf;

@SuppressWarnings("deprecation")
public class TestPathMergeH3 extends GenomixMiniClusterTest {
    protected String LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/bubble_test1.txt";
    protected String HDFS_SEQUENCE = "/00-sequence/";
    protected String HDFS_GRAPHBUILD = "/01-graphbuild/";
    protected String HDFS_MARKPATHS = "/02-pathmark/";
    protected String HDFS_MERGED = "/03-pathmerge/";
    
    protected String GRAPHBUILD_FILE = "graphbuild.result";
    protected String PATHMARKS_FILE = "markpaths.result";
    protected String PATHMERGE_FILE = "mergepath.result";
    protected boolean regenerateGraph = true;
    
    {
        KMER_LENGTH = 5;
        READ_LENGTH = 8;
        HDFS_PATHS = new ArrayList<String>(Arrays.asList(HDFS_SEQUENCE, HDFS_GRAPHBUILD, HDFS_MARKPATHS, HDFS_MERGED));
        conf.setInt(GenomixJobConf.KMER_LENGTH, KMER_LENGTH);
        conf.setInt(GenomixJobConf.READ_LENGTH, READ_LENGTH);
    }

    @Test
    public void TestBuildGraph() throws Exception {
    	LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/bubble_test1.txt";
    	GRAPHBUILD_FILE = "bubble_test1.txt";
        cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/fr_test.txt";
    	GRAPHBUILD_FILE = "fr_test.txt";
    	cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
    	
    	LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/fr_test2.txt";
    	GRAPHBUILD_FILE = "fr_test2.txt";
    	cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/rf_test.txt";
    	GRAPHBUILD_FILE = "rf_test.txt";
    	cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/singleread.txt";
    	GRAPHBUILD_FILE = "single_read.txt";
    	cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/text.txt";
    	GRAPHBUILD_FILE = "text.txt";
    	cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/tworeads.txt";
    	GRAPHBUILD_FILE = "tworeads.txt";
    	cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/tip_test1.txt";
        GRAPHBUILD_FILE = "tip_test1.txt";
        cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/fr_with_tip.txt";
        GRAPHBUILD_FILE = "fr_with_tipcon.txt";
        cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
        
        LOCAL_SEQUENCE_FILE = "src/test/resources/data/sequence/walk_random_seq1.txt";
        GRAPHBUILD_FILE = "walk_random_seq1.txt";
        cleanUpOutput();
        copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
        buildGraph();
    }

//    @Test
    public void TestMergeOneIteration() throws Exception {
        cleanUpOutput();
        if (regenerateGraph) {
            copyLocalToDFS(LOCAL_SEQUENCE_FILE, HDFS_SEQUENCE);
            buildGraph();
            copyLocalToDFS(ACTUAL_ROOT + GRAPHBUILD_FILE + ".binmerge", HDFS_GRAPHBUILD);
        } else {
            copyLocalToDFS(EXPECTED_ROOT + GRAPHBUILD_FILE + ".binmerge", HDFS_GRAPHBUILD);
        }
        
//        PathNodeInitial inith3 = new PathNodeInitial();
//        inith3.run(HDFS_GRAPHBUILD, HDFS_MARKPATHS + "toMerge", HDFS_MARKPATHS + "complete", conf);
//        copyResultsToLocal(HDFS_MARKPATHS + "toMerge", ACTUAL_ROOT + PATHMARKS_FILE, false, conf);
//        copyResultsToLocal(HDFS_MARKPATHS + "complete", ACTUAL_ROOT + PATHMARKS_FILE, false, conf);
//
//        MergePathsH3Driver h3 = new MergePathsH3Driver();
//        h3.run(HDFS_MARKPATHS + "toMerge", HDFS_MERGED, 2, KMER_LENGTH, 1, conf);
//        copyResultsToLocal(HDFS_MERGED, ACTUAL_ROOT + PATHMERGE_FILE, false, conf);
    }



    public void buildGraph() throws Exception {
        JobConf buildConf = new JobConf(conf);  // use a separate conf so we don't interfere with other jobs 
        FileInputFormat.setInputPaths(buildConf, HDFS_SEQUENCE);
        FileOutputFormat.setOutputPath(buildConf, new Path(HDFS_GRAPHBUILD));
        buildConf.set(GenomixJobConf.OUTPUT_FORMAT, GenomixJobConf.OUTPUT_FORMAT_BINARY);
        buildConf.set(GenomixJobConf.GROUPBY_TYPE, GenomixJobConf.GROUPBY_TYPE_PRECLUSTER);
        driver.runJob(new GenomixJobConf(buildConf), Plan.BUILD_DEBRUJIN_GRAPH, true);
        String fileFormat = buildConf.get(GenomixJobConf.OUTPUT_FORMAT);
        boolean resultsAreText = GenomixJobConf.OUTPUT_FORMAT_TEXT.equalsIgnoreCase(fileFormat);
        copyResultsToLocal(HDFS_GRAPHBUILD, ACTUAL_ROOT + GRAPHBUILD_FILE, resultsAreText, buildConf);
    }
}
