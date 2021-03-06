/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.genomix.pregelix.jobrun;

import junit.framework.TestCase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.junit.Test;

import edu.uci.ics.genomix.data.cluster.GenomixClusterManager;
import edu.uci.ics.genomix.data.config.GenomixJobConf;
import edu.uci.ics.genomix.data.utils.GenerateGraphViz;
import edu.uci.ics.genomix.data.utils.GenerateGraphViz.GRAPH_TYPE;
import edu.uci.ics.genomix.hadoop.utils.GraphStatistics;
import edu.uci.ics.pregelix.api.job.PregelixJob;
import edu.uci.ics.pregelix.api.util.BspUtils;
import edu.uci.ics.pregelix.core.base.IDriver.Plan;
import edu.uci.ics.pregelix.core.driver.Driver;
import edu.uci.ics.pregelix.core.util.PregelixHyracksIntegrationUtil;

public class BasicSmallTestCase extends TestCase {
    private final PregelixJob job;
    private final String resultFileDir;
    //    private final String textFileDir;
    private final String graphvizFile;
    private final String statisticsFileDir;
    private final String expectedFileDir;
    private final String jobFile;
    private final Driver driver = new Driver(this.getClass());
    private final FileSystem dfs;

    public BasicSmallTestCase(String hadoopConfPath, String jobName, String jobFile, FileSystem dfs, String hdfsInput,
            String resultFile, String graphvizFile, String statisticsFile, String expectedFile) throws Exception {
        super("test");
        this.jobFile = jobFile;
        this.job = new PregelixJob("test");
        this.job.getConfiguration().addResource(new Path(jobFile));
        this.job.getConfiguration().addResource(new Path(hadoopConfPath));
        FileInputFormat.setInputPaths(job, hdfsInput);
        FileOutputFormat.setOutputPath(job, new Path(hdfsInput + "_result"));
        job.setJobName(jobName);
        this.resultFileDir = resultFile;
        //        this.textFileDir = textFile;
        this.graphvizFile = graphvizFile;
        this.statisticsFileDir = statisticsFile;
        this.expectedFileDir = expectedFile;

        this.dfs = dfs;
    }

    private void waitawhile() throws InterruptedException {
        synchronized (this) {
            this.wait(20);
        }
    }

    @Test
    public void test() throws Exception {
        setUp();
        Plan[] plans = new Plan[] { Plan.OUTER_JOIN };
        for (Plan plan : plans) {
            driver.runJob(job, plan, PregelixHyracksIntegrationUtil.CC_HOST,
                    PregelixHyracksIntegrationUtil.TEST_HYRACKS_CC_CLIENT_PORT, false);
        }
        compareResults();
        tearDown();
        waitawhile();
    }

    private void compareResults() throws Exception {
        //copy bin and text to local
        System.out.println();
        GenomixClusterManager.copyBinAndTextToLocal((JobConf) job.getConfiguration(),
                FileOutputFormat.getOutputPath(job).toString(), resultFileDir);
        //covert bin to graphviz
        GenerateGraphViz
                .writeLocalBinToLocalSvg(resultFileDir, graphvizFile, GRAPH_TYPE.DIRECTED_GRAPH_WITH_ALLDETAILS);
        // compare results
        //        TestUtils.compareFilesBySortingThemLineByLine(new File(expectedFileDir), new File(resultFileDir
        //                + File.separator + "data")); 
        //generate statistic counters
        //        generateStatisticsResult(resultFileDir + File.separator + "stats.txt");
        //        drawStatistics(resultFileDir + File.separator + "stats");
        Counters newC = BspUtils.getCounters(job);
        org.apache.hadoop.mapred.Counters oldC = new org.apache.hadoop.mapred.Counters();
        for (String g : newC.getGroupNames()) {
            for (org.apache.hadoop.mapreduce.Counter c : newC.getGroup(g)) {
                oldC.findCounter(g, c.getName()).increment(c.getValue());
            }
        }
        GraphStatistics.saveGraphStats(resultFileDir + "stats", oldC, (GenomixJobConf)new Configuration());
        GraphStatistics.drawStatistics(resultFileDir + "stats", oldC, (GenomixJobConf)new Configuration());
    }

    public String toString() {
        return jobFile;
    }

}
