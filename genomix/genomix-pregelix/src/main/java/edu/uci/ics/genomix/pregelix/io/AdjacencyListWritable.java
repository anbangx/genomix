package edu.uci.ics.genomix.pregelix.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.WritableComparable;

import edu.uci.ics.genomix.oldtype.PositionListWritable;
import edu.uci.ics.genomix.type.KmerListWritable;

public class AdjacencyListWritable implements WritableComparable<AdjacencyListWritable>{
    private KmerListWritable forwardList;
    private KmerListWritable reverseList;
    
    public AdjacencyListWritable(){
        forwardList = new KmerListWritable();
        reverseList = new KmerListWritable();
    }
    
    public AdjacencyListWritable(int kmerSize){
        forwardList = new KmerListWritable();
        reverseList = new KmerListWritable();
    }

    public void set(AdjacencyListWritable adjacencyList){
        forwardList.setCopy(adjacencyList.getForwardList());
        reverseList.setCopy(adjacencyList.getReverseList());
    }
    
    public void reset(){
        forwardList.reset();
        reverseList.reset();
    }
    
    public void reset(int kmerSize){
        forwardList.reset();
        reverseList.reset();
    }
    
    public int getCountOfPosition(){
    	return forwardList.getCountOfPosition() + reverseList.getCountOfPosition();
    }

    public KmerListWritable getForwardList() {
        return forwardList;
    }

    public void setForwardList(KmerListWritable forwardList) {
        this.forwardList = forwardList;
    }

    public KmerListWritable getReverseList() {
        return reverseList;
    }

    public void setReverseList(KmerListWritable reverseList) {
        this.reverseList = reverseList;
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        forwardList.readFields(in);
        reverseList.readFields(in);
    }

    @Override
    public void write(DataOutput out) throws IOException {
        forwardList.write(out);
        reverseList.write(out);
    }

    @Override
    public int compareTo(AdjacencyListWritable o) {
        return 0;
    }
}
