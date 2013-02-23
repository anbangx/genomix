package edu.uci.ics.pregelix.api.io.binary;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;

import edu.uci.ics.pregelix.api.io.VertexInputFormat;
import edu.uci.ics.pregelix.api.io.VertexReader;

public class BinaryVertexInputFormat <I extends WritableComparable, V extends Writable, E extends Writable, M extends Writable>
	extends VertexInputFormat<I, V, E, M>{
	
    /** Uses the SequenceFileInputFormat to do everything */
	protected SequenceFileInputFormat binaryInputFormat = new SequenceFileInputFormat();
    
    /**
     * Abstract class to be implemented by the user based on their specific
     * vertex input. Easiest to ignore the key value separator and only use key
     * instead.
     * 
     * @param <I>
     *            Vertex index value
     * @param <V>
     *            Vertex value
     * @param <E>
     *            Edge value
     */
    public static abstract class BinaryVertexReader<I extends WritableComparable, V extends Writable, E extends Writable, M extends Writable>
            implements VertexReader<I, V, E, M> {
        /** Internal line record reader */
        private final RecordReader<BytesWritable,ByteWritable> lineRecordReader;
        /** Context passed to initialize */
        private TaskAttemptContext context;

        /**
         * Initialize with the LineRecordReader.
         * 
         * @param recordReader
         *            Line record reader from SequenceFileInputFormat
         */
        public BinaryVertexReader(RecordReader<BytesWritable, ByteWritable> recordReader) {
            this.lineRecordReader = recordReader;
        }

        @Override
        public void initialize(InputSplit inputSplit, TaskAttemptContext context) throws IOException,
                InterruptedException {
            lineRecordReader.initialize(inputSplit, context);
            this.context = context;
        }

        @Override
        public void close() throws IOException {
            lineRecordReader.close();
        }

        @Override
        public float getProgress() throws IOException, InterruptedException {
            return lineRecordReader.getProgress();
        }

        /**
         * Get the line record reader.
         * 
         * @return Record reader to be used for reading.
         */
        protected RecordReader<BytesWritable,ByteWritable> getRecordReader() {
            return lineRecordReader;
        }

        /**
         * Get the context.
         * 
         * @return Context passed to initialize.
         */
        protected TaskAttemptContext getContext() {
            return context;
        }
    }

    @Override
    public List<InputSplit> getSplits(JobContext context, int numWorkers) throws IOException, InterruptedException {
        // Ignore the hint of numWorkers here since we are using SequenceFileInputFormat
        // to do this for us
        return binaryInputFormat.getSplits(context);
    }

	@Override
	public VertexReader<I, V, E, M> createVertexReader(InputSplit split,
			TaskAttemptContext context) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}




}