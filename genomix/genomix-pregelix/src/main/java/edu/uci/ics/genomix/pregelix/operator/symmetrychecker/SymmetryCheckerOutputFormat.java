package edu.uci.ics.genomix.pregelix.operator.symmetrychecker;

import java.io.IOException;

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import edu.uci.ics.genomix.data.types.Node;
import edu.uci.ics.genomix.data.types.VKmer;
import edu.uci.ics.genomix.pregelix.base.BinaryVertexOutputFormat;
import edu.uci.ics.genomix.pregelix.base.VertexValueWritable;
import edu.uci.ics.genomix.pregelix.base.VertexValueWritable.State;
import edu.uci.ics.pregelix.api.graph.Vertex;
import edu.uci.ics.pregelix.api.io.VertexWriter;

public class SymmetryCheckerOutputFormat extends BinaryVertexOutputFormat<VKmer, VertexValueWritable, NullWritable> {

    @Override
    public VertexWriter<VKmer, VertexValueWritable, NullWritable> createVertexWriter(TaskAttemptContext context)
            throws IOException, InterruptedException {
        @SuppressWarnings("unchecked")
        RecordWriter<VKmer, Node> recordWriter = binaryOutputFormat.getRecordWriter(context);
        return new BinaryLoadGraphVertexWriter(recordWriter);
    }

    /**
     * Simple VertexWriter that supports {@link BinaryLoadGraphVertex}
     */
    public static class BinaryLoadGraphVertexWriter extends
            BinaryVertexWriter<VKmer, VertexValueWritable, NullWritable> {
        public BinaryLoadGraphVertexWriter(RecordWriter<VKmer, Node> lineRecordWriter) {
            super(lineRecordWriter);
        }

        @Override
        public void writeVertex(Vertex<VKmer, VertexValueWritable, NullWritable, ?> vertex) throws IOException,
                InterruptedException {
            byte state = (byte) (vertex.getVertexValue().getState() & State.VERTEX_MASK);
            if (state == State.ERROR_NODE)
                getRecordWriter().write(vertex.getVertexId(), vertex.getVertexValue().getCopyAsNode());
        }
    }
}
