/*
 * Copyright 2009-2013 by The Regents of the University of California
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

package edu.uci.ics.genomix.hyracks.graph.driver;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
//import org.apache.hadoop.mapred.lib.NLineInputFormat;
import org.jfree.util.Log;

import edu.uci.ics.genomix.config.GenomixJobConf;
import edu.uci.ics.genomix.hyracks.graph.job.JobGen;
import edu.uci.ics.genomix.hyracks.graph.job.JobGenBuildBrujinGraph;
import edu.uci.ics.genomix.hyracks.graph.job.JobGenReadLetterParser;
import edu.uci.ics.hyracks.api.client.HyracksConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.client.NodeControllerInfo;
import edu.uci.ics.hyracks.api.deployment.DeploymentId;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.hdfs.scheduler.Scheduler;

public class GenomixHyracksDriver {
    public enum Plan {
        /**
         * Build the deBruijin graph from original readID file to the final graph binary file
         */
        BUILD_DEBRUIJN_GRAPH,
        /**
         * Parser the original readID into kmer + node text file only. Used to check the intermediate result.
         */
        BUILD_READ_PARSER,
    }

    private static final Logger LOG = Logger.getLogger(GenomixHyracksDriver.class.getName());
    private JobGen jobGen;
    private boolean profiling;

    private int numPartitionPerMachine;

    private IHyracksClientConnection hcc;
    private Scheduler scheduler;

    public GenomixHyracksDriver(String ipAddress, int port, int numPartitionPerMachine) throws HyracksException {
        try {
            hcc = new HyracksConnection(ipAddress, port);
            scheduler = new Scheduler(hcc.getNodeControllerInfos());
        } catch (Exception e) {
            throw new HyracksException(e);
        }
        this.numPartitionPerMachine = numPartitionPerMachine;
    }

    public void runJob(GenomixJobConf job) throws HyracksException {
        runJob(job, Plan.BUILD_DEBRUIJN_GRAPH, false);
    }

    public void runJob(GenomixJobConf job, Plan planChoice, boolean profiling) throws HyracksException {
        /** add hadoop configurations */
        URL hadoopCore = job.getClass().getClassLoader().getResource("core-site.xml");
        if (hadoopCore != null)
            LOG.info("hadoopCore URL:  " + hadoopCore.toString());
        job.addResource(hadoopCore);
        URL hadoopMapRed = job.getClass().getClassLoader().getResource("mapred-site.xml");
        if (hadoopMapRed != null)
            LOG.info("hadoopMapRed URL:  " + hadoopMapRed.toString());
        job.addResource(hadoopMapRed);
        URL hadoopHdfs = job.getClass().getClassLoader().getResource("hdfs-site.xml");
        if (hadoopHdfs != null)
            LOG.info("hadoopHdfs URL:  " + hadoopHdfs.toString());
        job.addResource(hadoopHdfs);

        // TODO make hyracks works on this linespermap
        //        job.setInt("mapred.line.input.format.linespermap", 2000000); // must be a multiple of 4
        //        job.setInputFormat(NLineInputFormat.class);

        LOG.info("job started");
        long start = System.currentTimeMillis();
        long end = start;
        long time = 0;

        this.profiling = profiling;
        try {
            Map<String, NodeControllerInfo> ncMap = hcc.getNodeControllerInfos();
            LOG.info("ncmap:" + ncMap.size() + " " + ncMap.keySet().toString());
            if (ncMap.size() == 0) {
                throw new IllegalStateException("No registered worker NC's to build the graph!");
            }
            switch (planChoice) {
                case BUILD_DEBRUIJN_GRAPH:
                    jobGen = new JobGenBuildBrujinGraph(job, scheduler, ncMap, numPartitionPerMachine);
                    break;
                case BUILD_READ_PARSER:
                    jobGen = new JobGenReadLetterParser(job, scheduler, ncMap, numPartitionPerMachine);
                    break;
                default:
                    throw new IllegalArgumentException("Unrecognized planChoice: " + planChoice);
            }

            start = System.currentTimeMillis();
            run(jobGen);
            end = System.currentTimeMillis();
            time = end - start;
            LOG.info("result writing finished " + time + "ms");
            LOG.info("job finished");
        } catch (Exception e) {
            throw new HyracksException(e);
        }
    }

    private void run(JobGen jobGen) throws Exception {
        try {
            JobSpecification createJob = jobGen.generateJob();
            execute(createJob);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private DeploymentId prepareJobs() throws Exception {
        URLClassLoader classLoader = (URLClassLoader) this.getClass().getClassLoader();
        List<String> jars = new ArrayList<String>();
        LOG.info("Deploying jar files to NC's");
        URL[] urls = classLoader.getURLs();
        for (URL url : urls)
            if (url.toString().endsWith(".jar")) {
                jars.add(new File(url.getPath()).toString());
            }
        LOG.info("Finished jar deployment");
        DeploymentId deploymentId = hcc.deployBinary(jars);
        return deploymentId;
    }

    private void execute(JobSpecification job) throws Exception {
        job.setUseConnectorPolicyForScheduling(false);
        DeploymentId deployId = prepareJobs();
        JobId jobId = hcc.startJob(deployId, job,
                profiling ? EnumSet.of(JobFlag.PROFILE_RUNTIME) : EnumSet.noneOf(JobFlag.class));
        hcc.waitForCompletion(jobId);
    }

    // Keep this main function for debug or test usage.
    public static void main(String[] args) throws Exception {
        //String[] myArgs = { "-hdfsInput", "/home/nanz1/TestData", "-hdfsOutput", "/home/hadoop/pairoutput",  
        // "-kmerLength", "55", "-ip", "128.195.14.113", "-port", "3099", "-hyracksBuildOutputText", "true"};
        GenomixJobConf jobConf = GenomixJobConf.fromArguments(args);
        String ipAddress = jobConf.get(GenomixJobConf.IP_ADDRESS);
        int port = Integer.parseInt(jobConf.get(GenomixJobConf.PORT));
        String IODirs = jobConf.get(GenomixJobConf.HYRACKS_IO_DIRS, null);
        int numOfDuplicate = IODirs != null ? IODirs.split(",").length : 4;
        boolean bProfiling = jobConf.getBoolean(GenomixJobConf.PROFILE, true);

        Log.info("Input dir:" + GenomixJobConf.INITIAL_INPUT_DIR);
        Log.info("Output dir:" + GenomixJobConf.FINAL_OUTPUT_DIR);
        FileInputFormat.setInputPaths(jobConf, new Path(jobConf.get(GenomixJobConf.INITIAL_INPUT_DIR)));
        {
            Path path = new Path(jobConf.getWorkingDirectory(), jobConf.get(GenomixJobConf.INITIAL_INPUT_DIR));
            jobConf.set("mapred.input.dir", path.toString());

            Path outputDir = new Path(jobConf.getWorkingDirectory(), jobConf.get(GenomixJobConf.FINAL_OUTPUT_DIR));
            jobConf.set("mapred.output.dir", outputDir.toString());
        }

        FileOutputFormat.setOutputPath(jobConf, new Path(jobConf.get(GenomixJobConf.FINAL_OUTPUT_DIR)));
        FileSystem dfs = FileSystem.get(jobConf);
        dfs.delete(new Path(jobConf.get(GenomixJobConf.FINAL_OUTPUT_DIR)), true);

        GenomixHyracksDriver driver = new GenomixHyracksDriver(ipAddress, port, numOfDuplicate);
        driver.runJob(jobConf, Plan.BUILD_DEBRUIJN_GRAPH, bProfiling);
    }
}
