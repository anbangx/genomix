package edu.uci.ics.genomix.pregelix.operator.splitrepeat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import edu.uci.ics.genomix.pregelix.client.Client;
import edu.uci.ics.genomix.pregelix.io.VertexValueWritable;
import edu.uci.ics.genomix.pregelix.io.message.SplitRepeatMessageWritable;
import edu.uci.ics.genomix.pregelix.operator.BasicGraphCleanVertex;
import edu.uci.ics.genomix.pregelix.operator.aggregator.StatisticsAggregator;
import edu.uci.ics.genomix.pregelix.type.StatisticsCounter;
import edu.uci.ics.genomix.type.EdgeListWritable;
import edu.uci.ics.genomix.type.EdgeWritable;
import edu.uci.ics.genomix.type.NodeWritable.EDGETYPE;
import edu.uci.ics.genomix.type.VKmerBytesWritable;
import edu.uci.ics.pregelix.api.graph.Vertex;
import edu.uci.ics.pregelix.api.util.BspUtils;

/**
 * Graph clean pattern: Split Repeat
 * @author anbangx
 *
 */
public class SplitRepeatVertex extends 
    BasicGraphCleanVertex<VertexValueWritable, SplitRepeatMessageWritable>{
    
    public class EdgeAndDir{
        private EDGETYPE edgeType;
        private EdgeWritable edge;
        
        public EdgeAndDir(){
            edgeType = null;
            edge = new EdgeWritable();
        }

        public EDGETYPE getDir() {
            return edgeType;
        }

        public void setDir(EDGETYPE dir) {
            this.edgeType = dir;
        }

        public EdgeWritable getEdge() {
            return edge;
        }

        public void setEdge(EdgeWritable edge) {
            this.edge.setAsCopy(edge);
        }

    }
    
    private static Set<String> existKmerString = Collections.synchronizedSet(new HashSet<String>());
    private VKmerBytesWritable createdVertexId = null;  
    private Set<Long> incomingReadIdSet = new HashSet<Long>();
    private Set<Long> outgoingReadIdSet = new HashSet<Long>();
    private Set<Long> neighborEdgeIntersection = new HashSet<Long>();
    private EdgeWritable tmpIncomingEdge = null;
    private EdgeWritable tmpOutgoingEdge = null;

    private EdgeWritable deletedEdge = new EdgeWritable();
    private Set<EdgeAndDir> deletedEdges = new HashSet<EdgeAndDir>();//A set storing deleted edges
    
    /**
     * initiate kmerSize, maxIteration
     */
    @Override
    public void initVertex() {
        super.initVertex();
        if(incomingMsg == null)
            incomingMsg = new SplitRepeatMessageWritable();
        if(outgoingMsg == null)
            outgoingMsg = new SplitRepeatMessageWritable();
        else
            outgoingMsg.reset();
        if(destVertexId == null)
            destVertexId = new VKmerBytesWritable();
        if(tmpKmer == null)
            tmpKmer = new VKmerBytesWritable();
        if(incomingEdgeList == null)
            incomingEdgeList = new EdgeListWritable();
        if(outgoingEdgeList == null)
            outgoingEdgeList = new EdgeListWritable();
        if(createdVertexId == null)
            createdVertexId = new VKmerBytesWritable();
        if(tmpIncomingEdge == null)
            tmpIncomingEdge = new EdgeWritable();
        if(tmpOutgoingEdge == null)
            tmpOutgoingEdge = new EdgeWritable();
        if(getSuperstep() == 1)
            StatisticsAggregator.preGlobalCounters.clear();
//        else
//            StatisticsAggregator.preGlobalCounters = BasicGraphCleanVertex.readStatisticsCounterResult(getContext().getConfiguration());
        counters.clear();
        getVertexValue().getCounters().clear();
    }
    
    /**
     * Generate random string from [ACGT]
     */
    public String generaterRandomString(int n){
        char[] chars = "ACGT".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        synchronized(existKmerString){
            while(true){
                for (int i = 0; i < n; i++) {
                    char c = chars[random.nextInt(chars.length)];
                    sb.append(c);
                }
                if(!existKmerString.contains(sb.toString()))
                    break;
            }
            existKmerString.add(sb.toString());
        }
        return sb.toString();
    }
    
    public void randomGenerateVertexId(int numOfSuffix){
        String newVertexId = getVertexId().toString() + generaterRandomString(numOfSuffix);;
        createdVertexId.setByRead(kmerSize + numOfSuffix, newVertexId.getBytes(), 0);
    }
   
    public void setNeighborEdgeIntersection(EdgeWritable incomingEdge, EdgeWritable outgoingEdge){
        incomingReadIdSet.clear();
        long[] incomingReadIds = incomingEdge.getReadIDs().toReadIDArray();
        for(long readId : incomingReadIds){
            incomingReadIdSet.add(readId);
        }
        outgoingReadIdSet.clear();
        long[] outgoingReadIds = outgoingEdge.getReadIDs().toReadIDArray();
        for(long readId : outgoingReadIds){
            outgoingReadIdSet.add(readId);
        }
        neighborEdgeIntersection.clear();
        neighborEdgeIntersection.addAll(incomingReadIdSet);
        neighborEdgeIntersection.retainAll(outgoingReadIdSet);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void createNewVertex(int i, EdgeWritable incomingEdge, EdgeWritable outgoingEdge){
        Vertex vertex = (Vertex) BspUtils.createVertex(getContext().getConfiguration());
        vertex.getMsgList().clear();
        vertex.getEdges().clear();
        VKmerBytesWritable vertexId = new VKmerBytesWritable();
        VertexValueWritable vertexValue = new VertexValueWritable();
        //add the corresponding edge to new vertex
        vertexValue.getEdgeList(connectedTable[i][0]).add(incomingEdge);
        
        vertexValue.getEdgeList(connectedTable[i][1]).add(outgoingEdge);
        
        vertexValue.setInternalKmer(getVertexId());
        
        vertexId.setAsCopy(createdVertexId);
        vertex.setVertexId(vertexId);
        vertex.setVertexValue(vertexValue);
        
        addVertex(vertexId, vertex);
    }
    
    public void sendMsgToUpdateEdge(EdgeWritable incomingEdge, EdgeWritable outgoingEdge){
        EdgeWritable createdEdge = new EdgeWritable();
        createdEdge.setKey(createdVertexId);
        for(Long readId: neighborEdgeIntersection)
            createdEdge.appendReadID(readId);
        outgoingMsg.setCreatedEdge(createdEdge);
//        outgoingMsg.setSourceVertexId(getVertexId());
        deletedEdge.reset();
        deletedEdge.setKey(getVertexId());
        deletedEdge.setReadIDs(neighborEdgeIntersection);
        outgoingMsg.setDeletedEdge(deletedEdge);
        
        outgoingMsg.setFlag(incomingEdgeType.get());
        destVertexId.setAsCopy(incomingEdge.getKey());
        sendMsg(destVertexId, outgoingMsg);
        
        outgoingMsg.setFlag(outgoingEdgeType.get());
        destVertexId.setAsCopy(outgoingEdge.getKey());
        sendMsg(destVertexId, outgoingMsg);
    }
    
    public void storeDeletedEdge(int i, EdgeWritable incomingEdge, EdgeWritable outgoingEdge,
            Set<Long> commonReadIdSet){
        EdgeAndDir deletedIncomingEdge = new EdgeAndDir();
        EdgeAndDir deletedOutgoingEdge = new EdgeAndDir();
        
        deletedIncomingEdge.setDir(connectedTable[i][0]);
        deletedIncomingEdge.setEdge(incomingEdge);
        
        deletedOutgoingEdge.setDir(connectedTable[i][1]);
        deletedOutgoingEdge.setEdge(outgoingEdge);
        
        deletedEdges.add(deletedIncomingEdge);
        deletedEdges.add(deletedOutgoingEdge);
    }
    
    public void deleteEdgeFromOldVertex(EdgeAndDir deleteEdge){
        getVertexValue().getEdgeList(deleteEdge.edgeType).removeSubEdge(deleteEdge.getEdge());
    }
    
    public void updateEdgeListPointToNewVertex(){
        EDGETYPE meToNeighborDir = EDGETYPE.fromByte(incomingMsg.getFlag());//(byte) (incomingMsg.getFlag() & MessageFlag.VERTEX_MASK);
        EDGETYPE neighborToMeDir = meToNeighborDir.mirror();
        
        getVertexValue().getEdgeList(neighborToMeDir).removeSubEdge(incomingMsg.getDeletedEdge());
        getVertexValue().getEdgeList(neighborToMeDir).add(new EdgeWritable(incomingMsg.getCreatedEdge()));
    }
    
    @Override
    public void compute(Iterator<SplitRepeatMessageWritable> msgIterator) {
        initVertex();
        if(getSuperstep() == 1){
            if(getVertexValue().getDegree() > 2){
                deletedEdges.clear();
                // process connectedTable
                for(int i = 0; i < 4; i++){
                    // set edgeList and edgeType based on connectedTable
                    setEdgeListAndEdgeType(i);
                    
                    for(EdgeWritable incomingEdge : incomingEdgeList){
                        for(EdgeWritable outgoingEdge : outgoingEdgeList){
                            // set neighborEdge readId intersection
                            setNeighborEdgeIntersection(incomingEdge, outgoingEdge);
                            
                            if(!neighborEdgeIntersection.isEmpty()){
                                // random generate vertexId of new vertex
                                randomGenerateVertexId(3);
                                
                                // change incomingEdge/outgoingEdge's edgeList to commondReadIdSet
                                tmpIncomingEdge.setAsCopy(incomingEdge);
                                tmpOutgoingEdge.setAsCopy(outgoingEdge);
                                tmpIncomingEdge.setReadIDs(neighborEdgeIntersection);
                                tmpOutgoingEdge.setReadIDs(neighborEdgeIntersection);
                                
                                // create new/created vertex 
                                createNewVertex(i, tmpIncomingEdge, tmpOutgoingEdge);
                                //set statistics counter: Num_SplitRepeats
                                incrementCounter(StatisticsCounter.Num_SplitRepeats);
                                getVertexValue().setCounters(counters);
                                
                                // send msg to neighbors to update their edges to new vertex 
                                sendMsgToUpdateEdge(tmpIncomingEdge, tmpOutgoingEdge);
                                
                                // store deleted edge
                                storeDeletedEdge(i, tmpIncomingEdge, tmpOutgoingEdge, neighborEdgeIntersection);
                            }
                        }
                    }                
                }
                // delete extra edges from old vertex
                for(EdgeAndDir deletedEdge : deletedEdges){
                    deleteEdgeFromOldVertex(deletedEdge);
                }
                
                // Old vertex delete or voteToHalt 
                if(getVertexValue().getDegree() == 0)//if no any edge, delete
                    deleteVertex(getVertexId());
                else
                    voteToHalt();
            }
        } else if(getSuperstep() == 2){
            while(msgIterator.hasNext()){
                incomingMsg = msgIterator.next();
                // update edgelist to new/created vertex
                updateEdgeListPointToNewVertex();
            }
            voteToHalt();
        }
    }
    
    public static void main(String[] args) throws Exception {
        Client.run(args, getConfiguredJob(null, SplitRepeatVertex.class));
    }
    
}
