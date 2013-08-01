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
package edu.uci.ics.genomix.hyracks.newgraph.io;

import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.mapred.JobConf;
import edu.uci.ics.genomix.hyracks.job.GenomixJobConf;
import edu.uci.ics.genomix.hyracks.newgraph.dataflow.AssembleKeyIntoNodeOperator;
import edu.uci.ics.genomix.type.NodeWritable;
import edu.uci.ics.genomix.type.KmerBytesWritable;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.hdfs.api.ITupleWriter;
import edu.uci.ics.hyracks.hdfs.api.ITupleWriterFactory;
import edu.uci.ics.hyracks.hdfs.dataflow.ConfFactory;

@SuppressWarnings("deprecation")
public class NodeSequenceWriterFactory implements ITupleWriterFactory {

    /**
     * Write the node to Text
     */
    private static final long serialVersionUID = 1L;
    private final int kmerSize;
    private ConfFactory confFactory;
    
    public static final int OutputNodeField = AssembleKeyIntoNodeOperator.OutputNodeField;
    
    public NodeSequenceWriterFactory(JobConf conf) throws HyracksDataException {
        this.confFactory = new ConfFactory(conf);
        this.kmerSize = conf.getInt(GenomixJobConf.KMER_LENGTH, GenomixJobConf.DEFAULT_KMERLEN);
    }

    public class TupleWriter implements ITupleWriter {

        public TupleWriter(ConfFactory confFactory) {
            this.cf = confFactory;
        }

        ConfFactory cf;
        Writer writer = null;
        NodeWritable node = new NodeWritable();

        @Override
        public void open(DataOutput output) throws HyracksDataException {
            try {
                writer = SequenceFile.createWriter(cf.getConf(), (FSDataOutputStream) output, NodeWritable.class,
                        NullWritable.class, CompressionType.NONE, null);
            } catch (IOException e) {
                throw new HyracksDataException(e);
            }
        }

        @Override
        public void write(DataOutput output, ITupleReference tuple) throws HyracksDataException {
            node.setAsReference(tuple.getFieldData(OutputNodeField), tuple.getFieldStart(OutputNodeField));
            try {
                writer.append(node, NullWritable.get());
            } catch (IOException e) {
                throw new HyracksDataException(e);
            }
        }

        @Override
        public void close(DataOutput output) throws HyracksDataException {
        }

    }

    @Override
    public ITupleWriter getTupleWriter(IHyracksTaskContext ctx) throws HyracksDataException {
        KmerBytesWritable.setGlobalKmerLength(kmerSize);
        return new TupleWriter(confFactory);
    }

}
