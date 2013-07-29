package edu.uci.ics.genomix.pregelix.operator.bubblemerge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hadoop.io.NullWritable;

import edu.uci.ics.genomix.type.KmerBytesWritableFactory;
import edu.uci.ics.pregelix.api.graph.Vertex;
import edu.uci.ics.pregelix.api.job.PregelixJob;
import edu.uci.ics.genomix.oldtype.PositionWritable;
import edu.uci.ics.genomix.pregelix.client.Client;
import edu.uci.ics.genomix.pregelix.format.GraphCleanInputFormat;
import edu.uci.ics.genomix.pregelix.format.GraphCleanOutputFormat;
import edu.uci.ics.genomix.pregelix.io.MergeBubbleMessageWritable;
import edu.uci.ics.genomix.pregelix.io.VertexValueWritable;
import edu.uci.ics.genomix.pregelix.type.AdjMessage;
import edu.uci.ics.genomix.pregelix.util.VertexUtil;

/*
 * vertexId: BytesWritable
 * vertexValue: ByteWritable
 * edgeValue: NullWritable
 * message: MessageWritable
 * 
 * DNA:
 * A: 00
 * C: 01
 * G: 10
 * T: 11
 * 
 * succeed node
 *  A 00000001 1
 *  G 00000010 2
 *  C 00000100 4
 *  T 00001000 8
 * precursor node
 *  A 00010000 16
 *  G 00100000 32
 *  C 01000000 64
 *  T 10000000 128
 *  
 * For example, ONE LINE in input file: 00,01,10    0001,0010,
 * That means that vertexId is ACG, its succeed node is A and its precursor node is C.
 * The succeed node and precursor node will be stored in vertexValue and we don't use edgeValue.
 * The details about message are in edu.uci.ics.pregelix.example.io.MessageWritable. 
 */
/**
 * Naive Algorithm for path merge graph
 */
public class BubbleMergeVertex extends
        Vertex<PositionWritable, VertexValueWritable, NullWritable, MergeBubbleMessageWritable> {
    public static final String KMER_SIZE = "BubbleMergeVertex.kmerSize";
    public static final String ITERATIONS = "BubbleMergeVertex.iteration";
    public static int kmerSize = -1;
    private int maxIteration = -1;

    private MergeBubbleMessageWritable incomingMsg = new MergeBubbleMessageWritable();
    private MergeBubbleMessageWritable outgoingMsg = new MergeBubbleMessageWritable();
    private KmerBytesWritableFactory kmerFactory = new KmerBytesWritableFactory(1);
    
    private Iterator<PositionWritable> iterator;
    private PositionWritable pos = new PositionWritable();
    private PositionWritable destVertexId = new PositionWritable();
    private Iterator<PositionWritable> posIterator;
    private Map<PositionWritable, ArrayList<MergeBubbleMessageWritable>> receivedMsgMap = new HashMap<PositionWritable, ArrayList<MergeBubbleMessageWritable>>();
    private ArrayList<MergeBubbleMessageWritable> receivedMsgList = new ArrayList<MergeBubbleMessageWritable>();
    
    /**
     * initiate kmerSize, maxIteration
     */
    public void initVertex() {
        if (kmerSize == -1)
            kmerSize = getContext().getConfiguration().getInt(KMER_SIZE, 5);
        if (maxIteration < 0)
            maxIteration = getContext().getConfiguration().getInt(ITERATIONS, 1000000);
        outgoingMsg.reset();
    }
    /**
     * get destination vertex
     */
    public PositionWritable getNextDestVertexId(VertexValueWritable value) {
        if(value.getFFList().getCountOfPosition() > 0) // #FFList() > 0
            posIterator = value.getFFList().iterator();
        else // #FRList() > 0
            posIterator = value.getFRList().iterator();
        return posIterator.next();
    }
    
    public PositionWritable getPrevDestVertexId(VertexValueWritable value) {
        if(value.getRFList().getCountOfPosition() > 0) // #FFList() > 0
            posIterator = value.getRFList().iterator();
        else // #FRList() > 0
            posIterator = value.getRRList().iterator();
        return posIterator.next();
    }

    /**
     * check if prev/next destination exists
     */
    public boolean hasNextDest(VertexValueWritable value){
        return value.getFFList().getCountOfPosition() > 0 || value.getFRList().getCountOfPosition() > 0;
    }
    
    public boolean hasPrevDest(VertexValueWritable value){
        return value.getRFList().getCountOfPosition() > 0 || value.getRRList().getCountOfPosition() > 0;
    }
    
    /**
     * head send message to all next nodes
     */
    public void sendMsgToAllNextNodes(VertexValueWritable value) {
        posIterator = value.getFFList().iterator(); // FFList
        while(posIterator.hasNext()){
            outgoingMsg.setMessage(AdjMessage.FROMFF);
            destVertexId.set(posIterator.next());
            sendMsg(destVertexId, outgoingMsg);
        }
        posIterator = value.getFRList().iterator(); // FRList
        while(posIterator.hasNext()){
            outgoingMsg.setMessage(AdjMessage.FROMFR);
            destVertexId.set(posIterator.next());
            sendMsg(destVertexId, outgoingMsg);
        }
    }
    
    /**
     * head send message to all next nodes
     */
    public void sendMsgToAllPrevNodes(VertexValueWritable value) {
        posIterator = value.getRFList().iterator(); // FFList
        while(posIterator.hasNext()){
            outgoingMsg.setMessage(AdjMessage.FROMRF);
            destVertexId.set(posIterator.next());
            sendMsg(destVertexId, outgoingMsg);
        }
        posIterator = value.getRRList().iterator(); // FRList
        while(posIterator.hasNext()){
            outgoingMsg.setMessage(AdjMessage.FROMRR);
            destVertexId.set(posIterator.next());
            sendMsg(destVertexId, outgoingMsg);
        }
    }
    
    /**
     * broadcast kill self to all neighbers  Pre-condition: vertex is a path vertex 
     */
    public void broadcaseKillself(){
        outgoingMsg.setSourceVertexId(getVertexId());
        
        if(getVertexValue().getFFList().getCountOfPosition() > 0){//#FFList() > 0
            outgoingMsg.setMessage(AdjMessage.FROMFF);
            sendMsg(incomingMsg.getSourceVertexId(), outgoingMsg);
        }
        else if(getVertexValue().getFRList().getCountOfPosition() > 0){//#FRList() > 0
            outgoingMsg.setMessage(AdjMessage.FROMFR);
            sendMsg(incomingMsg.getSourceVertexId(), outgoingMsg);
        }
        
        
        if(getVertexValue().getRFList().getCountOfPosition() > 0){//#RFList() > 0
            outgoingMsg.setMessage(AdjMessage.FROMRF);
            sendMsg(incomingMsg.getStartVertexId(), outgoingMsg);
        }
        else if(getVertexValue().getRRList().getCountOfPosition() > 0){//#RRList() > 0
            outgoingMsg.setMessage(AdjMessage.FROMRR);
            sendMsg(incomingMsg.getStartVertexId(), outgoingMsg);
        }
        
        deleteVertex(getVertexId());
    }
    
    /**
     * do some remove operations on adjMap after receiving the info about dead Vertex
     */
    public void responseToDeadVertex(Iterator<MergeBubbleMessageWritable> msgIterator){
        while (msgIterator.hasNext()) {
            incomingMsg = msgIterator.next();
            if(incomingMsg.getMessage() == AdjMessage.FROMFF){
                //remove incomingMsg.getSourceId from RR positionList
                iterator = getVertexValue().getRRList().iterator();
                while(iterator.hasNext()){
                    pos = iterator.next();
                    if(pos.equals(incomingMsg.getSourceVertexId())){
                        iterator.remove();
                        break;
                    }
                }
            } else if(incomingMsg.getMessage() == AdjMessage.FROMFR){
                //remove incomingMsg.getSourceId from RF positionList
                iterator = getVertexValue().getFRList().iterator();
                while(iterator.hasNext()){
                    pos = iterator.next();
                    if(pos.equals(incomingMsg.getSourceVertexId())){
                        iterator.remove();
                        break;
                    }
                }
            } else if(incomingMsg.getMessage() == AdjMessage.FROMRF){
                //remove incomingMsg.getSourceId from FR positionList
                iterator = getVertexValue().getRFList().iterator();
                while(iterator.hasNext()){
                    pos = iterator.next();
                    if(pos.equals(incomingMsg.getSourceVertexId())){
                        iterator.remove();
                        break;
                    }
                }
            } else{ //incomingMsg.getMessage() == AdjMessage.FROMRR
                //remove incomingMsg.getSourceId from FF positionList
                iterator = getVertexValue().getFFList().iterator();
                while(iterator.hasNext()){
                    pos = iterator.next();
                    if(pos.equals(incomingMsg.getSourceVertexId())){
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void compute(Iterator<MergeBubbleMessageWritable> msgIterator) {
        initVertex();
        if (getSuperstep() == 1) {
            if(VertexUtil.isHeadVertexWithIndegree(getVertexValue())
                    || VertexUtil.isHeadWithoutIndegree(getVertexValue())){
                outgoingMsg.setSourceVertexId(getVertexId());
                sendMsgToAllNextNodes(getVertexValue());
            }
//            if(VertexUtil.isRearVertexWithOutdegree(getVertexValue())
//                    || VertexUtil.isRearWithoutOutdegree(getVertexValue())){
//                outgoingMsg.setSourceVertexId(getVertexId());
//                sendMsgToAllPrevNodes(getVertexValue());
//            }
        } else if (getSuperstep() == 2){
            while (msgIterator.hasNext()) {
                incomingMsg = msgIterator.next();
                if(VertexUtil.isPathVertex(getVertexValue())){
                    switch(incomingMsg.getMessage()){
                        case AdjMessage.FROMFF:
                        case AdjMessage.FROMRF:
                            if(hasNextDest(getVertexValue())){
                                outgoingMsg.setMessage(AdjMessage.NON);
                                outgoingMsg.setStartVertexId(incomingMsg.getSourceVertexId());
                                outgoingMsg.setSourceVertexId(getVertexId());
                                outgoingMsg.setChainVertexId(getVertexValue().getKmer());
                                destVertexId.set(getNextDestVertexId(getVertexValue()));
                                sendMsg(destVertexId, outgoingMsg);
                            }
                            break;
                        case AdjMessage.FROMFR:
                        case AdjMessage.FROMRR:
                            if(hasPrevDest(getVertexValue())){
                                outgoingMsg.setMessage(AdjMessage.NON);
                                outgoingMsg.setStartVertexId(incomingMsg.getSourceVertexId());
                                outgoingMsg.setSourceVertexId(getVertexId());
                                outgoingMsg.setChainVertexId(getVertexValue().getKmer());
                                destVertexId.set(getPrevDestVertexId(getVertexValue()));
                                sendMsg(destVertexId, outgoingMsg);
                            }
                            break;
                    }
                }
            }
        } else if (getSuperstep() == 3){
            while (msgIterator.hasNext()) {
                incomingMsg = msgIterator.next();
                if(!receivedMsgMap.containsKey(incomingMsg.getStartVertexId())){
                    receivedMsgList.clear();
                    receivedMsgList.add(incomingMsg);
                    receivedMsgMap.put(incomingMsg.getStartVertexId(), (ArrayList<MergeBubbleMessageWritable>)receivedMsgList.clone());
                }
                else{
                    receivedMsgList.clear();
                    receivedMsgList.addAll(receivedMsgMap.get(incomingMsg.getStartVertexId()));
                    receivedMsgList.add(incomingMsg);
                    receivedMsgMap.put(incomingMsg.getStartVertexId(), (ArrayList<MergeBubbleMessageWritable>)receivedMsgList.clone());
                }
            }
            for(PositionWritable prevId : receivedMsgMap.keySet()){
                receivedMsgList = receivedMsgMap.get(prevId);
                if(receivedMsgList.size() > 1){
                    //find the node with largest length of Kmer
                    boolean flag = true; //the same length
                    int maxLength = receivedMsgList.get(0).getLengthOfChain();
                    PositionWritable max = receivedMsgList.get(0).getSourceVertexId();
                    PositionWritable secondMax = receivedMsgList.get(0).getSourceVertexId();
                    for(int i = 1; i < receivedMsgList.size(); i++){
                        if(receivedMsgList.get(i).getLengthOfChain() != maxLength)
                            flag = false;
                        if(receivedMsgList.get(i).getLengthOfChain() >= maxLength){
                            maxLength = receivedMsgList.get(i).getLengthOfChain();
                            secondMax.set(max);
                            max = receivedMsgList.get(i).getSourceVertexId();
                        }
                    }
                    //send unchange or merge Message to node with largest length
                    if(flag == true){
                        //1. send unchange Message to node with largest length
                        //   we can send no message to complete this step
                        //2. send delete Message to node which doesn't have largest length
                        for(int i = 0; i < receivedMsgList.size(); i++){
                            //if(receivedMsgList.get(i).getSourceVertexId().compareTo(max) != 0)
                            if(receivedMsgList.get(i).getSourceVertexId().compareTo(secondMax) == 0){ 
                                outgoingMsg.setMessage(AdjMessage.KILL);
                                outgoingMsg.setStartVertexId(prevId);
                                outgoingMsg.setSourceVertexId(getVertexId());
                                sendMsg(secondMax, outgoingMsg);
                            } else if(receivedMsgList.get(i).getSourceVertexId().compareTo(max) == 0){
                                outgoingMsg.setMessage(AdjMessage.UNCHANGE);
                                sendMsg(max, outgoingMsg);
                            }
                        }
                    } else{
                        //send merge Message to node with largest length
                        for(int i = 0; i < receivedMsgList.size(); i++){
                            //if(receivedMsgList.get(i).getSourceVertexId().compareTo(max) != 0)
                            if(receivedMsgList.get(i).getSourceVertexId().compareTo(secondMax) == 0){
                                outgoingMsg.setMessage(AdjMessage.KILL);
                                outgoingMsg.setStartVertexId(prevId);
                                outgoingMsg.setSourceVertexId(getVertexId());
                                sendMsg(receivedMsgList.get(i).getSourceVertexId(), outgoingMsg);
                            } else if(receivedMsgList.get(i).getSourceVertexId().compareTo(max) == 0){
                                outgoingMsg.setMessage(AdjMessage.MERGE);
                                /* add other node in message */
                                for(int j = 0; j < receivedMsgList.size(); i++){
                                    if(receivedMsgList.get(j).getSourceVertexId().compareTo(secondMax) == 0){
                                        outgoingMsg.setChainVertexId(receivedMsgList.get(j).getChainVertexId());
                                        break;
                                    }
                                }
                                sendMsg(receivedMsgList.get(i).getSourceVertexId(), outgoingMsg);
                            }
                        }
                    }
                }
            }
        } else if (getSuperstep() == 4){
            if(msgIterator.hasNext()) {
                incomingMsg = msgIterator.next();
                if(incomingMsg.getMessage() == AdjMessage.KILL){
                    broadcaseKillself();
                } else if (incomingMsg.getMessage() == AdjMessage.MERGE){
                    //merge with small node
                    getVertexValue().setKmer(kmerFactory.mergeTwoKmer(getVertexValue().getKmer(), 
                            incomingMsg.getChainVertexId()));
                }
            }
        } else if(getSuperstep() == 5){
            responseToDeadVertex(msgIterator);
        }
        voteToHalt();
    }

    public static void main(String[] args) throws Exception {
        PregelixJob job = new PregelixJob(BubbleMergeVertex.class.getSimpleName());
        job.setVertexClass(BubbleMergeVertex.class);
        /**
         * BinaryInput and BinaryOutput
         */
        job.setVertexInputFormatClass(GraphCleanInputFormat.class);
        job.setVertexOutputFormatClass(GraphCleanOutputFormat.class);
        job.setDynamicVertexValueSize(true);
        job.setOutputKeyClass(PositionWritable.class);
        job.setOutputValueClass(VertexValueWritable.class);
        Client.run(args, job);
    }
}
